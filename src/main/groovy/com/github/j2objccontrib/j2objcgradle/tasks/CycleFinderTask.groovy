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

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecException

/**
 * CycleFinder task checks for memory cycles that can cause memory leaks
 * since iOS doesn't have garbage collection.
 */
class CycleFinderTask extends DefaultTask {

    @InputFiles
    FileCollection getSrcFiles() {
        // Note that translatePattern does not need to be an @Input because it is
        // solely an input to this method, which is already an input (via @InputFiles).
        FileCollection allFiles = Utils.srcDirs(project, 'main', 'java')
        allFiles = allFiles.plus(Utils.srcDirs(project, 'test', 'java'))
        if (project.j2objcConfig.translatePattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.translatePattern)
        }
        return allFiles
    }

    // All input files that could affect translation output, except those in j2objc itself.
    @InputFiles
    FileCollection getAllInputFiles() {
        FileCollection allFiles = getSrcFiles()
        if (getTranslateSourcepaths()) {
            List<String> translateSourcepathPaths = getTranslateSourcepaths().split(':') as List<String>
            translateSourcepathPaths.each { String sourcePath ->
                allFiles = allFiles.plus(project.files(sourcePath))
            }
        }
        generatedSourceDirs.each { String sourceDir ->
            allFiles = allFiles.plus(project.files(sourceDir))
        }
        translateClassPaths.each { String classPath ->
            allFiles = allFiles.plus(project.files(classPath))
        }
        return allFiles
    }

    // CycleFinder output - Gradle requires output for UP-TO-DATE checks
    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    int getCycleFinderExpectedCycles() { return project.j2objcConfig.cycleFinderExpectedCycles }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    @Optional
    List<String> getCycleFinderArgs() { return project.j2objcConfig.cycleFinderArgs }

    @Input @Optional
    String getTranslateSourcepaths() { return project.j2objcConfig.translateSourcepaths }

    @Input
    boolean getFilenameCollisionCheck() { return project.j2objcConfig.filenameCollisionCheck }

    @Input
    List<String> getGeneratedSourceDirs() { return project.j2objcConfig.generatedSourceDirs }

    @Input
    List<String> getTranslateClassPaths() { return project.j2objcConfig.translateClassPaths }

    @Input
    List<String> getTranslateJ2objcLibs() { return project.j2objcConfig.translateJ2objcLibs }


    @TaskAction
    void cycleFinder() {
        cycleFinderWithExec(project)
    }

    @VisibleForTesting
    void cycleFinderWithExec(Project projectExec) {

        String cycleFinderExec = "${getJ2objcHome()}/cycle_finder"
        List<String> windowsOnlyArgs = new ArrayList<String>()
        if (Utils.isWindows()) {
            cycleFinderExec = 'java'
            windowsOnlyArgs.add('-jar')
            windowsOnlyArgs.add("${getJ2objcHome()}/lib/cycle_finder.jar")
        }

        String sourcepath = Utils.sourcepathJava(project)

        // Generated Files
        // TODO: Need to understand why generated source dirs are treated differently by CycleFinder
        // vs. translate task.  Here they are directly passed to the binary, but in translate
        // they are only on the translate source path (meaning they will only be translated with --build-closure).
        FileCollection fullSrcFiles = Utils.addJavaFiles(
                project, getSrcFiles(), getGeneratedSourceDirs())
        sourcepath += Utils.absolutePathOrEmpty(
                project, getGeneratedSourceDirs())

        // Additional Sourcepaths, e.g. source jars
        if (getTranslateSourcepaths()) {
            logger.debug "Add to sourcepath: ${getTranslateSourcepaths()}"
            sourcepath += ":${getTranslateSourcepaths()}"
        }

        String classPathArg = Utils.getClassPathArg(
                project, getJ2objcHome(), getTranslateClassPaths(), getTranslateJ2objcLibs())

        ByteArrayOutputStream output = new ByteArrayOutputStream()
        try {
            // Might be injected project, otherwise project.exec {...}
            projectExec.exec {
                executable cycleFinderExec
                windowsOnlyArgs.each { String windowsOnlyArg ->
                    args windowsOnlyArg
                }

                // Arguments
                args "-sourcepath", sourcepath
                if (classPathArg.size() > 0) {
                    args "-classpath", classPathArg
                }
                getCycleFinderArgs().each { String cycleFinderArg ->
                    args cycleFinderArg
                }

                // File Inputs
                fullSrcFiles.each { File file ->
                    args file.path
                }

                errorOutput output;
                standardOutput output;
            }

            logger.debug "CycleFinder found 0 cycles"
            assert 0 == getCycleFinderExpectedCycles()


        } catch (ExecException exception) {
            // Expected exception for non-zero exit of process

            String outputStr = output.toString()
            // matchNumberRegex throws exception if regex isn't found
            int cyclesFound = Utils.matchNumberRegex(outputStr, /(\d+) CYCLES FOUND/)
            if (cyclesFound != getCycleFinderExpectedCycles()) {
                logger.error outputStr
                String message =
                        "Unexpected number of cycles found:\n" +
                        "Expected Cycles:  ${getCycleFinderExpectedCycles()}\n" +
                        "Actual Cycles:    $cyclesFound\n" +
                        "\n" +
                        "You should investigate the change and if ok, modify build.gradle:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    cycleFinderExpectedCycles $cyclesFound\n" +
                        "}\n"
                throw new IllegalArgumentException(message)
            }
            // Suppress exception when cycles found == cycleFinderExpectedCycles
        }

        reportFile.write(output.toString())
        logger.debug "CycleFinder Output: ${reportFile.path}"
    }
}
