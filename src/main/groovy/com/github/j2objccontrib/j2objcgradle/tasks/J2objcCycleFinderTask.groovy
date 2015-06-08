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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
/**
 *
 */
class J2objcCycleFinderTask extends DefaultTask {

    @InputFiles
    FileCollection getSrcFiles() {
        // Note that translatePattern does not need to be an @Input because it is
        // solely an input to this method, which is already an input (via @InputFiles).
        def allFiles = J2objcUtils.srcDirs(project, 'main', 'java')
        allFiles = allFiles.plus(J2objcUtils.srcDirs(project, 'test', 'java'))
        if (project.j2objcConfig.translatePattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.translatePattern)
        }
        return allFiles
    }

    // All input files that could affect translation output, except those in j2objc itself.
    @InputFiles
    FileCollection getAllInputFiles() {
        FileCollection allFiles = srcFiles
        if (getTranslateSourcepaths()) {
            def translateSourcepathPaths = getTranslateSourcepaths().split(':') as List<String>
            translateSourcepathPaths.each {
                allFiles = allFiles.plus(project.files(it))
            }
        }
        generatedSourceDirs.each {
            allFiles = allFiles.plus(project.files(it))
        }
        translateClassPaths.each {
            allFiles = allFiles.plus(project.files('lib/' + it))
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
    String getJ2ObjCHome() { return J2objcUtils.j2objcHome(project) }

    @Input
    @Optional
    String getCycleFinderFlags() { return project.j2objcConfig.cycleFinderFlags }

    @Input @Optional
    String getTranslateSourcepaths() { return project.j2objcConfig.translateSourcepaths }

    @Input
    boolean getCycleFinderSkip() { return project.j2objcConfig.cycleFinderSkip }

    @Input
    boolean getFilenameCollisionCheck() { return project.j2objcConfig.filenameCollisionCheck }

    @Input
    List<String> getGeneratedSourceDirs() { return project.j2objcConfig.generatedSourceDirs }

    @Input
    List<String> getTranslateClassPaths() { return project.j2objcConfig.translateClassPaths }

    @Input
    List<String> getTranslateJ2objcLibs() { return project.j2objcConfig.translateJ2objcLibs }


    @TaskAction
    def cycleFinder() {

        if (getCycleFinderSkip()) {
            logger.debug "Skipping j2objcCycleFinder"
            return
        }

        def cycleFinderExec = getJ2ObjCHome() + "/cycle_finder"
        def windowsOnlyArgs = ""
        if (J2objcUtils.isWindows()) {
            cycleFinderExec = "java"
            windowsOnlyArgs = "-jar ${getJ2ObjCHome()}/lib/cycle_finder.jar"
        }

        if (getCycleFinderFlags() == null) {
            def message =
                    "CycleFinder is more difficult to setup and use, though it's hoped to improve\n" +
                    "this for the future. There are two ways to configure it in build.gradle:\n" +
                    "\n" +
                    "SIMPLE: set cycleFinderFlags to empty string:\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    cycleFinder true\n" +
                    "    cycleFinderFlags \"\"\n" +
                    "}\n" +
                    "\n" +
                    "DIFFICULT:\n" +
                    "1) Download the j2objc source:\n" +
                    "    https://github.com/google/j2objc\n" +
                    "2) Within your local j2objc repo run:\n" +
                    "    \$ (cd jre_emul && make java_sources_manifest)\n" +
                    "3) Configure j2objcConfig in build.gradle so CycleFinder uses j2objc source:\n" +
                    "j2objcConfig {\n" +
                    "    cycleFinder true\n" +
                    "    cycleFinderFlags (\n" +
                    "            \"--whitelist \${projectDir}/../../<J2OBJC_REPO>/jre_emul/cycle_whitelist.txt " +
                    "\\\n" +
                    "             --sourcefilelist \${projectDir}/../." +
                    "./<J2OBJC_REPO>/jre_emul/build_result/java_sources.mf\")\n" +
                    "    cycleFinderExpectedCycles 0\n" +
                    "}\n" +
                    "Also see: https://groups.google.com/forum/#!msg/j2objc-discuss/2fozbf6-liM/R83v7ffX5NMJ"
            throw new InvalidUserDataException(message)
        }

        def sourcepath = J2objcUtils.sourcepathJava(project)

        // Generated Files
        // TODO: Need to understand why generated source dirs are treated differently by CycleFinder
        // vs. translate task.  Here they are directly passed to the binary, but in translate
        // they are only on the translate source path (meaning they will only be translated with --build-closure).
        def fullSrcFiles = J2objcUtils.addJavaFiles(
                project, srcFiles, getGeneratedSourceDirs())
        sourcepath += J2objcUtils.absolutePathOrEmpty(
                project, getGeneratedSourceDirs())

        // Additional Sourcepaths, e.g. source jars
        if (getTranslateSourcepaths()) {
            logger.debug "Add to sourcepath: ${getTranslateSourcepaths()}"
            sourcepath += ":${getTranslateSourcepaths()}"
        }

        def classPathArg = J2objcUtils.getClassPathArg(
                project, getJ2ObjCHome(), getTranslateClassPaths(), getTranslateJ2objcLibs())

        def output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable cycleFinderExec

                args windowsOnlyArgs.split()
                args "-sourcepath", sourcepath

                if (classPathArg.size() > 0) {
                    args "-classpath", classPathArg
                }

                args getCycleFinderFlags().split()

                fullSrcFiles.each { file ->
                    args file.path
                }

                errorOutput output;
                standardOutput output;
            }

        } catch (Exception exception) {

            def outputStr = output.toString()
            int cyclesFound = J2objcUtils.matchNumberRegex(outputStr, /(\d+) CYCLES FOUND/)
            if (cyclesFound != getCycleFinderExpectedCycles()) {
                logger.error outputStr
                def message =
                        "Unexpected number of cycles founder:\n" +
                        "Cycles found:     ${cyclesFound}\n" +
                        "Expected cycles:  ${getCycleFinderExpectedCycles()}\n" +
                        "\n" +
                        "You should investigate the change and if ok, modify build.gradle:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    cycleFinderExpectedCycles ${cyclesFound}\n" +
                        "}\n"
                throw new Exception(message)
            }
            // Suppress exception when cycles found == cycleFinderExpectedCycles
        }

        reportFile.write(output.toString())
        logger.debug "CycleFinder Output: ${reportFile.path}"
    }
}
