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

package com.github.brunobowden.j2objcgradle.tasks

import groovy.transform.PackageScope
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

/**
 * Internal utilities supporting plugin implementation.
 */
@PackageScope
class J2objcUtils {
    // TODO: ideally bundle j2objc binaries with plugin jar and load at runtime with
    // TODO: ClassLoader.getResourceAsStream(), extract, chmod and then execute

    static isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows")
    }

    // Retrieves the configured source directories per specification indicated
    // in documentation of 'custom.*SrcDirs'.
    private final static def DEFAULT_SOURCE_DIRECTORIES =
            [main: [java: ['src/main/java'], resources: ['src/main/resources']],
             test: [java: ['src/test/java'], resources: ['src/test/resources']]]

    static def srcDirs(Project proj, String sourceSetName, String fileType) {
        assert fileType == 'java' || fileType == 'resources'
        assert sourceSetName == 'main' || sourceSetName == 'test'
        // Note that subprojects can also be passed to this method; such projects
        // may not have j2objcConfig.
        if (proj.hasProperty('j2objcConfig')) {
            String custom = null
            if (fileType == 'java') {
                custom = sourceSetName == 'test' ?
                         proj.j2objcConfig.customTestSrcDirs :
                         proj.j2objcConfig.customMainSrcDirs
            } else if (fileType == 'resources') {
                custom = sourceSetName == 'test' ?
                         proj.j2objcConfig.customTestResourcesSrcDirs :
                         proj.j2objcConfig.customMainResourcesSrcDirs
            }
            // We intentionally check for nulls only.  It is valid to desire
            // an empty list of (i.e. no) source directories.
            if (custom != null) {
                return custom.collect({ proj.file(it) })
            }
        }
        if (proj.hasProperty('sourceSets')) {
            def sourceSet = proj.sourceSets.findByName(sourceSetName)
            if (sourceSet != null) {
                // For standard fileTypes 'java' and 'resources,' per contract
                // this cannot be null.
                return sourceSet.getProperty(fileType).srcDirs
            }
        }
        return DEFAULT_SOURCE_DIRECTORIES[sourceSetName][fileType].collect({ proj.file(it) })
    }

    static def sourcepathJava(Project proj) {
        def javaRoots = []
        srcDirs(proj, 'main', 'java').each {
            javaRoots += it.path
        }
        srcDirs(proj, 'test', 'java').each {
            javaRoots += it.path
        }
        return javaRoots.join(':')
    }

    // MUST be used only in @Input getJ2ObjCHome() methods to ensure UP-TO-DATE checks are correct
    // @Input getJ2ObjCHome() method can be used freely inside the task action
    static def j2objcHome(Project proj) {
        def result = proj.j2objcConfig.j2objcHome
        if (result == null) {
            def message =
                    "j2objcHome not set, this should be configured in the parent gradle file with " +
                    "this syntax:\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    j2objcHome null // e.g. \"\${projectDir}/../j2objc\"\n" +
                    "}\n" +
                    "\n" +
                    "It must be the path of the unzipped j2objc release. Download releases here:\n" +
                    "https://github.com/google/j2objc/releases"
            throw new InvalidUserDataException(message)
        }
        if (!proj.file(result).exists()) {
            def message = "j2objc directory not found, expected location: ${result}"
            throw new InvalidUserDataException(message)
        }
        return result
    }

    // Filters a FileCollection by path:
    // must match includeRegex and NOT match excludeRegex, regex ignored if null
    static def fileFilter(FileCollection files, String includeRegex, String excludeRegex) {
        return files.filter { file ->
            return file.path.matches(includeRegex)
        }.filter { file ->
            return !file.path.matches(excludeRegex)
        }
    }

    // Reads properties file and flags from translateFlags (last flag takes precedence)
    //   --prefixes dir/prefixes.properties --prefix com.ex.dir=Short --prefix com.ex.dir2=Short2
    // TODO: separate this out to a distinct flag that's added to translateFlags
    // TODO: @InputFile conversion for this
    static def prefixProperties(Project proj, String translateFlags) {
        Properties props = new Properties()
        def matcher = (translateFlags =~ /--prefix(|es)\s+(\S+)/)
        def start = 0
        while (matcher.find(start)) {
            start = matcher.end()
            def newProps = new Properties()
            def argValue = matcher.group(2)
            if (matcher.group(1) == "es") {
                // --prefixes prefixes.properties
                // trailing space confuses FileInputStream
                def prefixesPath = argValue.trim()
                newProps.load(new FileInputStream(proj.file(prefixesPath).path))
            } else {
                // --prefix com.example.dir=CED
                newProps.load(new StringReader(argValue.trim()));
            }
            props.putAll(newProps)
        }
//        for (key in props.keys()) {
//            logger.debug key + ": " + props.getProperty(key)
//        }

        return props
    }

    static def filenameCollisionCheck(FileCollection files) {
        def nameMap = [:]
        for (file in files) {
            if (nameMap.containsKey(file.name)) {
                def prevFile = nameMap.get(file.name)
                def message =
                        "File name collision detected:\n" +
                        "  " + prevFile.path + "\n" +
                        "  " + file.path + "\n" +
                        "\n" +
                        "To disable this check (which may overwrite output files):\n" +
                        "j2objcConfig {\n" +
                        "    filenameCollisionCheck false\n" +
                        "}\n"
                throw new InvalidUserDataException(message)
            }
            nameMap.put(file.name, file)
        }
    }

    // add Java files to a FileCollection
    static def addJavaFiles(Project proj, FileCollection files, List<String> generatedSourceDirs) {
        if (generatedSourceDirs.size() > 0) {
            generatedSourceDirs.each { sourceDir ->
                logger.debug "include generatedSourceDir: " + sourceDir
                def buildSrcFiles = proj.files(proj.fileTree(dir: sourceDir, includes: ["**/*.java"]))
                files += buildSrcFiles
            }
        }
        return files
    }

    static def absolutePathOrEmpty(Project proj, List<String> relativePaths) {
        if (relativePaths.size() > 0) {
            def tmpPaths = ""
            relativePaths.each { relativePath ->
                logger.debug "Added to Path: " + relativePath
                tmpPaths += ":${proj.file(relativePath).path}"
            }
            return tmpPaths
        } else {
            return ""
        }
    }

    // -classpath javac flag generation from set of libraries (includes j2objc default libraries)
    // TODO: @InputFiles for libraries and j2objcLibs
    static def getClassPathArg(Project proj,
                               String j2objcHome,
                               List<String> libraries,
                               List<String> translateJ2objcLibs) {
        def classPathList = []
        // user defined libraries
        libraries.each { library ->
            def libPath = "${proj.projectDir}/lib/" + library
            classPathList += libPath
        }
        // j2objc default libraries
        translateJ2objcLibs.each { library ->
            classPathList += j2objcHome + "/lib/" + library
        }
        return classPathList.join(':')
    }

    static def filterJ2objcOutputForErrorLines(String processOutput) {
        return processOutput.tokenize('\n').grep(~/^(.*: )?error:.*/).join('\n')
    }
}
