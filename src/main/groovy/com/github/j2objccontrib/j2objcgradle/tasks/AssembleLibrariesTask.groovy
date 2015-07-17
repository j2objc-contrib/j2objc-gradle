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

import com.github.j2objccontrib.j2objcgradle.J2objcConfig
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Assemble task copies generated libraries to assembly directories for
 * use by an iOS application.
 */
@CompileStatic
class AssembleLibrariesTask extends DefaultTask {
    // Generated ObjC binaries
    @InputDirectory
    File libDir

    @InputDirectory
    File packedLibDir

    @OutputDirectory
    File getDestLibDir() {
        return project.file(getDestLibDirPath())
    }

    @Input
    // Debug or Release
    String buildType

    // j2objcConfig dependencies for UP-TO-DATE checks

    // We keep these strings as @Input properties in addition to the @OutputDirectory methods above because,
    // for example, whether or not the main source and test source are identical affects execution of this task.
    @Input
    String getDestLibDirPath() { return J2objcConfig.from(project).destLibDir }

    @TaskAction
    void destCopy() {
        // We don't need to clear out the library path, our libraries can co-exist
        // with other libraries if the user wishes them to.

        Utils.projectCopy(project, {
            includeEmptyDirs = true
            from libDir
            from packedLibDir
            into destLibDir
            include "*$buildType/*.a"
        })
    }
}
