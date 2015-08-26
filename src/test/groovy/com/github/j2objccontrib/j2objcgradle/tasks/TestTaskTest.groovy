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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * TestTask tests.
 */
class TestTaskTest {

    // Configured with setupTask()
    private Project proj
    private String j2objcHome
    private J2objcConfig j2objcConfig
    private TestTask j2objcTest

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    void setUp() {
        // Mac OS X is the only OS that can run this task
        Utils.setFakeOSMacOSX()
    }

    @Test
    void testGetTestNames_Simple() {

        // These are nonsense paths for files that don't exist
        proj = ProjectBuilder.builder().build()
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/SubdirClass.java",
                "${proj.rootDir}/src/test/java/com/example/other/OtherClass.java"])
        Properties noPackagePrefixes = new Properties()

        List<String> testNames = TestTask.getTestNames(proj, srcFiles, noPackagePrefixes)

        List<String> expectedTestNames = [
                "com.example.parent.ParentClass",
                "com.example.parent.subdir.SubdirClass",
                "com.example.other.OtherClass"]

        assert expectedTestNames == testNames
    }

    @Test
    void testGetTestNames_PackagePrefixes() {
        Properties packagePrefixes = new Properties()
        packagePrefixes.setProperty('com.example.parent', 'PrntPrefix')
        packagePrefixes.setProperty('com.example.parent.subdir', 'SubPrefix')
        packagePrefixes.setProperty('com.example.other', 'OthPrefix')

        // These are nonsense paths for files that don't exist
        proj = ProjectBuilder.builder().build()
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentOneClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/ParentTwoClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/SubdirClass.java",
                "${proj.rootDir}/src/test/java/com/example/other/OtherClass.java",
                "${proj.rootDir}/src/test/java/com/example/noprefix/NoPrefixClass.java"])

        List<String> testNames = TestTask.getTestNames(proj, srcFiles, packagePrefixes)

        List<String> expectedTestNames = [
                "PrntPrefixParentOneClass",
                "PrntPrefixParentTwoClass",
                "SubPrefixSubdirClass",
                "OthPrefixOtherClass",
                // No package prefix in this case
                "com.example.noprefix.NoPrefixClass"]

        assert expectedTestNames == testNames
    }

    private void setupTask() {
        (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createJ2objcConfig: true,
                createReportsDir: true,
        ))

        j2objcTest = (TestTask) proj.tasks.create(name: 'j2objcTest', type: TestTask) {
            testBinaryFile = proj.file(proj.file('build/binaries/testJ2objcExecutable/debug/testJ2objc'))
            buildType = 'debug'
        }
    }

    @Test
    void testTaskAction_Windows() {
        Utils.setFakeOSWindows()
        setupTask()

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Mac OS X is required for j2objcTest task')

        j2objcTest.test()
    }

    @Test
    // Name 'taskAction' as method name 'test' is ambiguous
    void testTaskAction_ZeroTestUnexpected() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)

        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (0 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        expectedException.expect(InvalidUserDataException.class)
        // Error:
        expectedException.expectMessage('Unit tests are strongly encouraged with J2ObjC')
        // Workaround:
        expectedException.expectMessage('testMinExpectedTests 0')

        j2objcTest.test()
    }

    @Test
    void testTaskAction_ZeroTestExpected() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        j2objcConfig.testMinExpectedTests = 0

        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (0 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test
    void testTaskAction_OneTest() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (1 test)',  // NOTE: 'test' is singular for stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test
    void testTaskAction_MultipleTests() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'IGNORE\nOK (2 tests)\nIGNORE',  // stdout
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    @Test(expected=InvalidUserDataException.class)
    void testTaskAction_CantParseOutput() {
        setupTask()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        demandCopyForJ2objcTest(mockProjectExec)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        proj.file('build/j2objcTest/debug/testJ2objc').absolutePath,
                        "org.junit.runner.JUnitCore",
                ],
                'OK (2 testXXXX)',  // NOTE: invalid stdout fails matchRegexOutputs
                '',  // stderr
                null)

        j2objcTest.test()

        mockProjectExec.verify()
    }

    private void demandCopyForJ2objcTest(MockProjectExec mockProjectExec) {
        // Delete test directory
        mockProjectExec.demandDeleteAndReturn(
                proj.file('build/j2objcTest/debug').absolutePath)
        // Copy main resources, test resources and test binary to test directory
        mockProjectExec.demandMkDirAndReturn(
                proj.file('build/j2objcTest/debug').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcTest/debug').absolutePath,
                proj.file('src/main/resources').absolutePath,
                proj.file('src/test/resources').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcTest/debug').absolutePath,
                proj.file('build/binaries/testJ2objcExecutable/debug/testJ2objc').absolutePath)
    }

    // TODO: test_Simple() - with some real unit tests

    // TODO: test_Complex() - preferably using real project in src/test/resources
}
