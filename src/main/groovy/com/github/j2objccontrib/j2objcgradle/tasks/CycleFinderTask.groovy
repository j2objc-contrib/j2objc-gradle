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

/**
 * CycleFinder task checks for memory cycles that can cause memory leaks
 * since iOS doesn't have garbage collection.
 */
@CompileStatic
class CycleFinderTask extends DefaultTask {

    @InputFiles
    FileCollection getSrcInputFiles() {
        // Note that translatePattern does not need to be an @Input because it is
        // solely an input to this method, which is already an input (via @InputFiles).
        FileTree allFiles = Utils.srcSet(project, 'main', 'java')
        allFiles = allFiles.plus(Utils.srcSet(project, 'test', 'java'))
        FileTree ret = allFiles
        if (J2objcConfig.from(project).translatePattern != null) {
            ret = allFiles.matching(J2objcConfig.from(project).translatePattern)
        }
        return Utils.mapSourceFiles(project, ret, getTranslateSourceMapping())
    }

    // All input files that could affect translation output, except those in j2objc itself.
    @InputFiles
    UnionFileCollection getAllInputFiles() {
        // Only care about changes in the generatedSourceDirs paths and not the contents
        // Assumes that any changes in generated code causes change in non-generated @Input
        return new UnionFileCollection([
                getSrcInputFiles(),
                project.files(getTranslateClasspaths()),
                project.files(getTranslateSourcepaths()),
                project.files(getGeneratedSourceDirs())
        ])
    }

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
    Map<String, String> getTranslateSourceMapping() { return J2objcConfig.from(project).translateSourceMapping }

    @Input
    boolean getFilenameCollisionCheck() { return J2objcConfig.from(project).getFilenameCollisionCheck() }


    // Output required for task up-to-date checks
    @OutputFile
    File getReportFile() { project.file("${project.buildDir}/reports/${name}.out") }


    @TaskAction
    void cycleFinder() {
        String cycleFinderExec = getJ2objcHome() + Utils.fileSeparator() + 'cycle_finder'
        List<String> windowsOnlyArgs = new ArrayList<String>()
        if (Utils.isWindows()) {
            cycleFinderExec = 'java'
            windowsOnlyArgs.add('-jar')
            windowsOnlyArgs.add("${getJ2objcHome()}/lib/cycle_finder.jar".toString())
        }

        FileCollection fullSrcFiles = getSrcInputFiles()
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
        String classpathArg = Utils.joinedPathArg(classpathFiles) +
                              Utils.pathSeparator() + "${project.buildDir}/classes"

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // Capturing group is the cycle count, i.e. '\d+'
        String cyclesFoundRegex = /(\d+) CYCLES FOUND/

        try {
            logger.debug('CycleFinderTask - projectExec:')
            Utils.projectExec(project, stdout, stderr, cyclesFoundRegex, {
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

                setStandardOutput stdout
                setErrorOutput stderr
            })

            logger.debug("CycleFinder found 0 cycles")
            assert 0 == getCycleFinderExpectedCycles()

        } catch (Exception exception) {
            // ExecException is converted to InvalidUserDataException in Utils.projectExec(...)

            if (!Utils.isProjectExecNonZeroExit(exception)) {
                throw exception
            }
            
            String cyclesFoundStr = Utils.matchRegexOutputs(stdout, stderr, cyclesFoundRegex)
            if (!cyclesFoundStr?.isInteger()) {
                String message =
                        exception.toString() + '\n' +
                        'CycleFinder completed could not find expected output.\n' +
                        'Failed Regex Match cyclesFoundRegex: ' +
                        Utils.escapeSlashyString(cyclesFoundRegex) + '\n' +
                        'Found: ' + cyclesFoundStr
                throw new InvalidUserDataException(message, exception)
            }

            // Matched (XX CYCLES FOUND), so assert on cyclesFound
            int cyclesFound = cyclesFoundStr.toInteger()
            if (cyclesFound != getCycleFinderExpectedCycles()) {
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
        } finally {
            // Write output always.
            getReportFile().write(Utils.stdOutAndErrToLogString(stdout, stderr))
            logger.debug("CycleFinder Output: ${getReportFile().path}")
        }
    }
}
