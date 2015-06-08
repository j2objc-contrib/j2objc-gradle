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
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcAssembleTask
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcCycleFinderTask
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcTestTask
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcTranslateTask
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcUtils
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcXcodeTask
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
/*
 * Usage:
 * 1) Download a j2objc release and unzip it to a directory:
 *     https://github.com/google/j2objc/releases
 * 2) Copy this file (j2objc.gradle) next to the build.gradle file of the project to be translated
 * 3) Copy and paste the following to your project's build.gradle file
 *     (it's currently best to run "gradlew clean" when changing this configuration)

 apply from: 'j2objc.gradle'

 j2objcConfig {

 // MODIFY to where your unzipped j2objc directory is located
 // NOTE download the latest version from: https://github.com/google/j2objc/releases
 j2objcHome null  // e.g. "${projectDir}/../../j2objc or absolute path

 // MODIFY to where generated objc files should be put for Xcode project
 // NOTE these files should be checked in to the repository and updated as needed
 // NOTE this should contain ONLY j2objc generated files, other contents will be deleted
 destDir null  // e.g. "${projectDir}/../Xcode/j2objc-generated"

 // Further settings are listed in the "J2objcPluginExtension" class below
 }

 * 4) Run command to generate and copyfiles. This will only succeed if all steps succeed.
 *
 *     $ gradlew <SHARED_PROJECT>:j2objcCopy
 *
 * Commands:
 * Each one depends on the previous command
 *     j2objcCycleFinder - Find cycles that can cause memory leaks, see https://github
 *     .com/google/j2objc/wiki/Cycle-Finder-Tool
 *     j2objcTranslate   - Translates to Objective-C, depends on java or Android project if found
 *     j2objcCompile     - Compile Objective-C files and build Objective-C binary (named 'runner')
 *     j2objcTest        - Run all java tests against the Objective-C binary
 *     j2objcCopy        - Copy generated Objective-C files to Xcode project
 *
 * Note that you can use the Gradle shorthand of "$ gradlew jCop" to do the j2objcCopy task.
 * The other shorthand expressions are "jTr", "jCom" and "jTe"
 *
 * Thanks to Peter Niederwieser and 'bigguy' from Gradleware
 */

class J2objcPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // This avoids a lot of "project." prefixes, such as "project.tasks.create"
        project.with {
            if (!plugins.hasPlugin('java')) {
                def message =
                        "j2objc plugin didn't find the 'java' plugin in the '${project.name}' project.\n"+
                        "This is a requirement for using j2objc. If you are migrating an existing\n" +
                        "Android app, it is suggested that you create a separate 'shared' project\n" +
                        "without android dependencies and gradually migrate shared code there.\n" +
                        "Within that project, first apply the 'java' then 'j2objc' plugins by adding\n" +
                        "the following lines to the build.gradle file:\n" +
                        "\n" +
                        "apply plugin: 'java'\n" +
                        "apply plugin: 'j2objc'\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    // j2objc settings here\n" +
                        "}\n" +
                        "\n" +
                        "More Info: https://github.com/j2objc-contrib/j2objc-gradle/#usage"
                throw new InvalidUserDataException(message)
            }

            extensions.create("j2objcConfig", J2objcPluginExtension)
            afterEvaluate { evaluatedProject ->
                // Validate minimally required parameters.
                // j2objcHome() will throw the appropriate exception internally.
                assert J2objcUtils.j2objcHome(evaluatedProject)
                evaluatedProject.j2objcConfig.configureDefaults(evaluatedProject)
            }

            // This is an intermediate directory only.  Clients should use only directories
            // specified in j2objcConfig (or associated defaults in J2objcPluginExtension).
            def j2objcSrcGenDir = file("${buildDir}/j2objcSrcGen")

            // Produces a modest amount of output
            logging.captureStandardOutput LogLevel.INFO

            // If users need to generate extra files that j2objc depends on, they can make this task dependent
            // on such generation.
            tasks.create(name: "j2objcPreBuild", type: DefaultTask,
                    dependsOn: 'test') {
                description "Marker task for all tasks that must be complete before j2objc building"
            }

            tasks.create(name: "j2objcCycleFinder", type: J2objcCycleFinderTask,
                    dependsOn: 'j2objcPreBuild') {
                description "Run the cycle_finder tool on all Java source files"
            }

            // TODO @Bruno "build/source/apt" must be project.j2objcConfig.generatedSourceDirs no idea how to set it
            // there
            // Dependency may be added in project.plugins.withType for Java or Android plugin
            tasks.create(name: "j2objcTranslate", type: J2objcTranslateTask,
                    dependsOn: 'j2objcCycleFinder') {
                description "Translates all the java source files in to Objective-C using j2objc"
                additionalSrcFiles = files(
                        fileTree(dir: "build/source/apt",
                                include: "**/*.java")
                )
                // Output directory of 'j2objcTranslate', input for all other tasks
                srcGenDir = j2objcSrcGenDir
            }

            // Configures native compilation for the production library and the test executable.
            new J2objcNativeCompilation().apply(project, j2objcSrcGenDir)

            // Note the 'debugTestJ2objcExecutable' task is dynamically created by the objective-c plugin applied
            // on the above line.  It is specified by the testJ2objc native component.
            tasks.create(name: "j2objcTest", type: J2objcTestTask,
                    dependsOn: 'debugTestJ2objcExecutable') {
                description 'Runs all tests in the generated Objective-C code'
                testBinaryFile = file("${buildDir}/binaries/testJ2objcExecutable/debug/testJ2objc")
            }
            // 'check' task is added by 'java' plugin, it depends on 'test' and all the other verification tasks,
            // now including 'j2objcTest'.
            lateDependsOn(project, 'check', 'j2objcTest')

            tasks.create(name: 'j2objcAssemble', type: J2objcAssembleTask,
                    dependsOn: ['j2objcTest', 'buildAllObjcLibraries']) {
                description 'Copies final generated source after testing to assembly directories'
                srcGenDir = j2objcSrcGenDir
                libDir = file("${buildDir}/binaries/${project.name}-j2objcStaticLibrary")
            }
            lateDependsOn(project, 'assemble', 'j2objcAssemble')

            // TODO: Where shall we fit this task in the plugin lifecycle?
            tasks.create(name: 'j2objcXcode', type: J2objcXcodeTask,
                    dependsOn: 'j2objcTest') {
                description 'Depends on j2objc translation, create a Pod file link it to Xcode project'
                srcGenDir = j2objcSrcGenDir
            }
        }
    }

    // Has task named afterTaskName depend on the task named beforeTaskName, regardless of
    // whether afterTaskName has been created yet or not.
    // The before task must already exist.
    private def lateDependsOn(Project proj, String afterTaskName, String beforeTaskName) {
        assert null != proj.tasks.findByName(beforeTaskName)
        def afterTask = proj.tasks.findByName(afterTaskName)
        if (afterTask != null) {
            afterTask.dependsOn beforeTaskName
        } else {
            proj.tasks.whenTaskAdded { task ->
                if (task.name == afterTaskName) {
                    task.dependsOn beforeTaskName
                }
            }
        }
    }
}
