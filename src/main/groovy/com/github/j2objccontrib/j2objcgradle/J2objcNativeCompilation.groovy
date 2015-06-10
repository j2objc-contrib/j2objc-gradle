package com.github.j2objccontrib.j2objcgradle

import com.github.j2objccontrib.j2objcgradle.tasks.J2objcUtils
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.toolchain.Clang

/**
 * Build the j2objc binaries, including the static libraries and test binary
 */

class J2objcNativeCompilation {
    // Tasks created to build the static and shared libraries
    // PROJECT = "${project.name}"
    //
    //   create{Ios|X86_64}{Debug|Release}PROJECT-j2objcStaticLibrary
    //   compile{Ios|X86_64}{Debug|Release}PROJECT-j2objc{Shared|Static}LibraryBase-j2objcObjc
    //   link{Ios|X86_64}{Debug|Release}PROJECT-j2objcSharedLibrary
    //   {Ios|X86_64}{Debug|Release}PROJECT-j2objc{Shared|Static}Library
    //
    // Tasks created to build the test binary (binary is executed in the 'j2objcTest' task)
    //   compileDebugTestJ2objcExecutableTestJ2objcObjc
    //   linkDebugTestJ2objcExecutable
    //   debugTestJ2objcExecutable
    def apply(Project project, File srcGenDir) {
        project.with {
            // Wire up dependencies with tasks created dynamically by native plugin(s).
            tasks.whenTaskAdded { task ->
                // The objective-c native plugin will add tasks of the form 'compile...Objc' for each
                // combination of buildType, platform, and component.  Note that components having only
                // one buildType or platform will NOT have the given buildType/platform in the task name, so
                // we have to use a very broad regular expression.
                // For example task 'compileDebugTestJ2objcExecutableTestJ2objcObjc' compiles the debug
                // buildType of the executable binary 'testJ2objc' from the 'testJ2objc' component.
                if ((task.name =~ /^compile.*Objc$/).matches()) {
                    task.dependsOn 'j2objcTranslate'
                }
            }

            apply plugin: 'objective-c'

            // TODO: Figure out a better way to force compilation.
            // We create these files so that before the first j2objcTranslate execution is performed, at least
            // one file exists for each of the objective-c sourceSets, at project evaluation time.
            // Otherwise the objective-c plugin skips creation of the compile tasks altogether.
            file("${buildDir}/j2objcHackToForceCompilation").mkdirs()
            file("${buildDir}/j2objcHackToForceCompilation/Empty.m").createNewFile()
            file("${buildDir}/j2objcHackToForceCompilation/EmptyTest.m").createNewFile()

            model {
                buildTypes {
                    debug
                    release
                }
                toolChains {
                    // Modify clang command line arguments since we need them to vary by target.
                    // https://docs.gradle.org/current/userguide/nativeBinaries.html#withArguments
                    clang(Clang) {
                        target('ios') {
                            def iosClangArgs = [
                                    '-arch',
                                    'x86_64',
                                    '-isysroot',
                                    '/Applications/Xcode' +
                                        '.app/Contents/Developer/Platforms/iPhoneSimulator' +
                                        '.platform/Developer/SDKs/iPhoneSimulator.sdk',
                                    '-mios-simulator-version-min=8.3',
                            ]
                            objcCompiler.withArguments { args ->
                                iosClangArgs.each { args << it }
                            }
                            linker.withArguments { args ->
                                iosClangArgs.each { args << it }
                            }
                        }
                        target('x86_64') {
                            linker.withArguments { args ->
                                args << '-framework'
                                args << 'ExceptionHandling'
                            }
                        }
                        target('i386') {
                            objcCompiler.withArguments { args ->
                                args << '-arch'
                                args << 'i386'
                            }
                            linker.withArguments { args ->
                                args << '-arch'
                                args << 'i386'
                                args << '-framework'
                                args << 'ExceptionHandling'
                            }
                        }
                    }
                }
                platforms {
                    ios {
                        architecture 'ios'
                    }
                    i386 {
                        architecture 'i386'
                    }
                    x86_64 {
                        architecture 'x86_64'
                    }
                }

                components {
                    // Builds library, e.g. "libPROJECT-j2objc.a"
                    // Calls dynamic property "PROJECT-j2objc" with NativeLibrarySpec.class object
                    "${project.name}-j2objc"(NativeLibrarySpec) {
                        sources {
                            objc {
                                source {
                                    srcDirs "${srcGenDir}", "${buildDir}/j2objcHackToForceCompilation"
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
                        targetPlatform 'ios'
                        targetPlatform 'i386'
                        targetPlatform 'x86_64'
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

                            // J2ObjC provided libraries for testing only
                            linker.args '-ljunit'
                            linker.args '-lmockito'
                        }
                        targetPlatform 'x86_64'
                    }
                }
            }

            // We need to run clang with the flags j2objcc would usually pass.
            binaries.all {
                // Only want to modify the objective-c toolchain, not the JDK one.
                if (toolChain in Clang) {
                    def j2objcPath = J2objcUtils.j2objcHome(project)

                    // If you want to override the flags passed to the compiler and linker, you must configure
                    // the binaries in your own build.gradle.  See
                    // Gradle User Guide: 54.11. Configuring the compiler, assembler and linker
                    // https://docs.gradle.org/current/userguide/nativeBinaries.html#N16030
                    // TODO: Consider making this configuration easier using plugin extension.
                    // If we do that, however, we will become inconsistent with Gradle objective-c building.
                    objcCompiler.args "-I$j2objcPath/include"
                    objcCompiler.args '-Werror', '-Wno-parentheses', '-fno-strict-overflow'
                    objcCompiler.args '-std=c11'

                    linker.args '-ObjC'

                    // J2ObjC provided libraries and search path:
                    // TODO: should we link to all? Or just the 'standard' J2ObjC libraries?
                    linker.args '-lguava', '-lj2objc_main', '-ljavax_inject', '-ljre_emul', '-ljsr305'
                    linker.args "-L$j2objcPath/lib"

                    // J2ObjC iOS library dependencies:
                    linker.args '-lc++'                    // C++ runtime for protobuf runtime
                    linker.args '-licucore'                // java.text
                    linker.args '-lz'                      // java.util.zip
                    linker.args '-framework', 'foundation' // core ObjC classes: NSObject, NSString
                    linker.args '-framework', 'Security'   // secure hash generation

                    if (buildType == buildTypes.debug) {
                        objcCompiler.args "-g"
                    }
                }
            }

            // Marker task to build all objective-c libraries.
            // From Gradle User Guide: 54.14.5. Building all possible variants
            // https://docs.gradle.org/current/userguide/nativeBinaries.html#N161B3
            // TODO: when does a user need the dynamic libraries?
            task('buildAllObjcLibraries').configure {
                dependsOn binaries.withType(NativeLibraryBinary).matching {
                    it.buildable
                }
            }
        }
    }
}


