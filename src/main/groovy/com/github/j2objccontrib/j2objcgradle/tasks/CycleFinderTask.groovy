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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.UnionFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecException

/**
 * CycleFinder task checks for memory cycles that can cause memory leaks
 * since iOS doesn't have garbage collection.
 */
@CompileStatic
class CycleFinderTask extends DefaultTask {

    @InputFiles
    FileTree getSrcFiles() {
        // Note that translatePattern does not need to be an @Input because it is
        // solely an input to this method, which is already an input (via @InputFiles).
        FileTree allFiles = Utils.srcSet(project, 'main', 'java')
        allFiles = allFiles.plus(Utils.srcSet(project, 'test', 'java'))
        FileTree ret = allFiles
        if (J2objcConfig.from(project).translatePattern != null) {
            ret = allFiles.matching(J2objcConfig.from(project).translatePattern)
        }
        return ret
    }

    // All input files that could affect translation output, except those in j2objc itself.
    @InputFiles
    UnionFileCollection getAllInputFiles() {
        // Only care about changes in the generatedSourceDirs paths and not the contents
        // Assumes that any changes in generated code causes change in non-generated @Input
        return new UnionFileCollection([
                getSrcFiles(),
                project.files(getTranslateClasspaths()),
                project.files(getTranslateSourcepaths()),
                project.files(getGeneratedSourceDirs())
        ])
    }

    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    int getCycleFinderExpectedCycles() { return J2objcConfig.from(project).cycleFinderExpectedCycles }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    List<String> getCycleFinderArgs() { return J2objcConfig.from(project).cycleFinderArgs }

    @Input
    List<String> getTranslateClasspaths() { return J2objcConfig.from(project).translateClasspaths }

    @Input
    List<String> getTranslateSourcepaths() { return J2objcConfig.from(project).translateSourcepaths }

    @Input
    List<String> getGeneratedSourceDirs() { return J2objcConfig.from(project).generatedSourceDirs }

    @Input
    List<String> getTranslateJ2objcLibs() { return J2objcConfig.from(project).translateJ2objcLibs }

    @Input
    boolean getFilenameCollisionCheck() { return J2objcConfig.from(project).filenameCollisionCheck }


    @TaskAction
    void cycleFinder() {
        String cycleFinderExec = "${getJ2objcHome()}/cycle_finder"
        List<String> windowsOnlyArgs = new ArrayList<String>()
        if (Utils.isWindows()) {
            cycleFinderExec = 'java'
            windowsOnlyArgs.add('-jar')
            windowsOnlyArgs.add("${getJ2objcHome()}/lib/cycle_finder.jar".toString())
        }

        FileCollection fullSrcFiles = getSrcFiles()
        // TODO: extract common methods of Translate and Cycle Finder
        // TODO: Need to understand why generated source dirs are treated differently by CycleFinder
        // vs. translate task.  Here they are directly passed to the binary, but in translate
        // they are only on the translate source path (meaning they will only be translated with --build-closure).

        // Generated Files
        // Assumes that any changes in generated code causes change in non-generated @Input
        fullSrcFiles = fullSrcFiles.plus(Utils.javaTrees(project, getGeneratedSourceDirs()))

        UnionFileCollection sourcepathDirs = new UnionFileCollection([
                project.files(Utils.srcSet(project, 'main', 'java').getSrcDirs()),
                project.files(Utils.srcSet(project, 'test', 'java').getSrcDirs()),
                project.files(getTranslateSourcepaths()),
                project.files(getGeneratedSourceDirs())
        ])
        String sourcepathArg = Utils.joinedPathArg(sourcepathDirs)

        UnionFileCollection classpathFiles = new UnionFileCollection([
                project.files(getTranslateClasspaths()),
                project.files(Utils.j2objcLibs(getJ2objcHome(), getTranslateJ2objcLibs()))
        ])
        // TODO: comment explaining ${project.buildDir}/classes
        String classpathArg = Utils.joinedPathArg(classpathFiles) + ":${project.buildDir}/classes"

        ByteArrayOutputStream output = new ByteArrayOutputStream()
        try {
            execCycleFinder(cycleFinderExec, windowsOnlyArgs, sourcepathArg, classpathArg, fullSrcFiles, output)

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
                        "Unexpected number of cycles found:\n" +
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


    ExecResult execCycleFinder(String cycleFinderExec, List<String> windowsOnlyArgs, String sourcepathArg,
                               String classpathArg, FileCollection fullSrcFiles, ByteArrayOutputStream output) {
        return Utils.projectExec(project, {
            executable cycleFinderExec
            windowsOnlyArgs.each { String windowsOnlyArg ->
                args windowsOnlyArg
            }

            // Arguments
            args "-sourcepath", sourcepathArg
            args "-classpath", classpathArg
            getCycleFinderArgs().each { String cycleFinderArg ->
                args cycleFinderArg
            }

            // File Inputs
            fullSrcFiles.each { File file ->
                args file.path
            }

            setErrorOutput output
            setStandardOutput output
        })
    }
}
