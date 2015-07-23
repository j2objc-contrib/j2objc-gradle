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
        proj = ProjectBuilder.builder().build()
    }

    @Test
    void getPodFile_Valid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.xcodeProjectDir = '../ios'
        j2objcConfig.xcodeTarget = 'IosApp'

        // Cast required as return type of create(...) is Task
        XcodeTask j2objcXcode =
                (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask) {}
        j2objcXcode.verifyXcodeArgs()
        File podFile = j2objcXcode.getPodFile()

        String expectedPath = proj.file('../ios').absolutePath + '/Podfile'
        assert expectedPath == podFile.absolutePath
    }

    // Test that null xcode arguments cause the expected exception
    @Test(expected = InvalidUserDataException.class)
    void getPodFile_Invalid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        assert j2objcConfig.xcodeProjectDir == null
        assert j2objcConfig.xcodeTarget == null

        // Cast required as return type of create(...) is Task
        XcodeTask j2objcXcode =
                (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask) {}
        // Test for fixing issue #226
        j2objcXcode.getPodFile()
    }

    @Test
    void testXcodeConfig_Basic() {
        Object unused
        String j2objcHome
        J2objcConfig j2objcConfig
        (proj, j2objcHome, j2objcConfig) =
                TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                        applyJavaPlugin: true,
                        createJ2objcConfig: true))
        // TODO: should be '../ios' but that needs temp project to be subdirectory of temp dir
        j2objcConfig.xcodeProjectDir = 'ios'
        j2objcConfig.xcodeTarget = 'IosApp'

        // Needed for podspecDebug
        proj.file(proj.buildDir).mkdir()
        // Needed for Podfile
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()

        // Cast required as return type of create(...) is Task
        XcodeTask j2objcXcode =
                (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask) {
                    srcGenDir = proj.file("${proj.buildDir}/j2objcSrcGen")
                }

        // Podfile written without podspecDebug reference
        File podfile = proj.file('ios/Podfile')
        podfile.write(
                "target 'IosApp' do\n" +
                "end")

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                'ios',  // working directory
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
                "target 'IosApp' do",
                // Newly added line
                "pod '$podNameDebug', :configuration => ['Debug'], :path => '${proj.projectDir}/build'",
                "pod '$podNameRelease', :configuration => ['Release'], :path => '${proj.projectDir}/build'",
                "end"]
        List<String> readPodFileLines = podfile.readLines()
        assert expectedPodfile == readPodFileLines

        // Debug Podspec
        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = '$podNameDebug'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'j2objcSrcGen**/*.h'",
                "  spec.resources = 'j2objcResources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcSrcGen**/*.a'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include',",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.projectDir}/build/j2objcOutputs/lib/iosDebug'",
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
                "  spec.public_header_files = 'j2objcSrcGen**/*.h'",
                "  spec.resources = 'j2objcResources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcSrcGen**/*.a'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '${j2objcHome}/include',",
                "    'LIBRARY_SEARCH_PATHS' => '${j2objcHome}/lib ${proj.projectDir}/build/j2objcOutputs/lib/iosRelease'",
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
        // TODO: should be '../ios' but that needs temp project to be subdirectory of temp dir
        j2objcConfig.xcodeProjectDir = 'ios'
        j2objcConfig.xcodeTarget = 'IosApp'

        // Needed for podspec
        proj.file(proj.buildDir).mkdir()
        // Needed for Podfile
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()

        // Cast required as return type of create(...) is Task
        XcodeTask j2objcXcode =
                (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask) {
                    srcGenDir = proj.file("${proj.buildDir}/j2objcSrcGen")
                }

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('The Podfile must be created with this command')
        expectedException.expectMessage("(cd ${proj.projectDir}/ios && pod init)")

        j2objcXcode.xcodeConfig()
    }

    @Test
    void testGenPodspec_Debug() {
        List<String> podspecDebug = XcodeTask.genPodspec(
                'j2objc-shared-debug', '/PROJECTDIR/build/j2objcOutputs/lib/iosDebug', 'shared-j2objc',
                '/J2OBJC_HOME', 'j2objcSrcGen', 'j2objcResources/**/*').split('\n')

        List<String> expectedPodspecDebug = [
                "Pod::Spec.new do |spec|",
                "  spec.name = 'j2objc-shared-debug'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'j2objcSrcGen**/*.h'",
                "  spec.resources = 'j2objcResources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcSrcGen**/*.a'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', 'shared-j2objc'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '/J2OBJC_HOME/include',",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib /PROJECTDIR/build/j2objcOutputs/lib/iosDebug'",
                "  }",
                "end"]

        assert expectedPodspecDebug == podspecDebug
    }

    @Test
    void testGenPodspec_Release() {
        List<String> podspecRelease = XcodeTask.genPodspec(
                'j2objc-shared-release', '/PROJECTDIR/build/j2objcOutputs/lib/iosRelease', 'shared-j2objc',
                '/J2OBJC_HOME', 'j2objcSrcGen', 'j2objcResources/**/*').split('\n')

        List<String> expectedPodspecRelease = [
                "Pod::Spec.new do |spec|",
                "  spec.name = 'j2objc-shared-release'",
                "  spec.version = '1.0'",
                "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'",
                "  spec.public_header_files = 'j2objcSrcGen**/*.h'",
                "  spec.resources = 'j2objcResources/**/*'",
                "  spec.requires_arc = true",
                "  spec.preserve_paths = 'j2objcSrcGen**/*.a'",
                "  spec.libraries = 'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', 'shared-j2objc'",
                "  spec.xcconfig = {",
                "    'HEADER_SEARCH_PATHS' => '/J2OBJC_HOME/include',",
                "    'LIBRARY_SEARCH_PATHS' => '/J2OBJC_HOME/lib /PROJECTDIR/build/j2objcOutputs/lib/iosRelease'",
                "  }",
                "end"]

        assert expectedPodspecRelease == podspecRelease
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

        // Cast required as return type of create(...) is Task
        XcodeTask j2objcXcode =
                (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)

        // Expect exception suggesting to configure j2objcConfig:
        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage("xcodeProjectDir '../ios'")
        expectedException.expectMessage("xcodeTarget 'IOS-APP-TARGET'")

        j2objcXcode.verifyXcodeArgs()
    }

    @Test
    void testWriteUpdatedPodFileIfNeeded_Needed() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IosApp' do\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IosApp',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                // Newly added line
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
                "target 'IosApp' do\n" +
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'\n" +
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IosApp',
                'j2objc-shared-debug', 'j2objc-shared-release',
                '/Users/USERNAME/dev/workspace/shared/build')

        // Same as before
        List<String> expectedLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "pod 'j2objc-shared-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        // Missing verification that the file wasn't written but verifies it's the same as before
        List<String> readPodFileLines = PodFile.readLines()
        assert expectedLines == readPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_UpToDate() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IosApp', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        assert podFileLines == newPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_PodPathMissing() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IosApp', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                "",
                // Newly added line
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    void testGetPodFileLinesIfChanged_PodPathWrong() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared-debug', :path => '/Users/WRONG/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IosApp',
                'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                // Modified line
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    void testUpdatePodFileLines_CleansUpDuplicates() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'pod1', :path => 'IGNORE'",
                // Note the duplicated lines with the WRONG_PATH
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/WRONG_PATH_ONE/dev/workspace/shared/build'",
                "pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/WRONG_PATH_TWO/dev/workspace/shared/build'",
                "pod 'j2objc-oldname-debug', :path => 'IGNORE'",
                "end"]

        List<String> newPodFileLines = XcodeTask.updatePodFileLines(
                podFileLines, 'IosApp', 'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
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
                "target 'IosApp' do",
                "pod 'j2objc-shared-debug', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        XcodeTask.updatePodFileLines(
                podFileLines, 'XCODE_TARGET_NOT_FOUND',
                'j2objc-shared-debug', ['Debug'],
                '/Users/USERNAME/dev/workspace/shared/build')
    }
}
