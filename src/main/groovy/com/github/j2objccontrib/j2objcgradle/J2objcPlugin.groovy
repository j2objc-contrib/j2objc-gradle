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
import com.github.j2objccontrib.j2objcgradle.tasks.J2objcPackLibrariesTask
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
 */

class J2objcPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // This avoids a lot of "project." prefixes, such as "project.tasks.create"
        project.with {
            if (!plugins.hasPlugin('java')) {
                def message =
                        "j2objc plugin didn't find the 'java' plugin in the '${project.name}' project.\n"+
                        "This is a requirement for using j2objc. Please see usage information at:\n" +
                        "\n" +
                        "https://github.com/j2objc-contrib/j2objc-gradle/#usage"
                throw new InvalidUserDataException(message)
            }

            extensions.create('j2objcConfig', J2objcPluginExtension)
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
            tasks.create(name: 'j2objcPreBuild', type: DefaultTask,
                    dependsOn: 'test') {
                group 'build'
                description "Marker task for all tasks that must be complete before j2objc building"
            }

            // j2objcCycleFinder is disabled by default as it's complex to use and understand.
            // TODO: consider enabling by default if it's possible to make it easier to use.
            // To enable the j2objcCycleFinder task, add the following to build.gradle:
            // j2objcCycleFinder { enabled = true }
            tasks.create(name: 'j2objcCycleFinder', type: J2objcCycleFinderTask,
                    dependsOn: 'j2objcPreBuild') {
                group 'build'
                description "Run the cycle_finder tool on all Java source files"
                enabled false
            }

            // TODO @Bruno "build/source/apt" must be project.j2objcConfig.generatedSourceDirs no idea how to set it
            // there
            // Dependency may be added in project.plugins.withType for Java or Android plugin
            tasks.create(name: 'j2objcTranslate', type: J2objcTranslateTask,
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

            // Configures native compilation for the production library and the test executable.
            new J2objcNativeCompilation().apply(project, j2objcSrcGenDir)

            // Note the 'debugTestJ2objcExecutable' task is dynamically created by the objective-c plugin applied
            // on the above line.  It is specified by the testJ2objc native component.
            tasks.create(name: 'j2objcTest', type: J2objcTestTask,
                    dependsOn: ['test', 'debugTestJ2objcExecutable']) {
                group 'verification'
                // This transitively depends on the 'test' task from the java plugin
                description 'Runs all tests in the generated Objective-C code'
                testBinaryFile = file("${buildDir}/binaries/testJ2objcExecutable/debug/testJ2objc")
            }
            // 'check' task is added by 'java' plugin, it depends on 'test' and
            // all the other verification tasks, now including 'j2objcTest'.
            lateDependsOn(project, 'check', 'j2objcTest')

            tasks.create(name: 'j2objcPackLibrariesDebug', type: J2objcPackLibrariesTask,
                    dependsOn: 'j2objcBuildAllObjcLibraries') {
                group 'build'
                description 'Packs multiple architectures into a single debug static library'
                buildType = 'Debug'
            }

            tasks.create(name: 'j2objcPackLibrariesRelease', type: J2objcPackLibrariesTask,
                    dependsOn: 'j2objcBuildAllObjcLibraries') {
                group 'build'
                description 'Packs multiple architectures into a single release static library'
                buildType = 'Release'
            }

            tasks.create(name: 'j2objcAssemble', type: J2objcAssembleTask,
                    dependsOn: ['j2objcBuildAllObjcLibraries',
                                'j2objcPackLibrariesDebug', 'j2objcPackLibrariesRelease']) {
                group 'build'
                description 'Copies final generated source after testing to assembly directories'
                srcGenDir = j2objcSrcGenDir
                libDir = file("${buildDir}/binaries/${project.name}-j2objcStaticLibrary")
                packedLibDir = file("${buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary")
            }
            lateDependsOn(project, 'assemble', 'j2objcAssemble')

            // TODO: Where shall we fit this task in the plugin lifecycle?
            tasks.create(name: 'j2objcXcode', type: J2objcXcodeTask,
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
    private def lateDependsOn(Project proj, String afterTaskName, String beforeTaskName) {
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
        proj.tasks.all { task ->
            if (task.name == afterTaskName) {
                task.dependsOn beforeTaskName
            }
        }
    }
}
