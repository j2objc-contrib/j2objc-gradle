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
import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Test task to run all unit tests and verify results.
 */
@CompileStatic
class TestTask extends DefaultTask {

    // *Test.java files and TestRunner binary
    @InputFile
    File testBinaryFile

    // 'debug' or 'release'
    @Input
    String buildType

    @InputFiles
    FileTree getTestSrcFiles() {
        // Note that neither testPattern nor translatePattern need to be @Input methods because they are solely
        // inputs to this method, which is already an input via @InputFiles.
        FileTree allFiles = Utils.srcSet(project, 'test', 'java')
        J2objcConfig config = J2objcConfig.from(project)
        if (config.translatePattern != null) {
            allFiles = allFiles.matching(config.translatePattern)
        }
        if (config.testPattern != null) {
            allFiles = allFiles.matching(config.testPattern)
        }
        return allFiles
    }

    @Input
    List<String> getTestArgs() { return J2objcConfig.from(project).testArgs }

    @Input
    List<String> getTranslateArgs() { return J2objcConfig.from(project).translateArgs }

    @Input
    int getTestMinExpectedTests() { return J2objcConfig.from(project).testMinExpectedTests }

    // As tests can depend on resources
    // TODO: switch to calling `inputs.dir(source set srcDirs)`
    @InputFiles
    FileTree getMainResourcesFiles() {
        FileTree allResources = Utils.srcSet(project, 'main', 'resources')
        allResources = allResources.plus(Utils.srcSet(project, 'test', 'resources'))
        return allResources
    }


    // Output required for task up-to-date checks
    @OutputFile
    File reportFile = project.file("${project.buildDir}/reports/${name}.out")

    @OutputDirectory
    // Combines main/test resources and test executables
    File getJ2objcTestDirFile() {
        assert buildType in ['debug', 'release']
        return new File(project.buildDir, "j2objcTest/$buildType")
    }


    @TaskAction
    void test() {
        Utils.requireMacOSX('j2objcTest task')

        // list of test names: ['com.example.dir.ClassOneTest', 'com.example.dir.ClassTwoTest']
        // depends on "--prefixes dir/prefixes.properties" in translateArgs
        Properties packagePrefixes = Utils.packagePrefixes(project, translateArgs)
        List<String> testNames = getTestNames(project, getTestSrcFiles(), packagePrefixes)

        // Test executable must be run from the same directory as the resources
        Utils.syncResourcesTo(project, ['main', 'test'], getJ2objcTestDirFile())
        Utils.projectCopy(project, {
            from testBinaryFile
            into getJ2objcTestDirFile()
        })

        File copiedTestBinary = new File(getJ2objcTestDirFile(), testBinaryFile.getName())
        logger.debug("Test Binary: $copiedTestBinary")

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // NOTE: last 's' is optional for the case of "OK (1 test)"
        // Capturing group is the test count, i.e. '\d+'
        String testCountRegex = /OK \((\d+) tests?\)/

        try {
            Utils.projectExec(project, stdout, stderr, testCountRegex, {
                executable copiedTestBinary
                args 'org.junit.runner.JUnitCore'

                getTestArgs().each { String testArg ->
                    args testArg
                }

                testNames.each { String testName ->
                    args testName
                }

                setStandardOutput stdout
                setErrorOutput stderr
            })

        } catch (Exception exception) {
            String message =
                    "The j2objcTest task failed. Given that the java plugin 'test' task\n" +
                    "completed successfully, this is an error specific to the J2ObjC Gradle\n" +
                    "Plugin build.\n" +
                    "\n" +
                    "1) Check BOTH 'Standard Output' and 'Error Output' above for problems.\n" +
                    "\n" +
                    "2) It could be that only the tests are failing while the non-test code\n" +
                    "may run correctly. If you can identify the failing test, then can try\n" +
                    "marking it to be ignored.\n" +
                    "\n" +
                    "To identify the failing test, look for 'Command Line failed' above.\n" +
                    "Copy and then run it in your shell. Selectively remove the test cases\n" +
                    "until you identify the failing test.\n" +
                    "\n" +
                    "Then the failing test can be filtered out using build.gradle:\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    testPattern {\n" +
                    "        exclude '**/FailingTest.java'\n" +
                    "        exclude 'src/main/java/Package/FailingDirectory/**'\n" +
                    "    }\n" +
                    "}\n" +
                    "\n" +
                    "Look at known J2ObjcC crash issues for further insights:\n" +
                    "    https://github.com/google/j2objc/issues?q=is%3Aissue+is%3Aopen+crash\n"

            // Copy message afterwards to make it more visible as exception text may be long
            message = exception.toString() + '\n' + message
            throw new InvalidUserDataException(message, exception)
        }

        // Only write output if task is successful
        reportFile.write(Utils.stdOutAndErrToLogString(stdout, stderr))
        logger.error("Test Output: ${reportFile.path}")

        String testCountStr = Utils.matchRegexOutputs(stdout, stderr, testCountRegex)
        if (!testCountStr?.isInteger()) {
            // Should have been caught in projectExec call above
            throw new InvalidUserDataException(
                    Utils.stdOutAndErrToLogString(stdout, stderr) + '\n' +
                    'Tests passed but could not find test count.\n' +
                    'Failed Regex Match testCountRegex: ' +
                    Utils.escapeSlashyString(testCountRegex) + '\n' +
                    'Found: ' + testCountStr)
        }
        int testCount = testCountStr.toInteger()

        String message =
                "\n" +
                "j2objcConfig {\n" +
                "    testMinExpectedTests ${testCount}\n" +
                "}\n"
        if (getTestMinExpectedTests() == 0) {
            logger.warn("Min Test check disabled due to: 'testMinExpectedTests 0'")
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
            throw new InvalidUserDataException(message)
        } else if (testCount != getTestMinExpectedTests()) {
            assert getTestMinExpectedTests() > 0
            assert getTestMinExpectedTests() < testCount
            message =
                    "testMinExpectedTests can be increased to guard against tests\n" +
                    "being accidentally missed in the future by modifying build.gradle\n" +
                    "J2ObjC build of project '${project.name}'\n" +
                    message
            logger.debug(message)
        }
    }

