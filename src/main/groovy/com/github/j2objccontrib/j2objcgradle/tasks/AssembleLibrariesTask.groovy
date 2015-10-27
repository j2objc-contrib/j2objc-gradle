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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction

/**
 * Assemble Libraries Task copies generated libraries to assembly directories for
 * use by an iOS application.
 */
@CompileStatic
class AssembleLibrariesTask extends DefaultTask {

    // Generated ObjC libraries
    File srcLibDir
    File srcPackedLibDir

    // Only treat as inputs the files of relevant buildType.
    @InputFiles
    FileTree getSrcLibs() {
        return project.fileTree(dir: srcLibDir, include: getLibraryPattern()) +
               project.fileTree(dir: srcPackedLibDir, include: getLibraryPattern())
    }

    // Debug or Release
    @Input
    String buildType

    String getLibraryPattern() { return "*$buildType/*.a" }

    File getDestLibDirFile() { return J2objcConfig.from(project).getDestLibDirFile() }

    // Only treat as outputs the directories of relevant buildType.
    @SuppressWarnings('unused')
    @OutputDirectories
    List<File> getDestLibSubdirs() {
        List<File> inputDirs = []
        FileFilter filter = new FileFilter() {
            @Override boolean accept(File pathname) {
                // We care only about architecture directories (other files could be in here,
                // like .DS_Store)
                return pathname.isDirectory() && pathname.name.endsWith(buildType)
            }
        }
        File[] filtered = srcLibDir.listFiles(filter)
        // Can legitimately be null when the parent directory doesn't exist.
        // If the prior tasks are disabled, it would be correct for this task to pack nothing.
        // When testing we don't actually create all these directories either.
        if (filtered != null) {
            inputDirs.addAll(filtered)
        }
        filtered = srcPackedLibDir.listFiles(filter)
        if (filtered != null) {
            inputDirs.addAll(filtered)
        }
        return inputDirs.collect({ project.file("${getDestLibDirFile().absolutePath}/${it.name}") })
    }

    @TaskAction
    void assembleLibraries() {
        // We don't need to clear out the library path, our libraries can co-exist
        // with other libraries if the user wishes them to.

        assert buildType in ['Debug', 'Release']

        Utils.projectCopy(project, {
            includeEmptyDirs = true
            from srcLibDir
            from srcPackedLibDir
            into getDestLibDirFile()
            include getLibraryPattern()
        })
    }
}
