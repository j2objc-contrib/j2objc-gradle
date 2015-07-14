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

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.process.internal.ExecException

/**
 * Translation task for Java to Objective-C using j2objc tool.
 */
class TranslateTask extends DefaultTask {

    // Source files outside of the Java sourceSets of main and test.
    FileCollection additionalSrcFiles

    // Source files part of the Java sourceSets of main and test.
    @InputFiles
    FileCollection getSrcFiles() {
        // Note that neither additionalSrcFiles nor translatePattern need
        // to be @Inputs because they are solely inputs to this method, which
        // is already an input.
        FileCollection allFiles = Utils.srcDirs(project, 'main', 'java')
        allFiles = allFiles.plus(Utils.srcDirs(project, 'test', 'java'))

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
        FileCollection allFiles = getSrcFiles()
        if (getTranslateSourcepaths()) {
            List<String> translateSourcepathPaths = getTranslateSourcepaths().split(':') as List<String>
            translateSourcepathPaths.each { String sourcePath ->
                allFiles = allFiles.plus(project.files(sourcePath))
            }
        }
        getGeneratedSourceDirs().each { String sourceDir ->
            allFiles = allFiles.plus(project.files(sourceDir))
        }
        getTranslateClassPaths().each { String classPath ->
            allFiles = allFiles.plus(project.files(classPath))
        }
        return allFiles
    }

    // Generated ObjC files
    @OutputDirectory
    File srcGenDir


    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    List<String> getTranslateArgs() { return project.j2objcConfig.translateArgs }

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
    void translate(IncrementalTaskInputs inputs) {
        List<String> translateArgs = getTranslateArgs()
        // Don't evaluate this expensive property multiple times.
        FileCollection originalSrcFiles = getSrcFiles()

        logger.debug "All source files: " + originalSrcFiles.getFiles().size()

        FileCollection srcFilesChanged
        if (('--build-closure' in translateArgs) && !project.j2objcConfig.UNSAFE_incrementalBuildClosure) {
            // We cannot correctly perform incremental compilation with --build-closure.
            // Consider the example where src/main/Something.java is deleted, we would not
            // be able to also delete the files that only Something.java depends on.
            // Due to this issue, incremental builds with --build-closure are enabled ONLY
            // if the user requests it with the UNSAFE_incrementalBuildClosure argument.
            // TODO: One correct way to incrementally compile with --build-closure would be to use
            // allInputFiles someway, but this will require some research.
            if (srcGenDir.exists()) {
                srcGenDir.deleteDir()
                srcGenDir.mkdirs()
            }
            srcFilesChanged = originalSrcFiles
        } else {
            boolean nonSourceFileChanged = false
            srcFilesChanged = project.files()
            inputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails details) {
                    // We must filter by srcFiles, since all possible input files are @InputFiles to this task.
                    if (originalSrcFiles.contains(details.file)) {
                        logger.debug "New or Updated file: " + details.file
                        srcFilesChanged += project.files(details.file)
                    } else {
                        nonSourceFileChanged = true
                        logger.debug "New or Updated non-source file: " + details.file
                    }
                }
            })
            List<String> removedFileNames = new ArrayList<>()
            inputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails details) {
                    // We must filter by srcFiles, since all possible input files are @InputFiles to this task.
                    if (originalSrcFiles.contains(details.file)) {
                        logger.debug "Removed file: " + details.file.name
                        String nameWithoutExt = details.file.name.toString().replaceFirst("\\..*", "")
                        removedFileNames += nameWithoutExt
                    } else {
                        nonSourceFileChanged = true
                        logger.debug "Removed non-source file: " + details.file
                    }
                }
            })
            logger.debug "Removed files: " + removedFileNames.size()

            logger.debug "New or Updated files: " + srcFilesChanged.getFiles().size()
            FileCollection unchangedSrcFiles = originalSrcFiles - srcFilesChanged
            logger.debug "Unchanged files: " + unchangedSrcFiles.getFiles().size()

            if (!nonSourceFileChanged) {
                // All changes were within srcFiles (i.e. in a Java source-set).
                int translatedFiles = 0
                if (srcGenDir.exists()) {
                    FileCollection destFiles = project.files(project.fileTree(
                            dir: srcGenDir, includes: ["**/*.h", "**/*.m"]))

                    // With --build-closure, files outside the source set can end up in the srcGen
                    // directory from prior translations.
                    // So only remove translated .h and .m files which has no corresponding .java files anymore
                    destFiles.each { File file ->
                        String nameWithoutExt = file.name.toString().replaceFirst("\\..*", "")
                        // TODO: Check for --no-package-directories when deciding whether
                        // to compare file name vs. full path.
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
                // if the user requests it with the UNSAFE_incrementalBuildClosure argument.
                if (translatedFiles > 0 && project.j2objcConfig.UNSAFE_incrementalBuildClosure) {
                    translateArgs.remove('--build-closure')
                }
            } else {
                // A change outside of the source set directories has occurred, so an incremental build isn't possible.
                // The most common such change is in the JAR for a dependent library, for example if Java project
                // that this project depends on had its source changed and was recompiled.
                if (srcGenDir.exists()) {
                    srcGenDir.deleteDir()
                    srcGenDir.mkdirs()
                }
                srcFilesChanged = originalSrcFiles
            }
        }

        String j2objcExecutable = "${getJ2objcHome()}/j2objc"
        List<String> windowsOnlyArgs = new ArrayList<String>()
        if (Utils.isWindows()) {
            j2objcExecutable = 'java'
            windowsOnlyArgs.add('-jar')
            windowsOnlyArgs.add("${getJ2objcHome()}/lib/j2objc.jar")
        }

        String sourcepath = Utils.sourcepathJava(project)

        // Additional Sourcepaths, e.g. source jars
        if (getTranslateSourcepaths()) {
            logger.debug "Add to sourcepath: ${getTranslateSourcepaths()}"
            sourcepath += ":${getTranslateSourcepaths()}"
        }

        // Generated Files
        sourcepath += Utils.absolutePathOrEmpty(project, getGeneratedSourceDirs())

        // TODO perform file collision check with already translated files in the srcGenDir
        if (getFilenameCollisionCheck()) {
            Utils.filenameCollisionCheck(getSrcFiles())
        }

        String classPathArg = Utils.getClassPathArg(
                project, getJ2objcHome(), getTranslateClassPaths(), getTranslateJ2objcLibs())

        classPathArg += ":${project.buildDir}/classes"

        ByteArrayOutputStream output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable j2objcExecutable
                windowsOnlyArgs.each { String windowsArg ->
                    args windowsOnlyArg
                }

                // Arguments
                args "-d", srcGenDir
                args "-sourcepath", sourcepath
                if (classPathArg.size() > 0) {
                    args "-classpath", classPathArg
                }
                translateArgs.each { String translateArg ->
                    args translateArg
                }

                // File Inputs
                srcFilesChanged.each { File file ->
                    args file.path
                }

                standardOutput output
                errorOutput output
            }

        } catch (ExecException exception) {
            String outputStr = output.toString()
            logger.debug 'Translation output:'
            logger.debug outputStr
            // Put to stderr only the lines at fault.
            // We do not separate standardOutput and errorOutput in the exec
            // task, because the interleaved output is helpful for context.
            logger.error 'Error during translation:'
            logger.error Utils.filterJ2objcOutputForErrorLines(outputStr)
            // Gradle will helpfully tell the user to use --debug for more
            // output when the build fails.
            throw exception
        }

        logger.debug 'Translation output:'
        logger.debug output.toString()
    }
}
