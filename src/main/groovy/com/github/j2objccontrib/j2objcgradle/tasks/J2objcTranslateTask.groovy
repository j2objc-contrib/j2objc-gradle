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
import org.gradle.api.artifacts.ProjectDependency
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

    // Java source files
    @InputFiles
    FileCollection srcFiles

    // Generated ObjC files
    @OutputDirectory @Optional
    File destDir

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getJ2ObjCHome() { return J2objcUtils.j2objcHome(project) }

    @Input
    String getTranslateFlags() { return project.j2objcConfig.translateFlags }

    @Input
    String getTranslateExcludeRegex() { return project.j2objcConfig.translateExcludeRegex }

    @Input
    String getTranslateIncludeRegex() { return project.j2objcConfig.translateIncludeRegex }

    @Input @Optional
    String getTranslateSourcepaths() { return project.j2objcConfig.translateSourcepaths }

    @Input
    boolean getAppendProjectDependenciesToSourcepath() {
        return project.j2objcConfig.appendProjectDependenciesToSourcepath
    }

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
        logger.debug "Source files: " + srcFiles.getFiles().size()
        FileCollection srcFilesChanged = project.files()
        inputs.outOfDate { change ->
            logger.debug "New or Updated file: " + change.file
            srcFilesChanged += project.files(change.file)
        }
        def removedFileNames = []
        inputs.removed { change ->
            logger.debug "Removed file: " + change.file.name
            def nameWithoutExt = file.name.toString().replaceFirst("\\..*", "")
            removedFileNames += nameWithoutExt
        }
        logger.debug "Removed files: " + removedFileNames.size()

        logger.debug "New or Updated files: " + srcFilesChanged.getFiles().size()
        FileCollection translatedSrcFiles = srcFiles - srcFilesChanged
        logger.debug "Unchanged files: " + translatedSrcFiles.getFiles().size()

        def translatedFiles = 0
        if (destDir.exists()) {
            FileCollection destFiles = project.files(project.fileTree(
                    dir: destDir, includes: ["**/*.h", "**/*.m"]))

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

        // set the srcFiles to the files which need to be translated
        srcFiles = srcFilesChanged


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

        // Project Dependencies
        if (getAppendProjectDependenciesToSourcepath()) {
            def depSourcePaths = []
            project.configurations.compile.allDependencies.each { dep ->
                if (dep instanceof ProjectDependency) {
                    def depProj = ((ProjectDependency) dep).getDependencyProject()
                    depSourcePaths += J2objcUtils.srcDirs(depProj, 'main', 'java')
                }
            }
            sourcepath += ':' + depSourcePaths.join(':')
        }

        srcFiles = J2objcUtils.fileFilter(srcFiles,
                getTranslateIncludeRegex(),
                getTranslateExcludeRegex())

        // TODO perform file collision check with already translated files in the destDir
        if (getFilenameCollisionCheck()) {
            J2objcUtils.filenameCollisionCheck(srcFiles)
        }

        def classPathArg = J2objcUtils.getClassPathArg(
                project, getJ2ObjCHome(), getTranslateClassPaths(), getTranslateJ2objcLibs())

        classPathArg += ":${project.buildDir}/classes"

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
        def translateFlags = project.j2objcConfig.translateFlags
        if (translatedFiles > 0 && project.j2objcConfig.UNSAFE_incrementalBuildClosure) {
            translateFlags = translateFlags.toString().replaceFirst("--build-closure", "").trim()
        }

        def output = new ByteArrayOutputStream()

        try {
            project.exec {
                executable j2objcExec

                args windowsOnlyArgs.split()
                args "-d", destDir
                args "-sourcepath", sourcepath

                if (classPathArg.size() > 0) {
                    args "-classpath", classPathArg
                }

                args translateFlags.split()

                srcFiles.each { file ->
                    args file.path
                }
                standardOutput output
                errorOutput output
            }

        } catch (e) {
            // Warn and explain typical case of Android application trying to translate
            // what can't be translated. Suggest instead excluding incompatible files.
            // Then finally rethrow the existing error.
            if (!project.plugins.findPlugin('java')) {
                if (getTranslateExcludeRegex() == "^\$" &&
                    getTranslateIncludeRegex() == "^.*\$") {

                    def message =
                            "\n" +
                            "J2objc by design will not translate your entire Android application.\n" +
                            "It focuses on the business logic, the UI should use native code:\n" +
                            "  https://github.com/google/j2objc/blob/master/README.md\n" +
                            "\n" +
                            "The best practice over time is to separate out the shared code to a\n" +
                            "distinct java project with NO android dependencies.\n" +
                            "\n" +
                            "As a step towards that, you can configure the j2objc plugin to only\n" +
                            "translate a subset of files that don't depend on Android. The settings\n" +
                            "are regular expressions on the file path. For example:\n" +
                            "\n" +
                            "j2objcConfig {\n" +
                            "    translateIncludeRegex \".*/src/main/java/com/example/TranslateThisDirectoryOnly/.*\n" +
                            "    translateExcludeRegex \".*/(SkipThisClass|AlsoSkipThisClass)\\.java\"\n" +
                            "}\n"

                    logger.warn message
                }
            }
            def processOutput = output.toString()
            logger.debug 'Translation output:'
            logger.debug processOutput
            // Put to stderr only the lines at fault.
            // We do not separate standardOutput and errorOutput in the exec
            // task, because the interleaved output is helpful for context.
            logger.error 'Error during translation:'
            logger.error J2objcUtils.filterJ2objcOutputForErrorLines(processOutput)
            // Gradle will helpfully tell the user to use --debug for more
            // output when the build fails.
            throw e
        }
        logger.debug 'Translation output:'
        logger.debug output.toString()
    }
}
