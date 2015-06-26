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

import com.github.j2objccontrib.j2objcgradle.tasks.AssembleTask
import com.github.j2objccontrib.j2objcgradle.tasks.CycleFinderTask
import com.github.j2objccontrib.j2objcgradle.tasks.PackLibrariesTask
import com.github.j2objccontrib.j2objcgradle.tasks.TestTask
import com.github.j2objccontrib.j2objcgradle.tasks.TranslateTask
import com.github.j2objccontrib.j2objcgradle.tasks.Utils
import com.github.j2objccontrib.j2objcgradle.tasks.XcodeTask
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel


/*
 * Main plugin class for creation of extension object and all the tasks.
 */
class J2objcPlugin implements Plugin<Project> {
  
    @Override
    void apply(Project project) {
        // This avoids a lot of "project." prefixes, such as "project.tasks.create"
        project.with {
            extensions.create('j2objcConfig', J2objcConfig, project)

            afterEvaluate { Project evaluatedProject ->

                // Validate minimally required parameters.
                // j2objcHome() will throw the appropriate exception internally.
                assert Utils.j2objcHome(evaluatedProject)

                Utils.throwIfNoJavaPlugin(evaluatedProject)

                if (!evaluatedProject.j2objcConfig.isFinalConfigured()) {
                    String message = "You must call finalConfigure() in j2objcConfig, ex:\n" +
                                  "j2objcConfig {\n" +
                                  "    // other settings\n" +
                                  "    finalConfigure()\n" +
                                  "}"
                    throw new InvalidUserDataException(message)
                }

                boolean arcTranslateArg = '-use-arc' in evaluatedProject.j2objcConfig.translateArgs
                boolean arcCompilerArg = '-fobjc-arc' in evaluatedProject.j2objcConfig.extraObjcCompilerArgs
                if (arcTranslateArg && !arcCompilerArg || !arcTranslateArg && arcCompilerArg) {
                    logger.error "${evaluatedProject.name}: using ARC with J2ObjC, the project is missing one of the two arguments required:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    // other settings\n" +
                        "    translateArgs '-use-arc'\n" +
                        // TODO: extraObjcCompilerArgs '-fobjc-arc'
                        "    extraObjcCompilerArgs = ['-fobjc-arc']\n" +
                        "}\n" +
                        "-fobjc-arc enables Automatic Reference Counting functionality in the compiler:\n" +
                        "https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15"
                } 
            }

            // This is an intermediate directory only.  Clients should use only directories
            // specified in j2objcConfig (or associated defaults in J2objcConfig).
            File j2objcSrcGenDir = file("${buildDir}/j2objcSrcGen")

            // Produces a modest amount of output
            logging.captureStandardOutput LogLevel.INFO

            // If users need to generate extra files that j2objc depends on, they can make this task dependent
            // on such generation.
            tasks.create(name: 'j2objcPreBuild', type: DefaultTask,
                    dependsOn: 'test') {
                group 'build'
                description "Marker task for all tasks that must be complete before j2objc building"
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

            // TODO @Bruno "build/source/apt" must be project.j2objcConfig.generatedSourceDirs no idea how to set it
            // there
            // Dependency may be added in project.plugins.withType for Java or Android plugin
            tasks.create(name: 'j2objcTranslate', type: TranslateTask,
                    dependsOn: 'j2objcCycleFinder') {
                group 'build'
                description "Translates all the java source files in to Objective-C using j2objc"
                additionalSrcFiles = files(
                        fileTree(dir: "build/source/apt",
                                include: "**/*.java")
                )
                // Output directory of 'j2objcTranslate', input for all other tasks
                srcGenDir = j2objcSrcGenDir
            }

            // Note the 'debugTestJ2objcExecutable' task is dynamically created by the Objective-C plugin applied
            // on the above line.  It is specified by the testJ2objc native component.
            tasks.create(name: 'j2objcTest', type: TestTask,
                    dependsOn: ['test', 'debugTestJ2objcExecutable']) {
                group 'verification'
                // This transitively depends on the 'test' task from the java plugin
                description 'Runs all tests in the generated Objective-C code'
                testBinaryFile = file("${buildDir}/binaries/testJ2objcExecutable/debug/testJ2objc")
            }
            // 'check' task is added by 'java' plugin, it depends on 'test' and
            // all the other verification tasks, now including 'j2objcTest'.
            lateDependsOn(project, 'check', 'j2objcTest')


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

            tasks.create(name: 'j2objcAssemble', type: AssembleTask,
                    dependsOn: ['j2objcPackLibrariesDebug', 'j2objcPackLibrariesRelease', 'j2objcTranslate']) {
                group 'build'
                description 'Copies final generated source and libraries to assembly directories'
                srcGenDir = j2objcSrcGenDir
                libDir = file("${buildDir}/binaries/${project.name}-j2objcStaticLibrary")
                packedLibDir = file("${buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary")
            }
            lateDependsOn(project, 'assemble', 'j2objcAssemble')

            // TODO: Where shall we fit this task in the plugin lifecycle?
            tasks.create(name: 'j2objcXcode', type: XcodeTask,
                    dependsOn: 'j2objcAssemble') {
                // This is not in the build group because you do not need to do it on every build.
                description 'Depends on j2objc translation, create a Pod file link it to Xcode project'
                srcGenDir = j2objcSrcGenDir
            }
        }
    }

    // Has task named afterTaskName depend on the task named beforeTaskName, regardless of
    // whether afterTaskName has been created yet or not.
    // The before task must already exist.
    private void lateDependsOn(Project proj, String afterTaskName, String beforeTaskName) {
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
