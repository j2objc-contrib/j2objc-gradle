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
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException;

/**
 * Utils tests.
 */
// Double quotes are used throughout this file to avoid escaping single quotes
// which are common in Podfiles, used extensively within these tests
class XcodeTaskTest {

    // TODO: use this within future tests
    private Project proj

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    void setUp() {
        // Mac OS X is the only OS that can run this task
        Utils.setFakeOSMacOSX()
        proj = ProjectBuilder.builder().build()
    }

    @After
    void tearDown() {
        Utils.setFakeOSNone()
    }

    @Test
    void getPodfileFile_Valid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.xcodeProjectDir = '../ios'
        j2objcConfig.xcodeTargets = ['IOS-APP']

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)
        j2objcXcode.verifyXcodeArgs()
        File podfile = j2objcXcode.getPodfileFile()

        String expectedPath = proj.file('../ios/Podfile').absolutePath
        assert expectedPath == podfile.absolutePath
    }

    // Test that null xcode arguments cause the expected exception
    @Test(expected = InvalidUserDataException.class)
    void getPodfileFile_Invalid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        assert null == j2objcConfig.xcodeProjectDir
        assert 0 == j2objcConfig.xcodeTargets.size()

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        // Test for fixing issue #226
        j2objcXcode.getPodfileFile()
    }

    @Test
    void testXcodeConfig_Windows() {
        Utils.setFakeOSWindows()

        String j2objcHome
        J2objcConfig j2objcConfig
        (proj, j2objcHome, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Mac OS X is required for j2objcXcode task')

        // Makes assumption that OS is checked before any other tests
        j2objcXcode.xcodeConfig()
    }

    @Test
    void testXcodeConfig_Basic() {
        String j2objcHome
        J2objcConfig j2objcConfig
        (proj, j2objcHome, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))

        j2objcConfig.xcodeProjectDir = '../ios'

        // Podfile Write
        // This is outside of the project's temp directory but appears to work fine
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()
        File podfile = proj.file('../ios/Podfile')
        podfile.deleteOnExit()
        podfile.write(
                "target 'IOS-APP' do\n" +
                "end\n" +
                "\n" +
                "target 'IOS-APPTests' do\n" +
                "end")

        // Expectations
        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                proj.file('../ios').absolutePath,  // working directory
                [
                        "pod",
                        "install",
                ],
                null,
                null,
                null)

        // XcodeTask requires this task for the podspec names
        proj.tasks.create(name: 'j2objcPodspec', type: PodspecTask)

        // Action
        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)
        j2objcXcode.xcodeConfig()

        // Verify mock project
        mockProjectExec.verify()

        // Verify Podfile has podspec references
        String podNameMethod = "j2objc_${proj.name}"
        String podNameDebug = "j2objc-${proj.name}-debug"
        String podNameRelease = "j2objc-${proj.name}-release"
        String path = "../${proj.getProjectDir().getName()}/build"
        List<String> expectedPodfile = [
                "def $podNameMethod",
                "    pod '$podNameDebug', :configuration => ['Debug'], :path => '$path'",
                "    pod '$podNameRelease', :configuration => ['Release'], :path => '$path'",
                "end",
                "",
                "target 'IOS-APP' do",
                "    $podNameMethod",
                "end",
                "",
                "target 'IOS-APPTests' do",
                "    $podNameMethod",
                "end"]
        List<String> readPodfileLines = podfile.readLines()
        assert expectedPodfile == readPodfileLines
    }

    @Test
    void testXcodeConfig_NeedsPodInit() {
        Object unused
        J2objcConfig j2objcConfig
        (proj, unused, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))

        j2objcConfig.xcodeProjectDir = 'ios'
        j2objcConfig.xcodeTargets = ['IOS-APP']

        // Needed for podspec
        proj.file(proj.buildDir).mkdir()
        // Needed for Podfile
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        try {
            j2objcXcode.xcodeConfig()
            assert false, 'Expected Exception'
        } catch (InvalidUserDataException exception) {
            assert exception.toString().contains("Set xcodeProjectDir to the directory containing 'IOS-APP.xcodeproj':")
            assert exception.toString().contains("Within that directory, create the Podfile with:")
            assert exception.toString().contains("(cd ${proj.file('ios').absolutePath} && pod init)")
            assert exception.toString().contains("sudo gem install cocoapods")
        }

        // Verify no calls to project.copy, project.delete or project.exec
        mockProjectExec.verify()
    }

    @Test
    void testVerifyXcodeArgs() {
        Object unused
        J2objcConfig j2objcConfig
        (proj, unused, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))
        assert null == j2objcConfig.xcodeProjectDir
        assert 0 == j2objcConfig.xcodeTargets.size()

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        // Expect exception suggesting to configure j2objcConfig:
        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage("xcodeProjectDir '../ios'")

        j2objcXcode.verifyXcodeArgs()
    }

    @Test
    void testExtractXcodeTargets_Simple() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end"]

        List<String> xcodeTargets = XcodeTask.extractXcodeTargets(podfileLines)

        List<String> expectedXcodeTargets = ['IOS-APP']
        assert expectedXcodeTargets == xcodeTargets
    }

    @Test
    void testExtractXcodeTargets_WatchKit() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end",
                "target 'IOS-APPTests' do",
                "end",
                "target 'IOS-APP WatchKit App' do",
                "end",
                "target 'IOS-APP WatchKit Extension' do",
                "end"]

        List<String> xcodeTargets = XcodeTask.extractXcodeTargets(podfileLines)

        List<String> expectedXcodeTargets = [
                'IOS-APP',
                'IOS-APPTests',
                'IOS-APP WatchKit App',
                'IOS-APP WatchKit Extension']
        assert expectedXcodeTargets == xcodeTargets
    }

    @Test
    void testWriteUpdatedPodfileIfNeeded_Needed() {

        // Write temp Podfile that's deleted on exit
        File podfile = File.createTempFile("podfile","")
        podfile.deleteOnExit()
        podfile.write(
                "target 'IOS-APP' do\n" +
                "end")

        // Update the Podfile
        String podspecBuildDir = podfile.getParentFile().getParentFile().toString() + '/PROJ/BUILD'
        List<XcodeTask.PodspecDetails> podspecDetailsList = new ArrayList<>()
        podspecDetailsList.add(new XcodeTask.PodspecDetails(
                'PROJ',
                // It doesn't matter that these files don't exist, only their relative path to Podfile
                new File(podspecBuildDir + '/j2objc-PROJ-debug.podspec'),
                new File(podspecBuildDir + '/j2objc-PROJ-release.podspec')))
        XcodeTask.writeUpdatedPodfileIfNeeded(
                podspecDetailsList, ['IOS-APP'], podfile, null)

        // Verify modified Podfile
        List<String> expectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "",
                "target 'IOS-APP' do",
                "    j2objc_PROJ",
                "end"]
        List<String> readPodfileLines = podfile.readLines()
        assert expectedLines == readPodfileLines
    }

    @Test
    void testWriteUpdatedPodfileIfNeeded_NotNeeded() {

        // Write temp Podfile that's deleted on exit
        List<String> writtenLines = [
            "def j2objc_PROJ",
            "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
            "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
            "end",
            "",
            "target 'IOS-APP' do",
            "    j2objc_PROJ",
            "end"]
        File podfile = File.createTempFile("podfile","")
        podfile.deleteOnExit()
        podfile.write(writtenLines.join('\n'))

        // Update the Podfile
        String podspecBuildDir = podfile.getParentFile().getParentFile().toString() + '/PROJ/BUILD'
        List<XcodeTask.PodspecDetails> podspecDetailsList = new ArrayList<>()
        podspecDetailsList.add(new XcodeTask.PodspecDetails(
                'PROJ',
                new File(podspecBuildDir + '/j2objc-PROJ-debug.podspec'),
                new File(podspecBuildDir + '/j2objc-PROJ-release.podspec')))
        XcodeTask.writeUpdatedPodfileIfNeeded(
                podspecDetailsList, ['IOS-APP'], podfile, null)

        // Missing verification that the file wasn't written but verifies it's the same as before
        List<String> readPodfileLines = podfile.readLines()
        assert writtenLines == readPodfileLines
    }

    @Test
    void testUpdatePodfile_Complex() {
        // 1) Clean up pod method
        // 2) Add pod method to IOS-APP target
        // 3) Remove pod method from IOS-APPTest target
        List<String> podfileLines = [
                "def j2objc_PROJ",
                "    RANDOM-CRUFT-TO-BE-DELETED",
                "    pod 'j2objc-PROJ-IGNORE', :configuration => ['Release'], :path => '/WRONG-DIR'",
                "end",
                "",
                "target 'IOS-APP' do",
                "",
                "    pod 'IGNORE1', :path => 'IGNORE'",
                "end",
                "",
                "target 'IOS-APP WatchKit App' do",
                "    j2objc_PROJ",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines,
                new XcodeTask.PodspecDetails(
                        'PROJ',
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec')),
                ['IOS-APP'],
                new File('/SRC/ios/Podfile'),
                null)

        List<String> expectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "",
                "target 'IOS-APP' do",
                "",
                "    pod 'IGNORE1', :path => 'IGNORE'",
                "    j2objc_PROJ",
                "end",
                "",
                "target 'IOS-APP WatchKit App' do",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test
    // If xcodeTargets == [], then include all targets
    void testUpdatePodfile_DefaultsToAllTargets() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "    pod 'IGNORE1', :path => 'IGNORE'",
                "end",
                "",
                "target 'IOS-APPTests' do",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "end",
                "",
                "target 'IOS-APP WatchKit App' do",
                "    j2objc_PROJ",
                "    pod 'IGNORE3', :path => 'IGNORE'",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines,
                new XcodeTask.PodspecDetails(
                        'PROJ',
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec')),
                [],  // xcodeTargets is empty to test default of all targets
                new File('/SRC/ios/Podfile'),
                null)

        List<String> expectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "",
                "target 'IOS-APP' do",
                "    pod 'IGNORE1', :path => 'IGNORE'",
                "    j2objc_PROJ",
                "end",
                "",
                "target 'IOS-APPTests' do",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "    j2objc_PROJ",
                "end",
                "",
                "target 'IOS-APP WatchKit App' do",
                "    j2objc_PROJ",
                "    pod 'IGNORE3', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test(expected = InvalidUserDataException.class)
    void testUpdatePodfileTarget_TargetNotFound() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "pod 'j2objc-PROJ-debug', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        XcodeTask.updatePodfileTarget(
                podfileLines, 'XCODE_TARGET_DOES_NOT_EXIST',
                'j2objc_PROJ', true)
    }

    @Test
    void testUpdatePodfileTarget_AddAndRemove() {
        List<String> podfileTargetEmpty = [
                "target 'IOS-APP' do",
                "end"]

        List<String> podfileTargetWithMethod = [
                "target 'IOS-APP' do",
                "    j2objc_PROJ",
                "end"]

        // Remove no-op
        List<String> newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileTargetEmpty, 'IOS-APP', 'j2objc_PROJ', false)
        assert newPodfileLines == newPodfileLines

        // Add
        newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileTargetEmpty, 'IOS-APP', 'j2objc_PROJ', true)
        assert podfileTargetWithMethod == newPodfileLines

        // Add no-op
        newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileTargetEmpty, 'IOS-APP', 'j2objc_PROJ', true)
        assert podfileTargetWithMethod == newPodfileLines

        // Remove
        newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileTargetEmpty, 'IOS-APP', 'j2objc_PROJ', false)
        assert newPodfileLines == newPodfileLines
    }

    @Test
    void testUpdatePodfileTarget_PreserveOrdering() {
        List<String> podfileLines = [
                "target 'TARGET_A' do",
                "    pod 'IGNORE1', :path => 'IGNORE'",
                "    j2objc_PROJ",
                "end",
                "",
                "target 'TARGET_B' do",
                "    j2objc_PROJ",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileLines, 'TARGET_A', 'j2objc_PROJ', true)
        newPodfileLines = XcodeTask.updatePodfileTarget(
                newPodfileLines, 'TARGET_B', 'j2objc_PROJ', true)

        // Preserves the ordering of the lines
        assert podfileLines == newPodfileLines
    }

    @Test
    // For upgrade from v0.4.3 to v0.5.0
    void testUpdatePodfileTarget_PodMethodUpgrade() {
        List<String> podfileLines = [
                // pod method should not be affected by removal of the old code
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end",
                "",
                "target 'TARGET' do",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "end"]

        List<String> expectedPodfileLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end",
                "",
                "target 'TARGET' do",
                "    pod 'IGNORE2', :path => 'IGNORE'",
                "    j2objc_PROJ",
                "end"]

        // First update cleans up the Podfile, replacing within targets definitions with pod method
        List<String> newPodfileLines = XcodeTask.updatePodfileTarget(
                podfileLines, 'TARGET', 'j2objc_PROJ', true)
        assert expectedPodfileLines == newPodfileLines

        // Second update has no effect
        newPodfileLines = XcodeTask.updatePodfileTarget(
                newPodfileLines, 'TARGET', 'j2objc_PROJ', true)
        assert expectedPodfileLines == newPodfileLines
    }
}
