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
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
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
        FileCollection allFiles = project.files()
        allFiles += Utils.srcSet(project, 'main', 'java')
        allFiles += Utils.srcSet(project, 'test', 'java')
        if (project.j2objcConfig.translatePattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.translatePattern)
        }
        return allFiles
    }

    // All input files that could affect translation output, except those in j2objc itself.
    @InputFiles
    FileCollection getAllInputFiles() {
        FileCollection allFiles = getSrcFiles()
        allFiles += project.files(getTranslateClasspaths())
        allFiles += project.files(getTranslateSourcepaths())
        allFiles += project.files(getGeneratedSourceDirs())
        // Only care about changes in the generatedSourceDirs paths and not the contents
        // Assumes that any changes in generated code causes change in non-generated @Input
        return allFiles
    }

    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    int getCycleFinderExpectedCycles() { return project.j2objcConfig.cycleFinderExpectedCycles }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    List<String> getCycleFinderArgs() { return project.j2objcConfig.cycleFinderArgs }

    @Input
    List<String> getTranslateClasspaths() { return project.j2objcConfig.translateClasspaths }

    @Input
    List<String> getTranslateSourcepaths() { return project.j2objcConfig.translateSourcepaths }

    @Input
    List<String> getGeneratedSourceDirs() { return project.j2objcConfig.generatedSourceDirs }

    @Input
    List<String> getTranslateJ2objcLibs() { return project.j2objcConfig.translateJ2objcLibs }

    @Input
    boolean getFilenameCollisionCheck() { return project.j2objcConfig.filenameCollisionCheck }


    @TaskAction
    void cycleFinder() {
        String cycleFinderExec = "${getJ2objcHome()}/cycle_finder"
        List<String> windowsOnlyArgs = new ArrayList<String>()
        if (Utils.isWindows()) {
            cycleFinderExec = 'java'
            windowsOnlyArgs.add('-jar')
            windowsOnlyArgs.add("${getJ2ObjCHome()}/lib/cycle_finder.jar")
        }

        FileCollection fullSrcFiles = getSrcFiles()
        // TODO: extract common methods of Translate and Cycle Finder
        // TODO: Need to understand why generated source dirs are treated differently by CycleFinder
        // vs. translate task.  Here they are directly passed to the binary, but in translate
        // they are only on the translate source path (meaning they will only be translated with --build-closure).

        // Generated Files
        // Assumes that any changes in generated code causes change in non-generated @Input
        fullSrcFiles = fullSrcFiles.plus(Utils.javaTrees(project, getGeneratedSourceDirs()))

        FileCollection sourcepathDirs = project.files()
        sourcepathDirs += project.files(Utils.srcSet(project, 'main', 'java').getSrcDirs())
        sourcepathDirs += project.files(Utils.srcSet(project, 'test', 'java').getSrcDirs())
        sourcepathDirs += project.files(getTranslateSourcepaths())
        sourcepathDirs += project.files(getGeneratedSourceDirs())
        String sourcepathArg = Utils.joinedPathArg(sourcepathDirs)

        FileCollection classpathFiles = project.files()
        classpathFiles += project.files(getTranslateClasspaths())
        classpathFiles += project.files(Utils.j2objcLibs(getJ2objcHome(), getTranslateJ2objcLibs()))
        // TODO: comment explaining ${project.buildDir}/classes
        String classpathArg = Utils.joinedPathArg(classpathFiles) + ":${project.buildDir}/classes"

        ByteArrayOutputStream output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable cycleFinderExec

                // Arguments
                args "-sourcepath", sourcepathArg
                args "-classpath", classpathArg
                getCycleFinderArgs().each { String cycleFinderArg ->
                    args cycleFinderArg
                }

                fullSrcFiles.each { File file ->
                    args file.path
                }

                errorOutput output;
                standardOutput output;
            }

            logger.debug("CycleFinder found 0 cycles")
            assert 0 == getCycleFinderExpectedCycles()

        } catch (ExecException exception) {
            // Expected exception for non-zero exit of process

            // TODO show output for build failure instead of matchNumberRegex exception
            String outputStr = output.toString()
            // matchNumberRegex throws exception if regex isn't found
            int cyclesFound = Utils.matchNumberRegex(outputStr, /(\d+) CYCLES FOUND/)
            if (cyclesFound != getCycleFinderExpectedCycles()) {
                logger.error(outputStr)
                String message =
                        "Unexpected number of cycles founder:\n" +
                        "Expected Cycles:  ${getCycleFinderExpectedCycles()}\n" +
                        "Actual Cycles:    $cyclesFound\n" +
                        "\n" +
                        "You should investigate the change and if ok, modify build.gradle:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    cycleFinderExpectedCycles $cyclesFound\n" +
                        "}\n"
                throw new InvalidUserDataException(message)
            }
            // Suppress exception when cycles found == cycleFinderExpectedCycles
        }

        reportFile.write(output.toString())
        logger.debug("CycleFinder Output: ${reportFile.path}")
    }
}
