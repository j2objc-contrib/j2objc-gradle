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

package com.github.j2objccontrib.j2objcgradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 *
 */
class J2objcCopyTask extends DefaultTask {

    // Generated ObjC source files
    @InputDirectory
    File srcDir

    // TODO: needs output for UP-TO-DATE checks

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input @Optional
    String getDestDir() { return project.j2objcConfig.destDir }

    @Input @Optional
    String getDestDirTest() { return project.j2objcConfig.destDirTest }

    private def clearDestDirWithChecks(File destDir, String name) {
        def destFiles = project.files(project.fileTree(
                dir: destDir, excludes: ["**/*.h", "**/*.m"]))
        // Warn if deleting non-generated objc files from destDir
        destFiles.each { file ->
            def message =
                    "Unexpected files in $name - this folder should contain ONLY j2objc\n" +
                    "generated files Objective-C. The folder contents are deleted to remove\n" +
                    "files that are nolonger generated. Please check the directory and remove\n" +
                    "any files that don't end with Objective-C extensions '.h' and '.m'.\n" +
                    "$name: ${destDir.path}\n" +
                    "Unexpected file for deletion: ${file.path}"
            throw new InvalidUserDataException(message)
        }
        // TODO: better if this was a sync operation as it does deletes automatically
        logger.debug "Deleting $name to fill with generated objc files... " + destDir.path
        project.delete destDir
    }

    @TaskAction
    def destCopy() {
        if (getDestDir() == null) {
            def message = "You must configure the location where the generated files are " +
                          "copied for Xcode. This is done in your build.gradle, for example:\n" +
                          "\n" +
                          "j2objcConfig {\n" +
                          "    destDir null // e.g. \"\${" + "projectDir}/../Xcode/j2objc-generated\"\n" +
                          "}"
            throw new InvalidUserDataException(message)
        }

        def destDir = project.file(getDestDir())
        clearDestDirWithChecks(destDir, 'destDir')

        project.copy {
            includeEmptyDirs = false
            from srcDir
            into destDir
            // TODO: this isn't precise, main source can be suffixed with Test as well.
            // Would be best to somehow keep the metadata about whether a file was from the
            // main sourceset or the test sourceset.
            // Don't copy the test code
            exclude "**/*Test.h"
            exclude "**/*Test.m"
        }

        if (getDestDirTest() != null) {
            def destDirTest = project.file(getDestDirTest())
            if (destDirTest != destDir) {
                // If we want main source and test source in one directory, then don't
                // re-delete the main directory where we just put files!
                clearDestDirWithChecks(destDirTest, 'destDirTest')
            }
            project.copy {
                includeEmptyDirs = false
                from srcDir
                into destDirTest
                // Only copy the test code
                include "**/*Test.h"
                include "**/*Test.m"
            }
        } else {
            logger.debug 'Discard test sources since destDirTest == null'
        }
    }
}
