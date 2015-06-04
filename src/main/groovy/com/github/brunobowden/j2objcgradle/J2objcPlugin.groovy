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

package com.github.brunobowden.j2objcgradle

import com.github.brunobowden.j2objcgradle.tasks.J2objcCompileTask
import com.github.brunobowden.j2objcgradle.tasks.J2objcCopyTask
import com.github.brunobowden.j2objcgradle.tasks.J2objcCycleFinderTask
import com.github.brunobowden.j2objcgradle.tasks.J2objcTestTask
import com.github.brunobowden.j2objcgradle.tasks.J2objcTranslateTask
import com.github.brunobowden.j2objcgradle.tasks.J2objcXcodeTask
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
            // TODO: dependency on project.j2objcConfig, so any setting change
            // TODO: invalidates all (ideally some) tasks and causes a rebuild
            extensions.create("j2objcConfig", J2objcPluginExtension)

            // Produces a modest amount of output
            logging.captureStandardOutput LogLevel.INFO

            tasks.create(name: "j2objcCycleFinder", type: J2objcCycleFinderTask) {
                description "Run the cycle_finder tool on all Java source files"
                // TODO: Once this is a proper plugin, we can switch this to use SourceSets.
                srcFiles = files(
                        fileTree(dir: projectDir,
                                include: "**/*.java",
                                exclude: relativePath(buildDir)))
            }

            // TODO @Bruno "build/source/apt" must be project.j2objcConfig.generatedSourceDirs no idea how to set it
            // there
            // Dependency may be added in project.plugins.withType for Java or Android plugin
            tasks.create(name: "j2objcTranslate", type: J2objcTranslateTask,
                    dependsOn: 'j2objcCycleFinder') {
                description "Translates all the java source files in to Objective-C using j2objc"
                // TODO: Once this is a proper plugin, we can switch this to use SourceSets.
                srcFiles = files(
                        fileTree(dir: projectDir,
                                include: "**/*.java",
                                exclude: relativePath(buildDir)) +
                        fileTree(dir: "build/source/apt",
                                include: "**/*.java")
                )
                destDir = file("${buildDir}/j2objc")
            }

            tasks.create(name: "j2objcCompile", type: J2objcCompileTask,
                    dependsOn: 'j2objcTranslate') {
                description "Compiles the j2objc generated Objective-C code to 'testrunner' binary"
                srcDir = file("${buildDir}/j2objc")
                destFile = file("${buildDir}/j2objcc/testrunner")
            }

            tasks.create(name: "j2objcTest", type: J2objcTestTask,
                    dependsOn: 'j2objcCompile') {
                description 'Runs all tests in the generated Objective-C code'
                srcFile = file("${buildDir}/j2objcc/testrunner")
                // Doesn't use 'buildDir' as missing full path with --no-package-directories flag
                // TODO: Once this is a proper plugin, we can switch this to use SourceSets.
                srcFiles = files(fileTree(dir: projectDir, includes: ["**/*Test.java"]))
            }

            tasks.create(name: 'j2objcCopy', type: J2objcCopyTask,
                    dependsOn: 'j2objcTest') {
                description 'Depends on j2objc translation and test, copies to destDir'
                // TODO: make "${buildDir}/j2objc" a shared config variable.
                srcDir = file("${buildDir}/j2objc")
            }

            tasks.create(name: 'j2objcXcode', type: J2objcXcodeTask,
                    dependsOn: 'j2objcTest') {
                description 'Depends on j2objc translation, create a Pod file link it to Xcode project'
                srcDir = file("${buildDir}/j2objc")
            }

            // Make sure the wider project builds successfully
            if (plugins.findPlugin('java')) {
                tasks.findByName('j2objcCycleFinder').dependsOn('test')
                // TODO: consider removing the com.android.application plugin support
            } else if (plugins.findPlugin('com.android.application')) {
                tasks.findByName('j2objcCycleFinder').dependsOn('assemble')
            } else {
                def message =
                        "j2objc plugin didn't find either 'java' or 'com.android.application'\n" +
                        "plugin (which was expected). When this is found, the j2objc plugin\n" +
                        "will build and run that first to make sure the project builds correctly.\n" +
                        "This will not be done here as it can't be found."
                logger.warn message
            }
        }
    }
}
