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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 *
 */
class J2objcTestTask extends DefaultTask {

    // *Test.java files and TestRunner binary
    @InputFile
    File srcFile
    @InputFiles
    FileCollection srcFiles

    // Report of test failures
    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getTestFlags() { return project.j2objcConfig.testFlags }

    @Input
    String getTestExcludeRegex() { return project.j2objcConfig.testExcludeRegex }

    @Input
    String getTestIncludeRegex() { return project.j2objcConfig.testIncludeRegex }

    @Input
    String getTranslateExcludeRegex() { return project.j2objcConfig.translateExcludeRegex }

    @Input
    String getTranslateFlags() { return project.j2objcConfig.translateFlags }

    @Input
    String getTranslateIncludeRegex() { return project.j2objcConfig.translateIncludeRegex }

    @Input
    boolean getTestExecutedCheck() { return project.j2objcConfig.testExecutedCheck }

    @Input
    boolean getTestSkip() { return project.j2objcConfig.testSkip }


    @TaskAction
    def test() {

        if (getTestSkip()) {
            logger.debug "Skipping j2objcTest"
            return
        }

        // Generate list of tests from the source java files
        // src/test/java/com/example/dir/ClassTest.java => "com.example.dir.ClassTest"

        // Already filtered by ".*Test.java" before it arrives here
        srcFiles = J2objcUtils.fileFilter(srcFiles,
                getTranslateIncludeRegex(),
                getTranslateExcludeRegex())
        srcFiles = J2objcUtils.fileFilter(srcFiles,
                getTestIncludeRegex(),
                getTestExcludeRegex())

        // Generate Test Names
        def prefixesProperties = J2objcUtils.prefixProperties(project, getTranslateFlags())
        def testNames = srcFiles.collect { file ->
            def testName = project.relativePath(file)
                    .replace('src/test/java/', '')
                    .replace('/', '.')
                    .replace('.java', '')
            // src/test/java/com/example/dir/SomeTest.java => com.example.dir.SomeTest

            // Translate test name according to prefixes.properties
            // E.g. com.example.dir.SomeTest => PREFIX.SomeTest
            def namespaceRegex = /^(([^.]+\.)+)[^.]+$/  // No match for test outside a package
            def matcher = (testName =~ namespaceRegex)
            if (matcher.find()) {
                def namespace = matcher[0][1]            // com.example.dir.
                def namespaceChopped = namespace[0..-2]  // com.example.dir
                if (prefixesProperties.containsKey(namespaceChopped)) {
                    def value = prefixesProperties.getProperty(namespaceChopped)
                    testName = testName.replace(namespace, value)
                }
            }
            return testName
        }

        def binary = srcFile.path
        logger.debug "Test Binary: " + srcFile.path

        def outputStream = new ByteArrayOutputStream()
        project.exec {
            executable binary
            args "org.junit.runner.JUnitCore"

            args getTestFlags().split()

            testNames.each { testName ->
                args testName
            }

            errorOutput outputStream
            standardOutput outputStream
        }

        def output = outputStream.toString()
        reportFile.write(output)
        logger.debug "Test Output: ${reportFile.path}"

        // 0 tests => warn by default
        if (getTestExecutedCheck()) {
            if (output.contains("OK (0 tests)")) {
                def message =
                        "Zero unit tests were run. Tests are strongly encouraged with J2objc:\n" +
                        "\n" +
                        "To disable this check (which is against best practice):\n" +
                        "j2objcConfig {\n" +
                        "    testExecutedCheck false\n" +
                        "}\n"
                throw new InvalidUserDataException(message)
            }
        }
    }
}
