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
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern

/**
 *
 */
class J2objcTestTask extends DefaultTask {

    // *Test.java files and TestRunner binary
    @InputFile
    File testBinaryFile

    @InputFiles
    FileCollection getSrcFiles() {
        // Note that neither testPattern nor translatePattern need  to be @Input methods because they are solely
        // inputs to this method, which is already an input via @InputFiles.
        def allFiles = J2objcUtils.srcDirs(project, 'test', 'java')

        if (project.j2objcConfig.translatePattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.translatePattern)
        }
        if (project.j2objcConfig.testPattern != null) {
            allFiles = allFiles.matching(project.j2objcConfig.testPattern)
        }
        return allFiles
    }

    // Report of test failures
    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    // j2objcConfig dependencies for UP-TO-DATE checks
    @Input
    String getTestFlags() { return project.j2objcConfig.testFlags }

    @Input
    String getTranslateFlags() { return project.j2objcConfig.translateFlags }

    @Input
    int getTestMinExpectedTests() { return project.j2objcConfig.testMinExpectedTests }

    @Input
    boolean getTestSkip() { return project.j2objcConfig.testSkip }


    @TaskAction
    def test() {

    	if (getTestSkip()) {
        	logger.debug "Skipping j2objcTest"
        	return
    	}

        def binary = testBinaryFile.path
        logger.debug "Test Binary: $binary"

        // list of test names: ['com.example.dir.ClassOneTest', 'com.example.dir.ClassTwoTest']
        // depends on "--prefixes dir/prefixes.properties" in translateFlags
        def testNames = testNames(project, getSrcFiles(), getTranslateFlags())

        def output = new ByteArrayOutputStream()
        try {
            project.exec {
                executable binary
                args "org.junit.runner.JUnitCore"

                args getTestFlags().split()

                testNames.each { testName ->
                    args testName
                }

                errorOutput output
                standardOutput output
            }
        } catch (Exception exception) {
            logger.error "STDOUT and STDERR from failed j2objcTest task:"
            logger.error output.toString()
            def message =
                    "The j2objcTest task failed. Given that the java plugin 'test' task\n" +
                    "completed successfully, this shows an error specific to the j2objc build.\n" +
                    "It may be that the code will still perform correctly. If you can identify\n" +
                    "the failing test, then it can be excluded by modifying build.gradle:\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    testPattern {\n" +
                    "        exclude '**/FailingTest.java'\n" +
                    "        exclude 'src/main/java/Package/FailingDirectory/**'\n" +
                    "    }\n" +
                    "}\n" +
                    "\n" +
                    "To identify the failing test, run with the --debug flag and look for:\n" +
                    "    testJ2objc org.junit.runner.JUnitCore\n" +
                    "Copy the command from \"Command:\" onwards, then try varying the command\n" +
                    "to drop tests and figure out which ones are causing the failures.\n"
                    "Then disable them as described above.\n"
            if (exception.getMessage().find("finished with non-zero exit value 139")) {
                message +=
                        "\n" +
                        "\"non-zero exit value 139\" indicates a process crash, most likely\n" +
                        "caused by a segmentation fault (SIGSEGV) in user space.\n" +
                        "\n" +
                        "Look at the known crash issues to see what may be causing this:\n" +
                        "    https://github.com/google/j2objc/issues?q=is%3Aissue+crash+is%3Aopen+\n"
            }
            logger.error message
            throw exception
        }

        // Test Output Report
        def outputStr = output.toString()
        reportFile.write(outputStr)
        logger.debug "Test Output: ${reportFile.path}"

        int testCount = J2objcUtils.matchNumberRegex(outputStr, /OK \((\d+) tests\)/)
        def message =
                "\n" +
                "j2objcConfig {\n" +
                "    testMinExpectedTests ${testCount}\n" +
                "}\n"
        if (getTestMinExpectedTests() == 0) {
            logger.warning "Min Test check disabled due to: 'testMinExpectedTests 0'"
        } else if (testCount < getTestMinExpectedTests()) {
            if (testCount == 0) {
                message =
                        "No unit tests were run. Unit tests are strongly encouraged with J2ObjC.\n" +
                        "J2ObjC build of project '${project.name}'\n" +
                        "\n" +
                        "To disable this check (against best practice), modify build.gradle:\n" +
                        message
            } else {
                message =
                        "Number of unit tests run is less than expected:\n" +
                        "J2ObjC build of project '${project.name}'\n" +
                        "Actual Tests Run:    ${testCount}\n" +
                        "Expected Tests Run:  ${getTestMinExpectedTests()}\n" +
                        "\n" +
                        "If there are legitimately fewer tests, then modify build.gradle:\n" +
                        message
            }
            throw new Exception(message)
        } else if (testCount != getTestMinExpectedTests()) {
            assert getTestMinExpectedTests() > 0
            assert getTestMinExpectedTests() < testCount
            message =
                    "testMinExpectedTests can be increased to guard against tests\n" +
                    "being accidentally missed in the future by modifying build.gradle\n" +
                    "J2ObjC build of project '${project.name}'\n" +
                    message
            logger.debug message
        }
    }


    // Generate Test Names
    // Generate list of tests from the source java files
    // e.g. src/test/java/com/example/dir/ClassTest.java => "com.example.dir.ClassTest"
    // depends on --prefixes dir/prefixes.properties in translateFlags
    def static testNames(Project proj, FileCollection srcFiles, String translateFlags) {
        def prefixesProperties = J2objcUtils.prefixProperties(proj, translateFlags)

        def testNames = srcFiles.collect { file ->
            // src/test/java/com/example/dir/SomeTest.java => com.example.dir.SomeTest
            def testName = proj.relativePath(file)
                    .replace('src/test/java/', '')
                    .replace('/', '.')
                    .replace('.java', '')

            // Translate test name according to prefixes.properties
            // E.g. com.example.dir.SomeTest => PREFIX.SomeTest
            def namespaceRegex = /^(([^.]+\.)+)[^.]+$/  // No match for test outside a namespace
            def matcher = (testName =~ namespaceRegex)
            if (matcher.find()) {
                def namespace = matcher[0][1]            // com.example.dir.
                def namespaceChopped = namespace[0..-2]  // com.example.dir
                if (prefixesProperties.containsKey(namespaceChopped)) {
                    def value = prefixesProperties.getProperty(namespaceChopped)
                    testName = testName.replace(namespace, value)
                }
            }
            // com.example.dir.SomeTest
            return testName
        }
        return testNames
    }
}
