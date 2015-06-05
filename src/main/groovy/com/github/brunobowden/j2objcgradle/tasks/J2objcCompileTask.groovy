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

package com.github.brunobowden.j2objcgradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 *
 */
class J2objcCompileTask extends DefaultTask {

    // Generated ObjC source files
    @InputDirectory
    File srcDir

    // TestRunner for running ObjC unit tests
    @OutputFile
    File destFile

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getJ2ObjCHome() { return J2objcUtils.j2objcHome(project) }

    @Input
    String getCompileFlags() { return project.j2objcConfig.compileFlags }

    @Input
    boolean getCompileSkip() { return project.j2objcConfig.compileSkip }

    @Input
    boolean getTestSkip() { return project.j2objcConfig.testSkip }


    @TaskAction
    def compile() {
        if (getCompileSkip()) {
            assert getTestSkip()
            // TODO: When we use task.enabled, dependencies will handle
            //       this correctly.  Currently, touching the file is required to
            //       avoid j2objcTest from complaining about a lack of input files.
            destFile.createNewFile()
            logger.debug "Skipping j2objcCompile"
            return
        }
        if (J2objcUtils.isWindows()) {
            throw new InvalidUserDataException(
                    "Windows only supports j2objc translation. To compile and test code, " +
                    "please develop on a Mac.");
        }

        // TODO: copy / reference test resources

        // No include / exclude regex as unlikely for compile to fail after successful translation

        logger.debug "Compiling test binary: " + destFile.path
        project.exec {
            executable getJ2ObjCHome() + "/j2objcc"
            args "-I${srcDir}"
            args "-o", destFile.path

            args getCompileFlags().split()

            def srcFiles = project.files(project.fileTree(
                    dir: srcDir, includes: ["**/*.h", "**/*.m"]))
            srcFiles.each { file ->
                args file.path
            }
        }
    }
}
