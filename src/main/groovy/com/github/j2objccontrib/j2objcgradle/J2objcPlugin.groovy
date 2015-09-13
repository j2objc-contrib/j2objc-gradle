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

import com.github.j2objccontrib.j2objcgradle.tasks.AssembleLibrariesTask
import com.github.j2objccontrib.j2objcgradle.tasks.AssembleResourcesTask
import com.github.j2objccontrib.j2objcgradle.tasks.AssembleSourceTask
import com.github.j2objccontrib.j2objcgradle.tasks.CycleFinderTask
import com.github.j2objccontrib.j2objcgradle.tasks.PackLibrariesTask
import com.github.j2objccontrib.j2objcgradle.tasks.TestTask
import com.github.j2objccontrib.j2objcgradle.tasks.TranslateTask
import com.github.j2objccontrib.j2objcgradle.tasks.Utils
import com.github.j2objccontrib.j2objcgradle.tasks.XcodeTask
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.logging.LogLevel
import org.gradle.util.GradleVersion

/*
 * Main plugin class for creation of extension object and all the tasks.
 */
class J2objcPlugin implements Plugin<Project> {
  
    @Override
    void apply(Project project) {
        String version = BuildInfo.VERSION
        String commit = BuildInfo.GIT_COMMIT
        String url = BuildInfo.URL
        String timestamp = BuildInfo.TIMESTAMP
        project.logger.info("j2objc-gradle plugin: Version $version, Built: $timestamp, Commit: $commit, URL: $url")
        if (!BuildInfo.GIT_IS_CLEAN) {
            project.logger.error('j2objc-gradle plugin was built with local modification.\n' +
                                 'If you encounter issues, please use an official release from:\n' +
                                 '    https://github.com/j2objc-contrib/j2objc-gradle/releases')
        } else if (version.contains('SNAPSHOT')) {
            project.logger.warn('j2objc-gradle plugin was built outside of an official release.\n' +
                                'If you encounter issues, please use an official release from:\n' +
                                '    https://github.com/j2objc-contrib/j2objc-gradle/releases')
        }

        // This avoids a lot of "project." prefixes, such as "project.tasks.create"
        project.with {
            Utils.checkMinGradleVersion(GradleVersion.current())
            getPluginManager().apply(JavaPlugin)

            extensions.create('j2objcConfig', J2objcConfig, project)

            afterEvaluate { Project evaluatedProject ->

                // Validate minimally required parameters.
                // j2objcHome() will throw the appropriate exception internally.
                assert Utils.j2objcHome(evaluatedProject)

                Utils.throwIfNoJavaPlugin(evaluatedProject)

                if (!evaluatedProject.j2objcConfig.isFinalConfigured()) {
                    logger.error("Project '${evaluatedProject.name}' is missing finalConfigure():\n" +
                                 "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#How-do-I-call-finalConfigure")
                }

                boolean arcTranslateArg = '-use-arc' in evaluatedProject.j2objcConfig.translateArgs
                boolean arcCompilerArg = '-fobjc-arc' in evaluatedProject.j2objcConfig.extraObjcCompilerArgs
                if (arcTranslateArg && !arcCompilerArg || !arcTranslateArg && arcCompilerArg) {
                    logger.error("Project '${evaluatedProject.name}' is missing required ARC flags:\n" +
                                 "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-enable-arc-for-my-objective-c-classes")
                }
            }

            // This is an intermediate directory only.  Clients should use only directories
            // specified in j2objcConfig (or associated defaults in J2objcConfig).
            File j2objcSrcGenDir = file("${buildDir}/j2objcSrcGen")

            // These configurations are groups of artifacts and dependencies for the plugin build
            // https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html
            configurations {
                // When j2objcConfig.autoConfigureDeps is true, this configuration
                // will have source paths automatically added to it.  You can add
                // *source* JARs/directories yourself as well.
                j2objcTranslationClosure {
                    description = 'J2ObjC Java source dependencies that need to be ' +
                                  'partially translated via --build-closure'
                }

                // If you want to translate an entire source library, regardless of which parts of the
                // library code your own code depends on, use this configuration.  This
                // will also cause the library to be Java compiled, since you cannot translate
                // a library with j2objc that does not successfully compile in Java.
                // So for example:
                //   dependencies { j2objcTranslation 'com.google.code.gson:gson:2.3.1:sources' }
                // will cause your project to produce a full gson Java classfile Jar AND a
                // j2objc-translated static native library.
                j2objcTranslation {
                    description = 'J2ObjC Java source libraries that should be fully translated ' +
                                  'and built as stand-alone native libraries'
                }

                // Currently, we can only handle Project dependencies already translated to Objective-C.
                j2objcLinkage {
                    description = 'J2ObjC native library dependencies that need to be ' +
                                  'linked into the final library, and do not need translation'
                }
            }

            DependencyResolver.configureSourceSets(project)

            // Produces a modest amount of output
            logging.captureStandardOutput LogLevel.INFO

            // If users need to generate extra files that j2objc depends on, they can make this task dependent
            // on such generation.
            tasks.create(name: 'j2objcPreBuild', type: DefaultTask,
                    dependsOn: 'test') {
                group 'build'
                description "Marker task for all tasks that must be complete before j2objc building"
            }

            // TODO @Bruno "build/source/apt" must be project.j2objcConfig.generatedSourceDirs no idea how to set it
            // there
            // Dependency may be added in project.plugins.withType for Java or Android plugin
            tasks.create(name: 'j2objcTranslate', type: TranslateTask,
                    dependsOn: 'j2objcPreBuild') {
                group 'build'
                description "Translates all the java source files in to Objective-C using 'j2objc'"
                additionalSrcFiles = files(
                        fileTree(dir: "build/source/apt",
                                include: "**/*.java")
                )
                // Output directory of 'j2objcTranslate', input for all other tasks
                srcGenDir = j2objcSrcGenDir
            }

            // j2objcCycleFinder is disabled by default as it's complex to use and understand.
            // TODO: consider enabling by default if it's possible to make it easier to use.
            // To enable the j2objcCycleFinder task, add the following to build.gradle:
            // j2objcCycleFinder { enabled = true }
            tasks.create(name: 'j2objcCycleFinder', type: CycleFinderTask,
                    dependsOn: 'j2objcPreBuild') {
                group 'build'
                description "Run the cycle_finder tool on all Java source files"
                enabled false
            }

            // NOTE: When adding new tasks, consider whether they fall under 'translate-only' mode, and
            // filter them in J2objcConfig.finalConfigure otherwise.

            // Note the '(debug|release)TestJ2objcExecutable' tasks are dynamically created by the Objective-C plugin.
            // It is specified by the testJ2objc native component in NativeCompilation.groovy.
            // TODO: copy and run debug and release tests within j2objcTestContent at the
            //       same time instead of destroying and recreating j2objcTestContent twice
            tasks.create(name: 'j2objcTestDebug', type: TestTask,
                    dependsOn: ['test', 'debugTestJ2objcExecutable']) {
                group 'verification'
                // This transitively depends on the 'test' task from the java plugin
                description 'Runs all tests in the generated Objective-C code'
                buildType = 'debug'
                testBinaryFile = file("${buildDir}/binaries/testJ2objcExecutable/debug/testJ2objc")
            }
            tasks.create(name: 'j2objcTestRelease', type: TestTask,
                    dependsOn: ['test', 'releaseTestJ2objcExecutable']) {
                group 'verification'
                // This transitively depends on the 'test' task from the java plugin
                description 'Runs all tests in the generated Objective-C code'
                buildType = 'release'
                testBinaryFile = file("${buildDir}/binaries/testJ2objcExecutable/release/testJ2objc")
            }
            tasks.create(name: 'j2objcTest', type: DefaultTask,
                    dependsOn: ['j2objcCycleFinder', 'j2objcTestDebug', 'j2objcTestRelease']) {
                group 'build'
                description "Marker task for all test tasks that take part in regular J2ObjC builds"
            }
            // 'check' task is added by 'java' plugin, it depends on 'test' and
            // all the other verification tasks, now including 'j2objcTest'.
            lateDependsOn(project, 'check', 'j2objcTest')

            // Pack Libraries
            tasks.create(name: 'j2objcPackLibrariesDebug', type: PackLibrariesTask,
                    dependsOn: 'j2objcBuildObjcDebug') {
                group 'build'
                description 'Packs multiple architectures into a single debug static library'
                buildType = 'Debug'
            }
            tasks.create(name: 'j2objcPackLibrariesRelease', type: PackLibrariesTask,
                    dependsOn: 'j2objcBuildObjcRelease') {
                group 'build'
                description 'Packs multiple architectures into a single release static library'
                buildType = 'Release'
            }

            // Assemble
            tasks.create(name: 'j2objcAssembleResources', type: AssembleResourcesTask,
                    dependsOn: ['j2objcPreBuild']) {
                group 'build'
                description 'Copies mains and test resources to assembly directories'
            }
            tasks.create(name: 'j2objcAssembleSource', type: AssembleSourceTask,
                    dependsOn: ['j2objcTranslate']) {
                group 'build'
                description 'Copies final generated source to assembly directories'
                srcGenDir = j2objcSrcGenDir
            }
            tasks.create(name: 'j2objcAssembleDebug', type: AssembleLibrariesTask,
                    dependsOn: ['j2objcPackLibrariesDebug', 'j2objcAssembleSource']) {
                group 'build'
                description 'Copies final generated source and debug libraries to assembly directories'
                buildType = 'Debug'
                srcLibDir = file("${buildDir}/binaries/${project.name}-j2objcStaticLibrary")
                srcPackedLibDir = file("${buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary")
            }
            tasks.create(name: 'j2objcAssembleRelease', type: AssembleLibrariesTask,
                    dependsOn: ['j2objcPackLibrariesRelease', 'j2objcAssembleSource']) {
                group 'build'
                description 'Copies final generated source and release libraries to assembly directories'
                buildType = 'Release'
                srcLibDir = file("${buildDir}/binaries/${project.name}-j2objcStaticLibrary")
                srcPackedLibDir = file("${buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary")
            }
            tasks.create(name: 'j2objcAssemble', type: DefaultTask,
                    dependsOn: ['j2objcAssembleDebug', 'j2objcAssembleRelease', 'j2objcAssembleResources']) {
                group 'build'
                description "Marker task for all assembly tasks that take part in regular j2objc builds"
            }
            lateDependsOn(project, 'assemble', 'j2objcAssemble')

            // Build
            tasks.create(name: 'j2objcBuildDebug', type: DefaultTask,
                    dependsOn: ['j2objcAssembleDebug', 'j2objcTestDebug']) {
                group 'build'
                description "Marker task for all debug tasks that take part in regular j2objc builds"
            }
            tasks.create(name: 'j2objcBuildRelease', type: DefaultTask,
                    dependsOn: ['j2objcAssembleRelease', 'j2objcTestRelease']) {
                group 'build'
                description "Marker task for all release tasks that take part in regular j2objc builds"
            }
            // If users need to depend on this project to build other j2objc projects, they can use this
            // marker task.
            tasks.create(name: 'j2objcBuild', type: DefaultTask,
                    dependsOn: ['j2objcBuildDebug', 'j2objcBuildRelease']) {
                group 'build'
                description "Marker task for all tasks that take part in regular j2objc builds"
            }
            lateDependsOn(project, 'build', 'j2objcBuild')

            // TODO: Where shall we fit this task in the plugin lifecycle?
            tasks.create(name: 'j2objcXcode', type: XcodeTask,
                    dependsOn: 'j2objcAssemble') {
                // This is not in the build group because you do not need to do it on every build.
                description 'Depends on j2objc translation, create a Pod file link it to Xcode project'
            }
        }
    }

    // Has task named afterTaskName depend on the task named beforeTaskName, regardless of
    // whether afterTaskName has been created yet or not.
    // The before task must already exist.
    private static void lateDependsOn(Project proj, String afterTaskName, String beforeTaskName) {
        assert null != proj.tasks.findByName(beforeTaskName)
        // You can't just call tasks.findByName on afterTaskName - for certain tasks like 'assemble' for
        // reasons unknown, the Java plugin creates - right there! - the task; this prevents
        // later code from modifying binaries, sourceSets, etc.  If you see an error
        // mentioning 'state GraphClosed' saying you can't mutate some object, see if you are magically
        // causing Gradle to make the task by using findByName!  Issue #156

        // tasks.all cleanly calls this closure on any existing elements and for all elements
        // added in the future.
        // TODO: Find a better way to have afterTask depend on beforeTask, without
        // materializing afterTask early.
        proj.tasks.all { Task task ->
            if (task.name == afterTaskName) {
                task.dependsOn beforeTaskName
            }
        }
    }
}
