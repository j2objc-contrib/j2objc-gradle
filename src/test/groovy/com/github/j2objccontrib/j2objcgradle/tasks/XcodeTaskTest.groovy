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

    @Test
    void getPodFile_Valid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.xcodeProjectDir = '../ios'
        j2objcConfig.xcodeTarget = 'IOS-APP'

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)
        j2objcXcode.verifyXcodeArgs()
        File podFile = j2objcXcode.getPodfileFile()

        String expectedPath = proj.file('../ios/Podfile').absolutePath
        assert expectedPath == podFile.absolutePath
    }

    // Test that null xcode arguments cause the expected exception
    @Test(expected = InvalidUserDataException.class)
    void getPodFile_Invalid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        assert null == j2objcConfig.xcodeProjectDir
        assert null == j2objcConfig.xcodeTarget

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
        j2objcConfig.xcodeTarget = 'IOS-APP'

        // Needed for podspecDebug
        proj.file(proj.buildDir).mkdir()
        // Needed for Podfile
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()
        // Podfile written without podspecDebug reference
        File podfile = proj.file('../ios/Podfile')
        podfile.write(
                "target 'IOS-APP' do\n" +
                "end")

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        // Demands for exec and copy
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

        j2objcXcode.xcodeConfig()

        mockProjectExec.verify()

        // Podname and library name are reversed
        String podNameDebug = "j2objc-${proj.name}-debug"
        String podNameRelease = "j2objc-${proj.name}-release"
        // libName is the same for debug and release
        String libName = "${proj.name}-j2objc"

        // Verify Podfile now has podspec references
        List<String> expectedPodfile = [
                "target 'IOS-APP' do",
                // Newly added line
                "pod '$podNameDebug', :configuration => ['Debug'], :path => '$proj.buildDir'",
                "pod '$podNameRelease', :configuration => ['Release'], :path => '$proj.buildDir'",
                "end"]
        List<String> readPodFileLines = podfile.readLines()
        assert expectedPodfile == readPodFileLines

        // Debug Podspec
        if (Utils.isWindowsNoFake()) {
            // TestingUtils.ProjectConfig converts j2objcHome to forwards slashes on Windows,
            // this is due to backslashes in local.properties being silently ignored
            j2objcHome = j2objcHome.replace('/', '\\')
        }
        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = '$podNameDebug'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'j2objcOutputs/src/main/objc/**/*.h'",
                "  spec.resources = 'j2objcOutputs/src/main/resources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcOutputs/src/main/objc/**/*'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include',",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosDebug').absolutePath}'",
                "  }",
                "end"]
        File podspecDebug = proj.file("build/${podNameDebug}.podspec")
        List<String> readPodspecDebug = podspecDebug.readLines()
        assert expectedPodspecDebug == readPodspecDebug

        // Release Podspec
        List<String> expectedPodspecRelease = [
                "Pod::Spec.new do |spec|",
                "  spec.name = '$podNameRelease'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'j2objcOutputs/src/main/objc/**/*.h'",
                "  spec.resources = 'j2objcOutputs/src/main/resources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcOutputs/src/main/objc/**/*'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include',",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.file('build/j2objcOutputs/lib/iosRelease').absolutePath}'",
                "  }",
                "end"]
        File podspecRelease = proj.file("build/${podNameRelease}.podspec")
        List<String> readPodspecRelease = podspecRelease.readLines()
        assert expectedPodspecRelease == readPodspecRelease
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
        j2objcConfig.xcodeTarget = 'IOS-APP'

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
            assert exception.toString().contains('The Podfile must be created with this command')
            assert exception.toString().contains("(cd ${proj.file('ios').absolutePath} && pod init)")
        }

        // Verify no calls to project.copy, project.delete or project.exec
        mockProjectExec.verify()
    }

    @Test
    void testValidatePodspecPath_Ok() {
        XcodeTask.validatePodspecPath('/dir/dir', false)
        XcodeTask.validatePodspecPath('dir/dir', true)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_DoubleSlash() {
        XcodeTask.validatePodspecPath('/dir//dir', false)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_TrailingSlash() {
        XcodeTask.validatePodspecPath('/dir/dir/', false)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_AbsoluteInvalid() {
        XcodeTask.validatePodspecPath('/dir/dir', true)
    }

    @Test(expected=InvalidUserDataException.class)
    void testValidatePodspecPath_RelativeInvalid() {
        XcodeTask.validatePodspecPath('dir/dir', false)
    }

    @Test
    void testGenPodspec() {
        List<String> podspecDebug = XcodeTask.genPodspec(
                'POD-NAME', '/DEST-LIB-DIR', 'LIB-NAME',
                '/J2OBJC_HOME', 'MAIN-OBJC', 'MAIN-RESOURCES').split('\n')

        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = 'POD-NAME'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'MAIN-OBJC/**/*.h'",
                "  spec.resources = 'MAIN-RESOURCES/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'MAIN-OBJC/**/*'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', 'LIB-NAME'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '/J2OBJC_HOME/include',",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib /DEST-LIB-DIR'",
                "  }",
                "end"]

        assert expectedPodspecDebug == podspecDebug
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
        assert null == j2objcConfig.xcodeTarget

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        // Expect exception suggesting to configure j2objcConfig:
        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage("xcodeProjectDir '../ios'")
        expectedException.expectMessage("xcodeTarget 'IOS-APP'")

        j2objcXcode.verifyXcodeArgs()
    }

    @Test
    void testExtractXcodeTargets_Simple() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "end"]

        List<String> xcodeTargets = XcodeTask.extractXcodeTargets('IOS-APP', podFileLines)

        List<String> expectedXcodeTargets = ['IOS-APP']
        assert expectedXcodeTargets == xcodeTargets
    }

    @Test
    void testExtractXcodeTargets_WatchKit() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "end",
                "target 'IOS-APPTests' do",
                "end",
                "target 'IOS-APP WatchKit App' do",
                "end",
                "target 'IOS-APP WatchKit Extension' do",
                "end"]

        List<String> xcodeTargets = XcodeTask.extractXcodeTargets('IOS-APP', podFileLines)

        List<String> expectedXcodeTargets = [
                'IOS-APP',
                'IOS-APPTests',
                'IOS-APP WatchKit App',
                'IOS-APP WatchKit Extension']
        assert expectedXcodeTargets == xcodeTargets
    }

    @Test
    void testWriteUpdatedPodFileIfNeeded_Needed() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IOS-APP' do\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IOS-APP',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IOS-APP' do",
                // Newly added lines
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> readPodFileLines = PodFile.readLines()
        assert expectedLines == readPodFileLines
    }

    @Test
    void testWriteUpdatedPodFileIfNeeded_NotNeeded() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IOS-APP' do\n" +
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'\n" +
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IOS-APP',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')

        // Same as before
        List<String> expectedLines = [
                "target 'IOS-APP' do",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        // Missing verification that the file wasn't written but verifies it's the same as before
        List<String> readPodFileLines = PodFile.readLines()
        assert expectedLines == readPodFileLines
    }

    @Test(expected=InvalidUserDataException.class)
    void testWriteUpdatedPodFileIfNeeded_TargetNotFound() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IOS-APP' do\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'TARGET-NOT-FOUND',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')
    }

    @Test
    void testWriteUpdatedPodFileIfNeeded_MultipleTargets() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IOS-APP' do\n" +
                "\n" +
                "end\n" +
                "\n" +
                "target 'IOS-APP WatchKit App' do\n" +
                "\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IOS-APP',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IOS-APP' do",
                "",
                // Newly added lines
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end",
                "",
                "target 'IOS-APP WatchKit App' do",
                "",
                // Newly added lines
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> readPodFileLines = PodFile.readLines()
        assert expectedLines == readPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_UpToDate() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IOS-APP', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        assert podFileLines == newPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_PodPathMissing() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IOS-APP', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IOS-APP' do",
                "",
                // Newly added line
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_PodPathWrong() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "pod 'j2objc-shared-debug', :path => '/Users/WRONG/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IOS-APP',
                'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IOS-APP' do",
                // Modified line
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    void testUpdatePodFileLines_CleansUpDuplicates() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "pod 'pod1', :path => 'IGNORE'",
                // Note the duplicated lines with the WRONG_PATH
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/WRONG_PATH_ONE/dev/workspace/shared/build'",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/WRONG_PATH_TWO/dev/workspace/shared/build'",
                "pod 'j2objc-oldname-debug', :path => 'IGNORE'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IOS-APP', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IOS-APP' do",
                "pod 'pod1', :path => 'IGNORE'",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                // Only cleans up podName that it's looking for, so this duplicate isn't fixed
                "pod 'j2objc-oldname-debug', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    void testUpdatePodFileLines_Complex() {
        // Updates incorrect path
        // Cleans up duplicates
        // Handles multiple Xcode Targets
        List<String> podFileLines = [
                "target 'WrongTarget' do",
                "pod 'pod1', :path => 'IGNORE'",
                "end",
                "",
                "target 'Target' do",
                "pod 'pod2', :path => 'IGNORE'",
                // Note the two distinct wrong paths and duplicated entries
                "pod 'j2objc-shared-debug', :configuration => '['Debug'], :path => '/Users/WRONG_PATH_ONE/dev/workspace/shared/build'",
                "pod 'j2objc-shared-debug', :path => '/Users/WRONG_PATH_TWO/dev/workspace/shared/build'",
                "end",
                "",
                "target 'AnotherWrongTarget' do",
                "",
                "pod 'pod3', :path => 'IGNORE'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'Target',
                'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'WrongTarget' do",
                "pod 'pod1', :path => 'IGNORE'",
                "end",
                "",
                "target 'Target' do",
                "pod 'pod2', :path => 'IGNORE'",
                // Note the fixed path, no duplicate
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end",
                "",
                "target 'AnotherWrongTarget' do",
                "",
                "pod 'pod3', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test(expected = InvalidUserDataException.class)
    void testUpdatePodFileLines_XcodeTargetNotFound() {
        List<String> podFileLines = [
                "target 'IOS-APP' do",
                "pod 'j2objc-shared-debug', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        XcodeTask.updatePodFileLines(
                podFileLines, 'XCODE_TARGET_NOT_FOUND',
                'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')
    }
}
