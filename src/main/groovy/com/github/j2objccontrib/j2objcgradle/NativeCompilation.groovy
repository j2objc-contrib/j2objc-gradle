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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.toolchain.Clang

/**
 * Compilation of libraries for debug/release and architectures listed below.
 */
class NativeCompilation {

    static final String[] ALL_SUPPORTED_ARCHS = ['ios_arm64', 'ios_armv7', 'ios_armv7s', 'ios_i386', 'ios_x86_64']

    private final Project project

    NativeCompilation(Project project) {
        this.project = project
    }

    @PackageScope
    void apply(File srcGenDir) {
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
            file("${buildDir}/j2objcHackToForceCompilation").mkdirs()
            file("${buildDir}/j2objcHackToForceCompilation/Empty.m").createNewFile()
            file("${buildDir}/j2objcHackToForceCompilation/EmptyTest.m").createNewFile()

            String[] simulatorClangArgs = [
                    '-isysroot',
                    '/Applications/Xcode.app/Contents/Developer/Platforms/' +
                            'iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk',
                    '-mios-simulator-version-min=8.3',
            ]
            String[] iphoneClangArgs = [
                    '-isysroot',
                    '/Applications/Xcode.app/Contents/Developer/Platforms/' +
                            'iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk',
                    '-miphoneos-version-min=8.3',
            ]

            model {
                buildTypes {
                    debug
                    release
                }
                toolChains {
                    // Modify clang command line arguments since we need them to vary by target.
                    // https://docs.gradle.org/current/userguide/nativeBinaries.html#withArguments
                    clang(Clang) {
                        target('ios_arm64') {
                            String[] iosClangArgs = [
                                    '-arch',
                                    'arm64']
                            iosClangArgs += iphoneClangArgs
                            objcCompiler.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                            linker.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                        }
                        target('ios_armv7') {
                            String[] iosClangArgs = [
                                    '-arch',
                                    'armv7']
                            iosClangArgs += iphoneClangArgs
                            objcCompiler.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                            linker.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                        }
                        target('ios_armv7s') {
                            String[] iosClangArgs = [
                                    '-arch',
                                    'armv7s']
                            iosClangArgs += iphoneClangArgs
                            objcCompiler.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                            linker.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                        }
                        target('ios_i386') {
                            String[] iosClangArgs = [
                                    '-arch',
                                    'i386']
                            iosClangArgs += simulatorClangArgs
                            objcCompiler.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                            linker.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                        }
                        target('ios_x86_64') {
                            String[] iosClangArgs = [
                                    '-arch',
                                    'x86_64']
                            iosClangArgs += simulatorClangArgs
                            objcCompiler.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                            linker.withArguments { List<String> args ->
                                iosClangArgs.each { String arg ->
                                    args << arg
                                }
                            }
                        }
                        target('x86_64') {
                            linker.withArguments { List<String> args ->
                                args << '-framework'
                                args << 'ExceptionHandling'
                            }
                        }
                    }
                }
                platforms {
                    x86_64 {
                        architecture 'x86_64'
                    }
                    // The rest of this list must match ALL_SUPPORTED_ARCHS.
                    ios_arm64 {
                        architecture 'ios_arm64'
                    }
                    ios_armv7 {
                        architecture 'ios_armv7'
                    }
                    ios_armv7s {
                        architecture 'ios_armv7s'
                    }
                    ios_i386 {
                        architecture 'ios_i386'
                    }
                    ios_x86_64 {
                        architecture 'ios_x86_64'
                    }
                }

                components {
                    // Builds library, e.g. "libPROJECT-j2objc.a"
                    "${project.name}-j2objc"(NativeLibrarySpec) {
                        sources {
                            objc {
                                source {
                                    // Note that contents of srcGenDir are generated by j2objcTranslate task.
                                    srcDirs "${srcGenDir}", "${buildDir}/j2objcHackToForceCompilation"
                                    srcDirs j2objcConfig.extraObjcSrcDirs
                                    include '**/*.m'
                                    exclude '**/*Test.m'
                                }
                                // NOTE: Gradle has not yet implemented automatically archiving the
                                // exportedHeaders, this serves solely as a signifier for now.
                                exportedHeaders {
                                    srcDirs "${srcGenDir}"
                                    include '**/*.h'
                                    exclude '**/*Test.h'
                                }
                            }
                        }
                        j2objcConfig.activeArchs.each { String arch ->
                            if (!(arch in ALL_SUPPORTED_ARCHS)) {
                                throw new InvalidUserDataException(
                                        "Requested architecture $arch must be one of $ALL_SUPPORTED_ARCHS")
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
                                    srcDirs "${srcGenDir}", "${buildDir}/j2objcHackToForceCompilation"
                                    include '**/*Test.m'
                                }
                            }
                        }
                        binaries.all {
                            lib library: "${project.name}-j2objc", linkage: 'static'
                            // Resolve dependencies from all java j2objc projects using the compiled static libs.
                            beforeProjects.each { Project beforeProject ->
                                lib project: beforeProject.path, library: "${beforeProject.name}-j2objc", linkage: 'static'
                            }
                            j2objcConfig.extraNativeLibs.each { Map nativeLibSpec ->
                                lib nativeLibSpec
                            }

                            // J2ObjC provided libraries for testing only
                            linker.args '-ljunit'
                            linker.args '-lmockito'
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

                    // J2ObjC provided libraries and search path:
                    // TODO: should we link to all? Or just the 'standard' J2ObjC libraries?
                    linker.args '-ljre_emul'
                    j2objcConfig.linkJ2objcLibs.each { String libArg ->
                        linker.args "-l$libArg"
                    }
                    linker.args "-L$j2objcPath/lib"

                    // J2ObjC iOS library dependencies:
                    linker.args '-lc++'                    // C++ runtime for protobuf runtime
                    linker.args '-licucore'                // java.text
                    linker.args '-lz'                      // java.util.zip
                    linker.args '-framework', 'foundation' // core ObjC classes: NSObject, NSString
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
    @PackageScope
    void dependsOnJ2objcLib(Project beforeProject) {
        boolean added = beforeProjects.add(beforeProject)
        assert added
    }
}


