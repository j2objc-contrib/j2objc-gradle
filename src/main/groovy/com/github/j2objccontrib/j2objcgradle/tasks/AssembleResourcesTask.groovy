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
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Assemble Resources Task copies main and test resources to separate
 * assembly directories for use by an iOS application.
 */
@CompileStatic
class AssembleResourcesTask extends DefaultTask {

    @InputFiles
    FileTree getMainTestResourcesFiles() {
        FileTree allFiles = Utils.srcSet(project, 'main', 'resources')
        allFiles = allFiles.plus(Utils.srcSet(project, 'test', 'resources'))
        return allFiles
    }


    @OutputDirectory
    File getDestSrcMainResDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'resources')
    }
    @OutputDirectory
    File getDestSrcTestResDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('test', 'resources')
    }


    @TaskAction
    void assembleResources() {
        assert getDestSrcMainResDirFile().absolutePath !=
               getDestSrcTestResDirFile().absolutePath
        Utils.syncResourcesTo(project, ['main'], getDestSrcMainResDirFile())
        Utils.syncResourcesTo(project, ['test'], getDestSrcTestResDirFile())
    }
}
