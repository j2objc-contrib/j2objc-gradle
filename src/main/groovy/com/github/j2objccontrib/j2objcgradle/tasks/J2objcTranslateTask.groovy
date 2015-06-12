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
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
/**
 *
 */
class J2objcTranslateTask extends DefaultTask {

    // Source files outside of the Java sourceSets of main and test.
    FileCollection additionalSrcFiles

    // Source files part of the Java sourceSets of main and test.
    @InputFiles
    FileCollection getSrcFiles() {
        // Note that neither additionalSrcFiles nor translatePattern need
        // to be @Inputs because they are solely inputs to this method, which
        // is already an input.
        def allFiles = J2objcUtils.srcDirs(project, 'main', 'java')
        allFiles = allFiles.plus(J2objcUtils.srcDirs(project, 'test', 'java'))

        if (project.j2objcConfig.translatePattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.translatePattern)
        }
        if (additionalSrcFiles != null) {
            allFiles = allFiles.plus(additionalSrcFiles)
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
            allFiles = allFiles.plus(project.files(it))
        }
        return allFiles
    }

    // Generated ObjC files
    @OutputDirectory
    File srcGenDir


    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getJ2ObjCHome() { return J2objcUtils.j2objcHome(project) }

    @Input
    String getTranslateFlags() { return project.j2objcConfig.translateFlags }

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
    def translate(IncrementalTaskInputs inputs) {
        def translateFlags = project.j2objcConfig.translateFlags
        // Don't evaluate this expensive property multiple times.
        def originalSrcFiles = srcFiles

        logger.debug "All source files: " + originalSrcFiles.getFiles().size()

        FileCollection srcFilesChanged
        if (translateFlags.contains('--build-closure') && !project.j2objcConfig.UNSAFE_incrementalBuildClosure) {
            // We cannot correctly perform incremental compilation with --build-closure.
            // Consider the example where src/main/Something.java is deleted, we would not
            // be able to also delete the files that only Something.java depends on.
            // Due to this issue, incremental builds with --build-closure are enabled ONLY
            // if the user requests it with the UNSAFE_incrementalBuildClosure flag.
            // TODO: One correct way to incrementally compile with --build-closure would be to use
            // allInputFiles someway, but this will require some research.
            if (srcGenDir.exists()) {
                srcGenDir.deleteDir()
                srcGenDir.mkdirs()
            }
            srcFilesChanged = originalSrcFiles
        } else {
            srcFilesChanged = project.files()
            inputs.outOfDate { change ->
                // We must filter by srcFiles, since all possible input files are @InputFiles to this task.
                if (originalSrcFiles.contains(change.file)) {
                    logger.debug "New or Updated file: " + change.file
                    srcFilesChanged += project.files(change.file)
                }
            }
            def removedFileNames = []
            inputs.removed { change ->
                // We must filter by srcFiles, since all possible input files are @InputFiles to this task.
                if (originalSrcFiles.contains(change.file)) {
                    logger.debug "Removed file: " + change.file.name
                    def nameWithoutExt = file.name.toString().replaceFirst("\\..*", "")
                    removedFileNames += nameWithoutExt
                }
            }
            logger.debug "Removed files: " + removedFileNames.size()

            logger.debug "New or Updated files: " + srcFilesChanged.getFiles().size()
            FileCollection unchangedSrcFiles = originalSrcFiles - srcFilesChanged
            logger.debug "Unchanged files: " + unchangedSrcFiles.getFiles().size()

            def translatedFiles = 0
            if (srcGenDir.exists()) {
                FileCollection destFiles = project.files(project.fileTree(
                        dir: srcGenDir, includes: ["**/*.h", "**/*.m"]))

                // remove translated .h and .m files which has no corresponding .java files anymore
                destFiles.each { File file ->
                    def nameWithoutExt = file.name.toString().replaceFirst("\\..*", "")
                    if (removedFileNames.contains(nameWithoutExt)) {
                        file.delete()
                    }
                }
                // compute the number of translated files
                translatedFiles = destFiles.getFiles().size()
            }

            // add java classpath base to classpath for incremental translation
            // we have to check if translation has been done before or not
            // if it is only an incremental build we must remove the --build-closure
            // argument to make fewer translations of dependent classes
            // NOTE: There is one case which fails, when you have translated the code
            // make an incremental change which refers to a not yet translated class from a
            // source lib. In this case due to not using --build-closure the dependent source
            // will not be translated, this can be fixed with a clean and fresh build.
            // Due to this issue, incremental builds with --build-closure are enabled ONLY
            // if the user requests it with the UNSAFE_incrementalBuildClosure flag.
            if (translatedFiles > 0 && project.j2objcConfig.UNSAFE_incrementalBuildClosure) {
                translateFlags = translateFlags.toString().replaceFirst("--build-closure", "").trim()
            }
        }

        def j2objcExec = getJ2ObjCHome() + "/j2objc"
        def windowsOnlyArgs = ""
        if (J2objcUtils.isWindows()) {
            j2objcExec = "java"
            windowsOnlyArgs = "-jar ${getJ2ObjCHome()}/lib/j2objc.jar"
        }

        def sourcepath = J2objcUtils.sourcepathJava(project)

        // Additional Sourcepaths, e.g. source jars
        if (getTranslateSourcepaths()) {
            logger.debug "Add to sourcepath: ${getTranslateSourcepaths()}"
            sourcepath += ":${getTranslateSourcepaths()}"
        }

        // Generated Files
        sourcepath += J2objcUtils.absolutePathOrEmpty(project, getGeneratedSourceDirs())

        // TODO perform file collision check with already translated files in the srcGenDir
        if (getFilenameCollisionCheck()) {
            J2objcUtils.filenameCollisionCheck(srcFiles)
        }

        def classPathArg = J2objcUtils.getClassPathArg(
                project, getJ2ObjCHome(), getTranslateClassPaths(), getTranslateJ2objcLibs())

        classPathArg += ":${project.buildDir}/classes"

        def output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable j2objcExec

                args windowsOnlyArgs.split()
                args "-d", srcGenDir
                args "-sourcepath", sourcepath

                if (classPathArg.size() > 0) {
                    args "-classpath", classPathArg
                }

                args translateFlags.split()

                srcFilesChanged.each { file ->
                    args file.path
                }
                standardOutput output
                errorOutput output
            }

        } catch (Exception exception) {
            def outputStr = output.toString()
            logger.debug 'Translation output:'
            logger.debug outputStr
            // Put to stderr only the lines at fault.
            // We do not separate standardOutput and errorOutput in the exec
            // task, because the interleaved output is helpful for context.
            logger.error 'Error during translation:'
            logger.error J2objcUtils.filterJ2objcOutputForErrorLines(outputStr)
            // Gradle will helpfully tell the user to use --debug for more
            // output when the build fails.
            throw exception
        }

        logger.debug 'Translation output:'
        logger.debug output.toString()
    }
}