    // Generate list of test names from the source java files
    // depends on --prefixes dir/prefixes.properties in translateArgs
    //   Before:  src/test/java/com/example/dir/SomeTest.java
    //   After:   com.example.dir.SomeTest or PREFIXSomeTest
    // TODO: Complexity is O(testCount * prefixCount), make more efficient if needed
    @VisibleForTesting
    static List<String> getTestNames(Project proj, FileCollection srcFiles, Properties packagePrefixes) {
        List<String> testNames = srcFiles.collect { File file ->
            // Back off to a fragile method that makes assumptions about source directories
            // if that didn't work.
            // Comments indicate the value at the end of that statement
            String testName = proj.relativePath(file)  // src/test/java/com/example/dir/SomeTest.java
                            .replace('\\', '/')  // Windows backslashes converted to forward slash
                            .replace('src/test/java/', '')  // com/example/dir/SomeTest.java
                            .replace('.java', '')  // com/example/dir/SomeTest
                            .replace('/', '.')  // com.example.dir.SomeTest

            // Translate test name according to prefixes.properties
            // Prefix Property: com.example.dir: PREFIX
            // Test Name: com.example.dir.SomeTest => PREFIXSomeTest

            // First match against the set of Java packages, excluding the filename
            Matcher packageMatcher = (testName =~ /^(.+)\.([^.]+)$/)  // (com.example.dir.)SomeTest
            if (packageMatcher.find()) {
                String clazz = packageMatcher.group(packageMatcher.groupCount())  // SomeTest
                String namespace = packageMatcher.group(1)  // com.example.dir

                for (Map.Entry<Object, Object> property in packagePrefixes.entrySet()) {
                    String keyStr = property.key as String
                    String valStr = property.value as String
                    assert null != keyStr
                    assert null != valStr

                    String keyRegex = wildcardToRegex(keyStr)
                    Matcher keyMatcher = (namespace =~ keyRegex)
                    if (keyMatcher.find()) {
                        testName = valStr + clazz  // PrefixSomeTest
                        break
                    }
                }
            }
            return testName  // com.example.dir.SomeTest or PrefixSomeTest
        }
        return testNames
    }

    @VisibleForTesting
    // Adapted from J2ObjC's PackagePrefixes.wildcardToRegex()
    // https://github.com/google/j2objc/blob/master/translator/src/main/java/com/google/devtools/j2objc/util/PackagePrefixes.java#L219
    static String wildcardToRegex(String s) {
        if (s.endsWith(".*")) {
            // Include root package in regex. For example, foo.bar.* needs to match
            // foo.bar, foo.bar.mumble, etc.
            String root = s.substring(0, s.length() - 2).replace(".",  "\\.");
            return String.format('^(%s|%s\\..*)$', root, root);
        }
        return String.format('^%s$', s.replace(".", "\\.").replace("\\*", ".*"));
    }
}
