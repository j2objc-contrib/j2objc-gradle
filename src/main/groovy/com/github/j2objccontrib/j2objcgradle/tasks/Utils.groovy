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

import groovy.util.logging.Slf4j
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import java.util.regex.Matcher

/**
 * Internal utilities supporting plugin implementation.
 */
// Without access to the project, logging is performed using the
// static 'log' variable added during decoration with this annotation.
@Slf4j
class Utils {
    // TODO: ideally bundle j2objc binaries with plugin jar and load at runtime with
    // TODO: ClassLoader.getResourceAsStream(), extract, chmod and then execute

    static boolean isWindows() {
        return System.getProperty('os.name').toLowerCase().contains('windows')
    }

    static void throwIfNoJavaPlugin(Project proj) {
        if (!proj.plugins.hasPlugin('java')) {
            String message =
                    "j2objc plugin didn't find the 'java' plugin in the '${proj.name}' project.\n" +
                    "This is a requirement for using j2objc. Please see usage information at:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/#usage"
            throw new InvalidUserDataException(message)
        }
    }

    // MUST be used only in @Input getJ2objcHome() methods to ensure UP-TO-DATE checks are correct
    // @Input getJ2objcHome() method can be used freely inside the task action
    static String j2objcHome(Project proj) {
        File localPropertiesFile = new File(proj.rootDir, 'local.properties')
        String result = null
        if (localPropertiesFile.exists()) {
            Properties localProperties = new Properties();
            localPropertiesFile.withInputStream {
                localProperties.load it
            }
            result = localProperties.getProperty('j2objc.home')
        }
        if (result == null) {
            result = System.getenv('J2OBJC_HOME')
        }
        if (result == null) {
            String message =
                    "j2objc home not set, this should be configured either:\n" +
                    "1) in a 'local.properties' file in the project root directory as:\n" +
                    "   j2objc.home=/PATH/TO/J2OBJC/DISTRIBUTION\n" +
                    "2) as the J2OBJC_HOME system environment variable\n" +
                    "\n" +
                    "If both are configured the value in the properties file will be used.\n" +
                    "\n" +
                    "It must be the path of the unzipped j2objc distribution. Download releases here:\n" +
                    "https://github.com/google/j2objc/releases"
            throw new InvalidUserDataException(message)
        }
        if (!proj.file(result).exists()) {
            String message = "j2objc directory not found, expected location: ${result}"
            throw new InvalidUserDataException(message)
        }
        return result
    }

    // Reads properties file and arguments from translateArgs (last argument takes precedence)
    //   --prefixes dir/prefixes.properties --prefix com.ex.dir=Short --prefix com.ex.dir2=Short2
    // TODO: separate this out to a distinct argument that's added to translateArgs
    // TODO: @InputFile conversion for this
    static Properties packagePrefixes(Project proj, List<String> translateArgs) {
        Properties props = new Properties()
        String joinedTranslateArgs = translateArgs.join(' ')
        Matcher matcher = (joinedTranslateArgs =~ /--prefix(|es)\s+(\S+)/)
        int start = 0
        while (matcher.find(start)) {
            start = matcher.end()
            Properties newProps = new Properties()
            String argValue = matcher.group(2)
            if (matcher.group(1) == "es") {
                // --prefixes prefixes.properties
                // trailing space confuses FileInputStream
                String prefixesPath = argValue.trim()
                log.debug "Loading prefixesPath: $prefixesPath"
                newProps.load(new FileInputStream(proj.file(prefixesPath).path))
            } else {
                // --prefix com.example.dir=CED
                newProps.load(new StringReader(argValue.trim()));
            }
            props.putAll(newProps)
        }

        log.debug 'Package Prefixes: http://j2objc.org/docs/Package-Prefixes.html'
        for (key in props.keys()) {
            log.debug "Package Prefix Property: $key : ${props.getProperty((String) key)}"
        }

        return props
    }

    /*
     * Throws exception if two filenames match, even if in distinct directories.
     * This is important for referencing files with Xcode.
     */
    static void filenameCollisionCheck(FileCollection files) {
        HashMap<String, File> nameMap = [:]
        for (file in files) {
            log.debug "CollisionCheck: ${file.name}, ${file.absolutePath}"
            if (nameMap.containsKey(file.name)) {
                File prevFile = nameMap.get(file.name)
                String message =
                        "File name collision detected:\n" +
                        "  ${prevFile.path}\n" +
                        "  ${file.path}\n" +
                        "\n" +
                        "To disable this check (which may overwrite files), modify build.gradle:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    filenameCollisionCheck false\n" +
                        "}\n"
                throw new InvalidUserDataException(message)
            }
            nameMap.put(file.name, file)
        }
    }

    // Retrieves the configured source directories from the Java plugin SourceSets.
    // This includes the files for all Java source within these directories.
    static SourceDirectorySet srcSet(Project proj, String sourceSetName, String fileType) {
        throwIfNoJavaPlugin(proj)

        assert fileType == 'java' || fileType == 'resources'
        assert sourceSetName == 'main' || sourceSetName == 'test'
        SourceSet sourceSet = proj.sourceSets.findByName(sourceSetName)
        // For standard fileTypes 'java' and 'resources,' per contract this cannot be null.
        SourceDirectorySet srcDirSet = sourceSet.getProperty(fileType)
        return srcDirSet
    }

    // Add list of java path to a FileCollection as a FileTree
    static FileCollection javaTrees(Project proj, List<String> treePaths) {
        FileCollection files = proj.files()
        treePaths.each { String treePath ->
            log.debug "javaTree: $treePath"
            files = files.plus(
                    proj.files(proj.fileTree(dir: treePath, includes: ["**/*.java"])))
        }
        return files
    }

    static List<String> j2objcLibs(String j2objcHome,
                                   List<String> libraries) {
        return libraries.collect { String library ->
            return "$j2objcHome/lib/$library"
        }
    }

    // Convert FileCollection to joined path arg, e.g. "src/Some.java:src/Another.java"
    static String joinedPathArg(FileCollection files) {
        String[] paths = []
        files.each { File file ->
            paths += file.path
        }
        return paths.join(':')
    }

    static String filterJ2objcOutputForErrorLines(String processOutput) {
        return processOutput.tokenize('\n').grep(~/^(.*: )?error:.*/).join('\n')
    }

    // Matches regex within 'str', extracts first match and then returns as int
    static int matchNumberRegex(String str, String regex) {
        Matcher matcher = (str =~ regex)
        if (!matcher.find()) {
            throw new InvalidUserDataException(
                    "Content:\n" +
                    "$str\n" +
                    "Regex couldn't match number in output: $regex")
        } else {
            String value = matcher[0][1]
            if (!value.isInteger()) {
                throw new InvalidUserDataException(
                        "Content:\n" +
                        "$str\n" +
                        "Regex didn't find number in output: $regex, value: $value")
            }
            return value.toInteger()
        }
    }
}
