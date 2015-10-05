/*
 * Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.j2objccontrib.j2objcgradle

import com.github.j2objccontrib.j2objcgradle.tasks.Utils
import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.platform.base.Platform

/**
 * Compilation of libraries for debug/release and architectures listed below.
 */
class NativeCompilation {

    static final String[] ALL_IOS_ARCHS = ['ios_arm64', 'ios_armv7', 'ios_armv7s', 'ios_i386', 'ios_x86_64']
    // TODO: Provide a mechanism to vary which OSX architectures are built.
    static final String[] ALL_OSX_ARCHS = ['x86_64']

    private final Project project

    NativeCompilation(Project project) {
        this.project = project
    }

    enum TargetSpec {
        TARGET_IOS_DEVICE,
        TARGET_IOS_SIMULATOR,
        TARGET_OSX,
    }

    String[] simulatorClangArgs = [
            '-isysroot',
            '/Applications/Xcode.app/Contents/Developer/Platforms/' +
            'iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk',
    ]
    String[] iphoneClangArgs = [
            '-isysroot',
            '/Applications/Xcode.app/Contents/Developer/Platforms/' +
            'iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk',
    ]

    void definePlatforms(NamedDomainObjectContainer<Platform> d, List<String> names) {
        names.each { String name ->
            d.create(name, {
                architecture name
            })
        }
    }

    void defineTarget(Clang d, String name, TargetSpec targetSpec, final String architecture) {
        d.target(name, new Action<GccPlatformToolChain>() {
            @Override
            void execute(GccPlatformToolChain gccPlatformToolChain) {
                // Arguments common to the compiler and linker.
                String[] clangArgs = [
                        '-arch',
                        architecture]
                // Arguments specific to the compiler.
                String[] compilerArgs = []
                // Arguments specific to the linker.
                String[] linkerArgs = []
                J2objcConfig config = J2objcConfig.from(project)
                String j2objcPath = Utils.j2objcHome(project)
                switch (targetSpec) {
                    case TargetSpec.TARGET_IOS_DEVICE:
                        clangArgs += iphoneClangArgs
                        clangArgs += ["-miphoneos-version-min=${config.minIosVersion}"]
                        linkerArgs += ["-L$j2objcPath/lib"]
                        break
                    case TargetSpec.TARGET_IOS_SIMULATOR:
                        clangArgs += simulatorClangArgs
                        clangArgs += ["-mios-simulator-version-min=${config.minIosVersion}"]
                        linkerArgs += ["-L$j2objcPath/lib"]
                        break
                    case TargetSpec.TARGET_OSX:
                        if (!Utils.j2objcHasOsxDistribution(project)) {
                            String msg = "J2ObjC distribution at $j2objcPath lacks a lib/macosx directory.\n" +
                                         "Please update to J2ObjC 0.9.8.2.1 or higher; earlier versions will\n" +
                                         "not work correctly with Xcode 7 or higher."
                            throw new InvalidUserDataException(msg)
                        }
                        linkerArgs += ["-L$j2objcPath/lib/macosx"]
                        linkerArgs += ['-framework', 'ExceptionHandling']
                        break
                }
                compilerArgs += clangArgs
                linkerArgs += clangArgs
                gccPlatformToolChain.objcCompiler.withArguments { List<String> args ->
                    args.addAll(compilerArgs)
                }
                gccPlatformToolChain.objcppCompiler.withArguments { List<String> args ->
                    args.addAll(compilerArgs)
                }
                gccPlatformToolChain.linker.withArguments { List<String> args ->
                    args.addAll(linkerArgs)
                }
            }
        })
    }

