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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
/**
 * Uses 'lipo' binary to combine multiple architecture flavors of a library into a
 * single 'fat' library.
 */
class J2objcPackLibrariesTask extends DefaultTask {

    // Generated ObjC binaries
    @InputFiles
    def getInputLibraries() {
        def staticLibraryPath = "${project.buildDir}/binaries/${project.name}-j2objcStaticLibrary"
        return project.files([
                "$staticLibraryPath/ios_arm64$buildType/lib${project.name}-j2objc.a",
                "$staticLibraryPath/ios_armv7$buildType/lib${project.name}-j2objc.a",
                "$staticLibraryPath/ios_armv7s$buildType/lib${project.name}-j2objc.a",
                "$staticLibraryPath/ios_i386$buildType/lib${project.name}-j2objc.a",
                "$staticLibraryPath/ios_x86_64$buildType/lib${project.name}-j2objc.a",
        ])
    }

    @OutputDirectory
    File getOutputLibDir() {
        return project.file("${project.buildDir}/packedBinaries/${project.name}-j2objcStaticLibrary/ios$buildType")
    }

    // Debug or Release for each library
    @Input
    String buildType

    @TaskAction
    def lipoLibraries() {
        if (outputLibDir.exists()) {
            // Clear it out.
            outputLibDir.deleteDir()
            outputLibDir.mkdirs()
        }
        def output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable 'xcrun'

                args 'lipo'
                args '-create', '-output', "${outputLibDir}/lib${project.name}-j2objc.a"
                inputLibraries.each { libFile ->
                    args libFile.absolutePath
                }

                errorOutput output
                standardOutput output
            }

        } catch (Exception exception) {
            def outputStr = output.toString()
            logger.error "$name failed, output: "
            logger.error outputStr
            throw exception
        }
        logger.debug "$name output: "
        logger.debug output.toString()
    }
}
