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

    XcodeTask.XcodeTargetDetails xcodeTargetDetailsIosAppOnly =
            new XcodeTask.XcodeTargetDetails(
                    ['IOS-APP'], [], [],
                    // Append '.0' to version number to check that it's not using defaults
                    '6.0.0', '10.6.0', '1.0.0')
    List<XcodeTask.PodspecDetails> podspecDetailsProj =
            [new XcodeTask.PodspecDetails(
                    'PROJ',
                    new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                    new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec'),
                    ['Debug'], ['Release'])]
    XcodeTask.XcodeTargetDetails xcodeTargetDetailsEmpty =
            new XcodeTask.XcodeTargetDetails(
                    [], [], [], null, null, null)

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
    void testPodspecDetails_serialization() {
        // From: http://stackoverflow.com/a/9775330/1509221
        XcodeTask.PodspecDetails podspecDetailsIn = new XcodeTask.PodspecDetails(
                'pname', new File('fileDebug'), new File('fileRelease'),
                ['Debug'], ['Release'])

        ByteArrayOutputStream bOut = new ByteArrayOutputStream()
        ObjectOutputStream sOut = new ObjectOutputStream(bOut)
        sOut.writeObject(podspecDetailsIn)
        sOut.close()

        byte[] payload = bOut.toByteArray()

        ByteArrayInputStream bIn = new ByteArrayInputStream(payload);
        ObjectInputStream sIn = new ObjectInputStream(bIn)
        XcodeTask.PodspecDetails podspecDetailsOut = (XcodeTask.PodspecDetails) sIn.readObject()

        assert 'pname' == podspecDetailsOut.projectName
        assert 'fileDebug' == podspecDetailsOut.podspecDebug.path
        assert 'fileRelease' == podspecDetailsOut.podspecRelease.path
    }

    @Test
    // Check that the project name is converted to a valid ruby method name
    // http://stackoverflow.com/a/10542599/1509221
    void testPodspecDetails_getPodMethodName_validForRuby() {
        XcodeTask.PodspecDetails podspecDetails = new XcodeTask.PodspecDetails(
                'project-NAME_09.!?=', null, null, ['Debug'], ['Release'])
        assert 'j2objc_project_NAME_09____' == podspecDetails.getPodMethodName()
    }

    @Test
    void testGetPodfileFile_Valid() {
        J2objcConfig j2objcConfig =
                proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.xcodeProjectDir = '../ios'
        j2objcConfig.xcodeTargetsIos = ['IOS-APP']

        XcodeTask j2objcXcode = (XcodeTask) proj.tasks.create(name: 'j2objcXcode', type: XcodeTask)
        File podfile = j2objcXcode.getPodfileFile()

        String expectedPath = proj.file('../ios/Podfile').absolutePath
        assert expectedPath == podfile.absolutePath
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
        j2objcConfig.xcodeTargetsIos = ['IOS-APP', 'IOS-APPTests']
        j2objcConfig.minVersionIos = '6.1.0'

        // Podfile Write
        // This is outside of the project's temp directory but appears to work fine
        proj.file(j2objcConfig.xcodeProjectDir).mkdir()
        File podfile = proj.file('../ios/Podfile')
        podfile.deleteOnExit()
        podfile.write(
                "use_frameworks!\n" +
                "\n" +
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
                        'pod',
                        'install',
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
        String path = "../${proj.getProjectDir().getName()}/build/j2objcOutputs"
        List<String> expectedPodfile = [
                "use_frameworks!",
                "",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def $podNameMethod",
                "    pod '$podNameDebug', :configuration => ['Debug'], :path => '$path'",
                "    pod '$podNameRelease', :configuration => ['Release'], :path => '$path'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    platform :ios, '6.1.0'",
                "    $podNameMethod",
                "end",
                "",
                "target 'IOS-APPTests' do",
                "    platform :ios, '6.1.0'",
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
        j2objcConfig.xcodeTargetsIos = ['IOS-APP']

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
            assert exception.toString().contains('Within that directory, create the Podfile with:')
            assert exception.toString().contains("(cd ${proj.file('ios').absolutePath} && pod init)")
            assert exception.toString().contains('sudo gem install cocoapods')
            assert exception.toString().contains('.xcworkspace')
            assert exception.toString().contains('bridging header')
        }

        // Verify no calls to project.copy, project.delete or project.exec
        mockProjectExec.verify()
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
    void testRegexStripLines_noStartMatch() {
        List<String> oldLines = [
                'st-art',
                'strip',
                'en-d']

        List<String> newLines = XcodeTask.regexStripLines(
                oldLines, /start/, /end/, /^strip$/)

        assert oldLines == newLines
    }

    @Test
    void testRegexStripLines_noEndMatchException() {
        List<String> oldLines = [
                'start',
                'strip',
                'en-d']

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Failed to find targetEndRegex: /end/')

        List<String> newLines = XcodeTask.regexStripLines(
                oldLines, /start/, /end/, /^strip$/)
    }

    @Test
    void testRegexStripLines_remove() {
        List<String> oldLines = [
                'strip outside',
                'start',
                'str-ip',
                'strip inside',
                'end']

        List<String> newLines = XcodeTask.regexStripLines(
                oldLines, /start/, /end/, /strip/)

        List<String> expectedNewLines = [
                'strip outside',
                'start',
                'str-ip',
                'end']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexStripLines_removeInclusivePreserveEndLine() {
        List<String> oldLines = [
                'BEFORE',
                'start',
                'end',
                'AFTER']

        List<String> newLines = XcodeTask.regexStripLines(
                oldLines, /start/, /end/, /.*/)

        List<String> expectedNewLines = [
                'BEFORE',
                'end',
                'AFTER']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexStripLines_multipleMatches() {
        List<String> oldLines = [
                'start',
                'strip',
                'end',
                '',
                'start',
                'strip',
                'end']

        List<String> newLines = XcodeTask.regexStripLines(
                oldLines, /start/, /end/, /strip/)

        List<String> expectedNewLines = [
                'start',
                'end',
                '',
                'start',
                'end']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexReplaceLines_noChange() {
        List<String> oldLines = [
                'strip outside',
                'start',
                'replace',
                'replace too',
                'end']

        List<String> newLines = XcodeTask.regexReplaceLines(
                oldLines, false, /start/, /end/, ['start', 'replace', 'replace too', 'end'])

        List<String> expectedNewLines = [
                'strip outside',
                'start',
                'replace',
                'replace too',
                'end']

        assert expectedNewLines == newLines
    }

    @Test
    void testRegexReplaceLInes_replace() {
        List<String> oldLines = [
                'strip outside',
                'start',
                'replace1',
                'replace2',
                'end']

        List<String> newLines = XcodeTask.regexReplaceLines(
                oldLines, false, /start/, /end/, ['replacement1', 'replacement2'])

        List<String> expectedNewLines = [
                'strip outside',
                'replacement1',
                'replacement2']

        assert expectedNewLines == newLines
    }

    @Test
    void testRegexReplaceLInes_preserveEndLine() {
        List<String> oldLines = [
                'strip outside',
                'start',
                'replace1',
                'replace2',
                'end']

        List<String> newLines = XcodeTask.regexReplaceLines(
                oldLines, true, /start/, /end/, ['replacement1', 'replacement2'])

        List<String> expectedNewLines = [
                'strip outside',
                'replacement1',
                'replacement2',
                'end']

        assert expectedNewLines == newLines
    }

    @Test
    void testRegexReplacesLines_doubleStart() {
        List<String> oldLines = [
                'start1',
                'start2',
                'end']

        // With mistaken logic, insertLines can be inserted twice
        // when two lines match startRegex before targetEndRegex is matched
        List<String> newLines = XcodeTask.regexReplaceLines(
                oldLines, false, /start1|start2/, /end/,
                [
                        'start1',
                        'start2',
                        'end'
                ])

        assert oldLines == newLines
    }

    @Test
    void testRegexInsertLines_afterStart() {
        List<String> oldLines = [
                'start',
                'in-between',
                'end']

        List<String> newLines = XcodeTask.regexInsertLines(
                oldLines, true, /start/, /end/, ['insert1', 'insert2'])

        List<String> expectedNewLines = [
                'start',
                'insert1',
                'insert2',
                'in-between',
                'end']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexInsertLines_notAfterStart() {
        List<String> oldLines = [
                'start',
                'in-between',
                'end']

        List<String> newLines = XcodeTask.regexInsertLines(
                oldLines, false, /start/, /end/, ['insert1', 'insert2'])

        List<String> expectedNewLines = [
                'start',
                'in-between',
                'insert1',
                'insert2',
                'end']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexInsertLines_ignoreMultipleMatches() {
        List<String> oldLines = [
                'start',
                'end',
                'start',
                'end']

        List<String> newLines = XcodeTask.regexInsertLines(
                oldLines, false, /start/, /end/, ['inserted'])

        List<String> expectedNewLines = [
                'start',
                'inserted',
                'end',
                'start',
                'end']
        assert expectedNewLines == newLines
    }

    @Test
    void testRegexInsertLines_noMatchStart() {
        List<String> oldLines = [
                'start',
                'end']

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Failed to find startRegex: /no-match-start/')

        XcodeTask.regexInsertLines(oldLines, false, /no-match-start/, /end/, new ArrayList<>())
    }

    @Test
    void testRegexInsertLines_noMatchEnd() {
        List<String> oldLines = [
                'start',
                'end']

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Failed to find targetEndRegex: /no-match-end/')

        XcodeTask.regexInsertLines(oldLines, false, /start/, /no-match-end/, new ArrayList<>())
    }

    @Test
    void testRegexMatchesLine() {
        List<String> lines = [
                'line1',
                'line2',
                'end']

        assert XcodeTask.regexMatchesLine(lines, /^line1/)
        assert XcodeTask.regexMatchesLine(lines, /[a-z]{4}2/)
        assert ! XcodeTask.regexMatchesLine(lines, /no-match/)
    }

    @Test
    void testAddNewPodMethod_podInitDefault() {
        List<String> podfileLines = [
                "# Uncomment this line to define a global platform for your project",
                "# platform :ios, '6.0'",
                "",
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.addNewPodMethod(podfileLines,
                ['', 'NEW-POD-METHOD1', 'NEW-POD-METHOD2', ''])

        List<String> expectedPodfileLines = [
                "# Uncomment this line to define a global platform for your project",
                "# platform :ios, '6.0'",
                "",
                "",
                "NEW-POD-METHOD1",
                "NEW-POD-METHOD2",
                "",
                "target 'IOS-APP' do",
                "end"]

        assert expectedPodfileLines == newPodfileLines
    }


    @Test
    void testAddNewPodMethod_podComplex() {
        List<String> podfileLines = [
                "source 'https://github.com/CocoaPods/Specs.git'",
                "platform :ios, '9.0'",
                "",
                "# ignore all warnings from all pods",
                "inhibit_all_warnings!",
                "use_frameworks!",
                "",
                "pod 'AFNetworking'",
                "",
                "post_install do |installer|",
                "LOTS OF COMPLEX RUBY",
                "end"]

        List<String> newPodfileLines = XcodeTask.addNewPodMethod(podfileLines,
                ['', 'NEW-POD-METHOD1', 'NEW-POD-METHOD2', ''])

        List<String> expectedPodfileLines = [
                "source 'https://github.com/CocoaPods/Specs.git'",
                "platform :ios, '9.0'",
                "",
                "# ignore all warnings from all pods",
                "inhibit_all_warnings!",
                "use_frameworks!",
                "",
                "pod 'AFNetworking'",
                "",
                "",
                "NEW-POD-METHOD1",
                "NEW-POD-METHOD2",
                "",
                "post_install do |installer|",
                "LOTS OF COMPLEX RUBY",
                "end"]

        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testAddNewPodMethod_noTargetFoundAppendsAtEnd() {
        List<String> podfileLines = [
                "# Uncomment this line to define a global platform for your project",
                "# platform :ios, '6.0'",
                ""]

        List<String> newPodfileLines = XcodeTask.addNewPodMethod(podfileLines,
                ['', 'NEW-POD-METHOD1', 'NEW-POD-METHOD2', ''])

        List<String> expectedPodfileLines = [
                "# Uncomment this line to define a global platform for your project",
                "# platform :ios, '6.0'",
                "",
                "",
                "NEW-POD-METHOD1",
                "NEW-POD-METHOD2",
                ""]

        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testWriteUpdatedPodfileIfNeeded_Needed_ThenNotNeeded() {

        // Write temp Podfile that's deleted on exit
        File podfile = File.createTempFile('Podfile', '')
        podfile.deleteOnExit()
        podfile.write(
                "#user comment\n" +
                "target 'IOS-APP' do\n" +
                "end")

        // Update the Podfile
        String podspecBuildDir = podfile.getParentFile().getParentFile().toString() + '/PROJ/BUILD'
        List<XcodeTask.PodspecDetails> podspecDetailsList = new ArrayList<>()
        podspecDetailsList.add(new XcodeTask.PodspecDetails(
                'PROJ',
                // Only their relative path to Podfile matters, they don't need to exist
                new File(podspecBuildDir + '/j2objc-PROJ-debug.podspec'),
                new File(podspecBuildDir + '/j2objc-PROJ-release.podspec'),
                ['Debug'], ['Release']))
        XcodeTask.writeUpdatedPodfileIfNeeded(
                podspecDetailsList, xcodeTargetDetailsIosAppOnly, false, podfile)

        // Verify modified Podfile
        List<String> expectedLines = [
                "#user comment",
                '',
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "end"]
        List<String> readPodfileLines = podfile.readLines()
        assert expectedLines == readPodfileLines

        // Verify unmodified on second call
        // TODO: verify that file wasn't written a second time
        XcodeTask.writeUpdatedPodfileIfNeeded(
                podspecDetailsList, xcodeTargetDetailsIosAppOnly, false, podfile)
        readPodfileLines = podfile.readLines()
        assert expectedLines == readPodfileLines
    }

    @Test
    void testUpdatePodfile_basic() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsIosAppOnly,
                false,
                new File('/SRC/ios/Podfile'))

        List<String> expectedPodfileLines = [
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "end"]
        assert expectedPodfileLines == newPodfileLines

        // Second time around is a no-op
        newPodfileLines = XcodeTask.updatePodfile(
                newPodfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsIosAppOnly,
                false,
                new File('/SRC/ios/Podfile'))
        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testUpdatePodfile_podfileTargetsManualConfig() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsEmpty ,
                true,
                new File('/SRC/ios/Podfile'))

        List<String> expectedPodfileLines = [
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "end"]
        assert expectedPodfileLines == newPodfileLines

        // Second time around is a no-op
        newPodfileLines = XcodeTask.updatePodfile(
                newPodfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsEmpty,
                true,
                new File('/SRC/ios/Podfile'))

        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testUpdatePodfile_complex() {
        List<String> podfileLines = [
                "# user comment",
                "",
                "target 'IOS-APP' do",
                "end",
                "target 'OSX-APP' do",
                "    j2objc_JUNK_TO_BE_DELETED",
                "end",
                "target 'WATCH-APP' do",
                "end"]
        XcodeTask.XcodeTargetDetails xcodeTargetDetails = new XcodeTask.XcodeTargetDetails(
                ['IOS-APP'], ['OSX-APP'], ['WATCH-APP'],
                '6.0.0', '10.6.0', '1.0.0')

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines,
                podspecDetailsProj,
                xcodeTargetDetails,
                false,
                new File('/SRC/ios/Podfile'))

        List<String> expectedPodfileLines = [
                "# user comment",
                "",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "end",
                "target 'OSX-APP' do",
                "    platform :osx, '10.6.0'",
                "    j2objc_PROJ",
                "end",
                "target 'WATCH-APP' do",
                "    platform :watchos, '1.0.0'",
                "    j2objc_PROJ",
                "end"]
        assert expectedPodfileLines == newPodfileLines

        // Second time around is a no-op
        newPodfileLines = XcodeTask.updatePodfile(
                newPodfileLines,
                podspecDetailsProj,
                xcodeTargetDetails,
                false,
                new File('/SRC/ios/Podfile'))
        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testUpdatePodfile_needJ2objcConfig() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end",
                "target 'WATCH-APP' do",
                "end"]

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage("You must configure the xcode targets for the J2ObjC Gradle Plugin")
        expectedException.expectMessage("It must be a subset of the valid targets: 'IOS-APP', 'WATCH-APP'")
        expectedException.expectMessage("xcodeTargetsIos 'IOS-APP', 'IOS-APPTests'  // example")

        XcodeTask.updatePodfile(
                podfileLines,
                [],
                new XcodeTask.XcodeTargetDetails(
                        [], [], [],
                        '6.0.0', '10.6.0', '1.0.0'),
                false,
                null)
    }

    @Test
    void testUpdatePodfile_invalidTarget() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end",
                "target 'WATCH-APP' do",
                "end"]

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage("Invalid j2objcConfig { xcodeTargetsIos 'TARGET-DOES-NOT-EXIST' }")
        expectedException.expectMessage("Must be one of the valid targets: 'IOS-APP', 'WATCH-APP'")

        XcodeTask.updatePodfile(
                podfileLines,
                [],
                new XcodeTask.XcodeTargetDetails(
                        ['TARGET-DOES-NOT-EXIST'], [], [],
                        '6.0.0', '10.6.0', '1.0.0'),
                false,
                null)
    }

    @Test
    void testUpdatePodfile_xcodeManualConfigWithNoContent() {
        List<String> podfileLines = []

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines, podspecDetailsProj,
                xcodeTargetDetailsEmpty,
                true,
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                ""]

        assert expectedLines == newPodfileLines
    }

    @Test
    void testUpdatePodfile_xcodeManualConfigComplexPodfile() {
        List<String> podfileLines = [
                "source 'https://github.com/CocoaPods/Specs.git'",
                "platform :ios, '7.0'",
                "",
                "# ignore all warnings from all pods",
                "inhibit_all_warnings!",
                "",
                "pod 'AFNetworking'",
                "pod 'OpenCV', '2.4.9.1'",
                "pod 'Facebook-iOS-SDK'",
                "",
                "post_install do |installer|",
                "target = installer.project.targets.find{|t| t.to_s == \"Pods-MagicalRecord\"}",
                "target.build_configurations.each do |config|",
                "s = config.build_settings['GCC_PREPROCESSOR_DEFINITIONS']",
                "s = [ '\$(inherited)' ] if s == nil;",
                "s.push('MR_ENABLE_ACTIVE_RECORD_LOGGING=0') if config.to_s == \"Debug\";",
                "config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] = s",
                "end",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodfile(
                podfileLines, podspecDetailsProj,
                xcodeTargetDetailsEmpty,
                true,
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "source 'https://github.com/CocoaPods/Specs.git'",
                "platform :ios, '7.0'",
                "",
                "# ignore all warnings from all pods",
                "inhibit_all_warnings!",
                "",
                "pod 'AFNetworking'",
                "pod 'OpenCV', '2.4.9.1'",
                "pod 'Facebook-iOS-SDK'",
                "",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "post_install do |installer|",
                "target = installer.project.targets.find{|t| t.to_s == \"Pods-MagicalRecord\"}",
                "target.build_configurations.each do |config|",
                "s = config.build_settings['GCC_PREPROCESSOR_DEFINITIONS']",
                "s = [ '\$(inherited)' ] if s == nil;",
                "s.push('MR_ENABLE_ACTIVE_RECORD_LOGGING=0') if config.to_s == \"Debug\";",
                "config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] = s",
                "end",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test
    void testUpdatePodMethods_noChange() {
        List<String> podfileLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodMethods(
                podfileLines,
                podspecDetailsProj,
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test
    void testUpdatePodMethods_upgradev051_oldest() {
        List<String> podfileLines = [
                "# user comment",
                "",
                // "# J2ObjC Gradle Plugin..." line is missing to make sure regex handles both cases
                "def j2objc_TO_BE_DELETED",
                "    pod 'j2objc-TO_BE_DELETED-debug', :configuration => ['Debug'], :path => '../shared/build'",
                "    pod 'j2objc-TO_BE_DELETED-release', :configuration => ['Release'], :path => '../shared/build'",
                "end",
                "",
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodMethods(
                podfileLines,
                podspecDetailsProj,
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test
    void testUpdatePodMethods_upgradev051_old() {
        List<String> podfileLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - DO NOT MODIFY from here to the first target",
                "def j2objc_PROJ",
                "    pod 'j2objc-TO_BE_DELETED-debug', :configuration => ['Debug'], :path => '../shared/build'",
                "    pod 'j2objc-TO_BE_DELETED-release', :configuration => ['Release'], :path => '../shared/build'",
                "end",
                "",
                "target 'IOS-APP' do",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodMethods(
                podfileLines,
                podspecDetailsProj,
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "end"]

        assert expectedLines == newPodfileLines

        // Repeated update has no effect
        List<String> updatedAgainPodfileLines = XcodeTask.updatePodMethods(
                newPodfileLines,
                podspecDetailsProj,
                new File('/SRC/ios/Podfile'))

        assert expectedLines == updatedAgainPodfileLines
    }

    @Test
    void testUpdatePodMethods_multipleProjects() {
        List<String> podfileLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_TO_BE_DELETED",
                "    pod 'j2objc-TO_BE_DELETED-debug', :configuration => ['Debug'], :path => '../shared/build'",
                "    pod 'j2objc-TO_BE_DELETED-release', :configuration => ['Release'], :path => '../shared/build'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    j2objc_DELETED_BY_ANOTHER_METHOD",
                "end"]

        List<String> newPodfileLines = XcodeTask.updatePodMethods(
                podfileLines,
                [new XcodeTask.PodspecDetails(
                        'PROJA',
                        new File('/SRC/PROJA/BUILD/j2objc-PROJA-debug.podspec'),
                        new File('/SRC/PROJA/BUILD/j2objc-PROJB-release.podspec'),
                        ['Debug'], ['Release']),
                 new XcodeTask.PodspecDetails(
                         'PROJB',
                         new File('/SRC/PROJB/BUILD/j2objc-PROJB-debug.podspec'),
                         new File('/SRC/PROJB/BUILD/j2objc-PROJB-release.podspec'),
                        ['Debug'], ['Release'])],
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "# user comment",
                "",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block",
                "def j2objc_PROJA",
                "    pod 'j2objc-PROJA-debug', :configuration => ['Debug'], :path => '../PROJA/BUILD'",
                "    pod 'j2objc-PROJB-release', :configuration => ['Release'], :path => '../PROJA/BUILD'",
                "end",
                "def j2objc_PROJB",
                "    pod 'j2objc-PROJB-debug', :configuration => ['Debug'], :path => '../PROJB/BUILD'",
                "    pod 'j2objc-PROJB-release', :configuration => ['Release'], :path => '../PROJB/BUILD'",
                "end",
                "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END",
                "",
                "target 'IOS-APP' do",
                "    j2objc_DELETED_BY_ANOTHER_METHOD",
                "end"]

        assert expectedLines == newPodfileLines
    }

    @Test
    void testUpdatePodMethods_customConfigurations() {
        XcodeTask.PodspecDetails podspecDetails =
                new XcodeTask.PodspecDetails(
                        'PROJ',
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec'),
                        ['Debug', 'Beta'], ['Release', 'Preview'])
        List<String> podMethodLines = XcodeTask.podMethodLines(
                podspecDetails,
                new File('/SRC/ios/Podfile'))
        List<String> expectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug','Beta'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release','Preview'], :path => '../PROJ/BUILD'",
                "end"]
        assert expectedLines == podMethodLines
    }

    @Test
    void testUpdatePodMethods_emptyConfigurations() {
        // Empty configurations list should omit pod line, while an empty string should be included
        // even though this would usually cause a CocoaPod error since an empty string is
        // unlikely to be a valid target name.
        XcodeTask.PodspecDetails debugPodspecDetails  =
                new XcodeTask.PodspecDetails(
                        'PROJ',
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec'),
                        ['Debug'], [])
        List<String> debugMethodLines = XcodeTask.podMethodLines(
                debugPodspecDetails,
                new File('/SRC/ios/Podfile'))
        List<String> debugExpectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "end"]
        assert debugExpectedLines == debugMethodLines

        XcodeTask.PodspecDetails releasePodspecDetails  =
                new XcodeTask.PodspecDetails(
                        'PROJ',
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-debug.podspec'),
                        new File('/SRC/PROJ/BUILD/j2objc-PROJ-release.podspec'),
                        [], ['Release'])
        List<String> releaseMethodLines = XcodeTask.podMethodLines(
                releasePodspecDetails,
                new File('/SRC/ios/Podfile'))
        List<String> releaseExpectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end"]
        assert releaseExpectedLines == releaseMethodLines
    }

    @Test
    void testPodMethodLines() {
        List<String> podMethodLines = XcodeTask.podMethodLines(
                podspecDetailsProj.get(0),
                new File('/SRC/ios/Podfile'))

        List<String> expectedLines = [
                "def j2objc_PROJ",
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '../PROJ/BUILD'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '../PROJ/BUILD'",
                "end"]

        assert expectedLines == podMethodLines
    }

    @Test
    void testUpdatePodfileTargets_basic() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end",
                "target 'IOS-APP-B' do",
                "end"]

        // Add 1st target
        List<String> newPodfileLines = XcodeTask.updatePodfileTargets(
                podfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsIosAppOnly)
        List<String> expectedPodfileLines = [
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "end",
                "target 'IOS-APP-B' do",
                "end"]
        assert expectedPodfileLines == newPodfileLines

        // Repeated call is noop
        newPodfileLines = XcodeTask.updatePodfileTargets(
                newPodfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsIosAppOnly)
        assert expectedPodfileLines == newPodfileLines

        // Swap to 2nd target
        newPodfileLines = XcodeTask.updatePodfileTargets(
                newPodfileLines,
                podspecDetailsProj,
                new XcodeTask.XcodeTargetDetails(
                        ['IOS-APP-B'], [], [],
                        '6.0.0', '10.6.0', '1.0.0'))
        List<String> expectedPodfileLinesAfterSwap = [
                "target 'IOS-APP' do",
                "end",
                "target 'IOS-APP-B' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "end"]
        assert expectedPodfileLinesAfterSwap == newPodfileLines
    }

    @Test
    void testUpdatePodfileTargets_allPlatformsMultipleProjectsAndTests() {
        List<String> podfileLines = [
                // pod method should not be affected by removal of the old code
                "target 'IOS-APP' do",
                "end",
                "target 'IOS-APPTests' do",
                "end",
                "target 'OSX-APP' do",
                "end",
                "target 'OSX-APPTests' do",
                "end",
                "target 'WATCH-APP' do",
                "end",
                "target 'WATCH-APPTests' do",
                "end"]

        // Update podfile targets
        List<String> newPodfileLines = XcodeTask.updatePodfileTargets(
                podfileLines,
                [new XcodeTask.PodspecDetails('PROJ_A', null, null, ['Debug'], ['Release']),
                 new XcodeTask.PodspecDetails('PROJ_B', null, null, ['Debug'], ['Release'])],
                new XcodeTask.XcodeTargetDetails(
                        ['IOS-APP', 'IOS-APPTests'], ['OSX-APP', 'OSX-APPTests'], ['WATCH-APP', 'WATCH-APPTests'],
                        '6.0.0', '10.6.0', '1.0.0'))

        List<String> expectedPodfileLines = [
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end",
                "target 'IOS-APPTests' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end",
                "target 'OSX-APP' do",
                "    platform :osx, '10.6.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end",
                "target 'OSX-APPTests' do",
                "    platform :osx, '10.6.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end",
                "target 'WATCH-APP' do",
                "    platform :watchos, '1.0.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end",
                "target 'WATCH-APPTests' do",
                "    platform :watchos, '1.0.0'",
                "    j2objc_PROJ_A",
                "    j2objc_PROJ_B",
                "end"]
        assert expectedPodfileLines == newPodfileLines
    }

    @Test
    void testUpdatePodfileTargets_ignoreExistingLines() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                // Existing lines should be ignored
                "    pod 'IGNORE1', :path => 'IGNORE1'",
                "    pod 'IGNORE2', :path => 'IGNORE2'",
                // v0.4.3 upgrade to discard old inlining of pod method
                "    pod 'j2objc-PROJ-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "    pod 'j2objc-PROJ-release', :configuration => ['Release'], :path => '/Users/USERNAME/dev/workspace/shared/build'",
                "end"]

        // Update podfile targets
        List<String> newPodfileLines = XcodeTask.updatePodfileTargets(
                podfileLines,
                podspecDetailsProj,
                xcodeTargetDetailsIosAppOnly)

        List<String> expectedPodfileLines = [
                "target 'IOS-APP' do",
                "    platform :ios, '6.0.0'",
                "    j2objc_PROJ",
                "    pod 'IGNORE1', :path => 'IGNORE1'",
                "    pod 'IGNORE2', :path => 'IGNORE2'",
                "end"]
        assert expectedPodfileLines == newPodfileLines
    }

    // Better error is given in parent call
    @Test(expected = InvalidUserDataException.class)
    void testUpdatePodfileTargets_TargetNotFound() {
        List<String> podfileLines = [
                "target 'IOS-APP' do",
                "end"]

        XcodeTask.updatePodfileTargets(
                podfileLines,
                podspecDetailsProj,
                new XcodeTask.XcodeTargetDetails(
                        ['TARGET-DOES-NOT-EXIST'], [], [],
                        '6.0.0', '10.6.0', '1.0.0'))
    }
}