    @PackageScope
    void apply(File srcGenMainDir, File srcGenTestDir) {
        project.with {
            // Wire up dependencies with tasks created dynamically by native plugin(s).
            tasks.whenTaskAdded { Task task ->
                // The Objective-C native plugin will add tasks of the form 'compile...Objc' for each
                // combination of buildType, platform, and component.  Note that components having only
                // one buildType or platform will NOT have the given buildType/platform in the task name, so
                // we have to use a very broad regular expression.
                // For example task 'compileDebugTestJ2objcExecutableTestJ2objcObjc' compiles the debug
                // buildType of the executable binary 'testJ2objc' from the 'testJ2objc' component.
                if ((task.name =~ /^compile.*Objc$/).matches()) {
                    task.dependsOn 'j2objcTranslate'
                }

                // Only static libraries are needed, so disable shared libraries dynamically.
                // There is no way to do this within the native binary model.
                if ((task.name =~ /^.*SharedLibrary.*$/).matches()) {
                    task.enabled = false
                }
            }

            apply plugin: 'objective-c'

            // TODO: Figure out a better way to force compilation.
            // We create these files so that before the first j2objcTranslate execution is performed, at least
            // one file exists for each of the Objective-C sourceSets, at project evaluation time.
            // Otherwise the Objective-C plugin skips creation of the compile tasks altogether.
            file("${buildDir}/j2objcHackToForceMainCompilation").mkdirs()
            file("${buildDir}/j2objcHackToForceMainCompilation/Empty.m").createNewFile()
            file("${buildDir}/j2objcHackToForceTestCompilation").mkdirs()
            file("${buildDir}/j2objcHackToForceTestCompilation/EmptyTest.m").createNewFile()

            model {
                buildTypes {
                    debug
                    release
                }
                toolChains {
                    // Modify clang command line arguments since we need them to vary by target.
                    // https://docs.gradle.org/current/userguide/nativeBinaries.html#withArguments
                    clang(Clang) {
                        defineTarget(delegate, 'ios_arm64', TargetSpec.TARGET_IOS_DEVICE, 'arm64')
                        defineTarget(delegate, 'ios_armv7', TargetSpec.TARGET_IOS_DEVICE, 'armv7')
                        defineTarget(delegate, 'ios_armv7s', TargetSpec.TARGET_IOS_DEVICE, 'armv7s')
                        defineTarget(delegate, 'ios_i386', TargetSpec.TARGET_IOS_SIMULATOR, 'i386')
                        defineTarget(delegate, 'ios_x86_64', TargetSpec.TARGET_IOS_SIMULATOR, 'x86_64')
                        defineTarget(delegate, 'x86_64', TargetSpec.TARGET_OSX, 'x86_64')
                    }
                }
                platforms {
                    definePlatforms(delegate as NamedDomainObjectContainer<Platform>, ALL_OSX_ARCHS as List<String>)
                    definePlatforms(delegate as NamedDomainObjectContainer<Platform>, ALL_IOS_ARCHS as List<String>)
                }

                components {
                    // Builds library, e.g. "libPROJECT-j2objc.a"
                    "${project.name}-j2objc"(NativeLibrarySpec) {
                        sources {
                            objc {
                                source {
                                    // Note that contents of srcGenMainDir are generated by j2objcTranslate task.
                                    srcDirs "${srcGenMainDir}", "${buildDir}/j2objcHackToForceMainCompilation"
                                    srcDirs j2objcConfig.extraObjcSrcDirs
                                    include '**/*.m'
                                }
                                // NOTE: Gradle has not yet implemented automatically archiving the
                                // exportedHeaders, this serves solely as a signifier for now.
                                exportedHeaders {
                                    srcDirs "${srcGenMainDir}"
                                    include '**/*.h'
                                }
                            }
                        }
                        j2objcConfig.activeArchs.each { String arch ->
                            if (!(arch in ALL_IOS_ARCHS)) {
                                throw new InvalidUserDataException(
                                        "Requested architecture $arch must be one of $ALL_IOS_ARCHS")
                            }
                            targetPlatform arch
                        }
                        // Always need x86_64 for unit-testing on Mac.
                        targetPlatform 'x86_64'

                        // Resolve dependencies from all java j2objc projects using the compiled static libs.
                        binaries.all {
                            beforeProjects.each { Project beforeProject ->
                                lib project: beforeProject.path, library: "${beforeProject.name}-j2objc", linkage: 'static'
                            }
                            j2objcConfig.extraNativeLibs.each { Map nativeLibSpec ->
                                lib nativeLibSpec
                            }
                        }
                    }

                    // Create an executable binary from a library containing just the test source code linked to
                    // the production library built above.
                    testJ2objc(NativeExecutableSpec) {
                        sources {
                            objc {
                                source {
                                    srcDirs "${srcGenTestDir}", "${buildDir}/j2objcHackToForceTestCompilation"
                                    include '**/*.m'
                                }
                                exportedHeaders {
                                    srcDirs "${srcGenTestDir}"
                                    include '**/*.h'
                                }
                            }
                        }
                        binaries.all {
                            lib library: "${project.name}-j2objc", linkage: 'static'
                            // Resolve dependencies from all java j2objc projects using the compiled static libs.
                            beforeProjects.each { Project beforeProject ->
                                lib project: beforeProject.path, library: "${beforeProject.name}-j2objc", linkage: 'static'
                            }
                            beforeTestProjects.each { Project beforeProject ->
                                lib project: beforeProject.path, library: "${beforeProject.name}-j2objc", linkage: 'static'
                            }
                            j2objcConfig.extraNativeLibs.each { Map nativeLibSpec ->
                                lib nativeLibSpec
                            }

                            j2objcConfig.linkJ2objcTestLibs.each { String libArg ->
                                linker.args "-l$libArg"
                            }
                        }
                        targetPlatform 'x86_64'
                    }
                }
            }

            // We need to run clang with the arguments that j2objcc would usually pass.
            binaries.all {
                // Only want to modify the Objective-C toolchain, not the JDK one.
                if (toolChain in Clang) {
                    String j2objcPath = Utils.j2objcHome(project)

                    // If you want to override the arguments passed to the compiler and linker,
                    // you must configure the binaries in your own build.gradle.
                    // See "Gradle User Guide: 54.11. Configuring the compiler, assembler and linker"
                    // https://docs.gradle.org/current/userguide/nativeBinaries.html#N16030
                    // TODO: Consider making this configuration easier using plugin extension.
                    // If we do that, however, we will become inconsistent with Gradle Objective-C building.
                    objcCompiler.args "-I$j2objcPath/include"
                    objcCompiler.args '-Werror', '-Wno-parentheses', '-fno-strict-overflow'
                    objcCompiler.args '-std=c11'
                    objcCompiler.args j2objcConfig.extraObjcCompilerArgs

                    linker.args '-ObjC'

                    // J2ObjC provided libraries:
                    // TODO: should we link to all? Or just the 'standard' J2ObjC libraries?
                    linker.args '-ljre_emul'
                    j2objcConfig.linkJ2objcLibs.each { String libArg ->
                        linker.args "-l$libArg"
                    }

                    // J2ObjC iOS library dependencies:
                    linker.args '-lc++'                    // C++ runtime for protobuf runtime
                    linker.args '-licucore'                // java.text
                    linker.args '-lz'                      // java.util.zip
                    linker.args '-framework', 'Foundation' // core ObjC classes: NSObject, NSString
                    linker.args '-framework', 'Security'   // secure hash generation
                    linker.args j2objcConfig.extraLinkerArgs

                    if (buildType == buildTypes.debug) {
                        objcCompiler.args '-g'
                        objcCompiler.args '-DDEBUG=1'
                    }
                }
            }

            // Marker tasks to build all Objective-C libraries.
            // See Gradle User Guide: 54.14.5. Building all possible variants
            // https://docs.gradle.org/current/userguide/nativeBinaries.html#N161B3
            task('j2objcBuildObjcDebug').configure {
                dependsOn binaries.withType(NativeLibraryBinary).matching { NativeLibraryBinary lib ->
                    lib.buildable && lib.buildType.name == 'debug'
                }
            }
            task('j2objcBuildObjcRelease').configure {
                dependsOn binaries.withType(NativeLibraryBinary).matching { NativeLibraryBinary lib ->
                    lib.buildable && lib.buildType.name == 'release'
                }
            }
        }
    }

    private List<Project> beforeProjects = []
    private List<Project> beforeTestProjects = []
    @PackageScope
    void dependsOnJ2objcLib(Project beforeProject, boolean isTest) {
        boolean added = (isTest ? beforeTestProjects : beforeProjects).add(beforeProject)
        assert added
    }
}


