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
import org.junit.Test;

/**
 * Utils tests.
 */
// Double quotes are used throughout this file to avoid escaping single quotes
// which are common in Podfiles, used extensively within these tests
public class XcodeTaskTest {

    // TODO: use this within future tests
    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    public void getPodFile_Valid() {
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
    public void getPodFile_Invalid() {
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
    public void testWriteUpdatedPodFileIfNeeded() {

        // Write temp file that's deleted on exit
        File PodFile = File.createTempFile("Podfile","")
        PodFile.deleteOnExit()
        PodFile.write(
                "target 'IosApp' do\n" +
                "end")

        XcodeTask.writeUpdatedPodFileIfNeeded(
                PodFile, 'IosApp', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                // Newly added line
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> readPodFileLines = PodFile.readLines()
        assert expectedLines == readPodFileLines
    }

    @Test
    public void testGetPodFileLinesIfChanged_UpToDate() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'IosApp', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')

        assert null == newPodFileLines
    }

    @Test
    public void testGetPodFileLinesIfChanged_PodPathMissing() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "",
                "end"]

        List<String> newPodFileLines = XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'IosApp', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                "",
                // Newly added line
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    public void testGetPodFileLinesIfChanged_PodPathWrong() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared', :path => '/Users/WRONG/dev/workspace/shared/build'",
                "end"]

        List<String> newPodFileLines = XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'IosApp', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                // Modified line
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    public void testGetPodFileLinesIfChanged_CleansUpDuplicates() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'pod1', :path => 'IGNORE'",
                // Note the duplicated lines with the WRONG_PATH
                "pod 'j2objc-shared', :path => '/Users/WRONG_PATH_ONE/dev/workspace/shared/build'",
                "pod 'j2objc-shared', :path => '/Users/WRONG_PATH_TWO/dev/workspace/shared/build'",
                "pod 'pod1', :path => 'IGNORE'",
                "end"]

        List<String> newPodFileLines = XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'IosApp', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'IosApp' do",
                "pod 'pod1', :path => 'IGNORE'",
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                // Only cleans up podName that it's looking for, so this duplicate isn't fixed
                "pod 'pod1', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test
    public void testGetPodFileLinesIfChanged_Complex() {
        // Updates incorrect path
        // Cleans up duplicates
        // Handles multiple Xcode Targets
        List<String> podFileLines = [
                "target 'WrongTarget' do",
                "pod 'pod1', :path => 'IGNORE'",
                "end",
                "",
                "target 'DifferentTargetName' do",
                "pod 'pod2', :path => 'IGNORE'",
                // Note the two distinct wrong paths and duplicated entries
                "pod 'j2objc-different-name', :path => '/Users/WRONG_PATH_ONE/dev/workspace/shared/build'",
                "pod 'j2objc-different-name', :path => '/Users/WRONG_PATH_TWO/dev/workspace/shared/build'",
                "end",
                "",
                "target 'AnotherWrongTarget' do",
                "",
                "pod 'pod3', :path => 'IGNORE'",
                "end"]

        List<String> newPodFileLines = XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'DifferentTargetName', 'j2objc-different-name',
                '/Users/USERNAME/dev/workspace/shared/build')

        List<String> expectedLines = [
                "target 'WrongTarget' do",
                "pod 'pod1', :path => 'IGNORE'",
                "end",
                "",
                "target 'DifferentTargetName' do",
                "pod 'pod2', :path => 'IGNORE'",
                // Note the fixed path, no duplicate
                "pod 'j2objc-different-name', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end",
                "",
                "target 'AnotherWrongTarget' do",
                "",
                "pod 'pod3', :path => 'IGNORE'",
                "end"]

        assert expectedLines == newPodFileLines
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPodFileLinesIfChanged_XcodeTargetNotFound() {
        List<String> podFileLines = [
                "target 'IosApp' do",
                "pod 'j2objc-shared', :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        XcodeTask.getPodFileLinesIfChanged(
                podFileLines, 'XCODE_TARGET_NOT_FOUND', 'j2objc-shared',
                '/Users/USERNAME/dev/workspace/shared/build')
    }
}
