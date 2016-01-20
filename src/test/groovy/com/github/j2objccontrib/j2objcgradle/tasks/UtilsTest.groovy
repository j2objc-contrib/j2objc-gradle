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

import groovy.transform.CompileStatic
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.process.ExecSpec
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.GradleVersion
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.testng.Assert

/**
 * Utils tests.
 */
@CompileStatic
class UtilsTest {

    private Project proj

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    void setUp() {
        // Default to native OS except for specific tests
        Utils.setFakeOSNone()
        proj = ProjectBuilder.builder().build()
    }

    @After
    void tearDown() {
        Utils.setFakeOSNone()
    }

    @Test
    void testCheckGradleVersion_valid() {
        assert !Utils.checkGradleVersion(GradleVersion.version('2.4'), false)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.4.1'), false)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.5'), false)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.8'), false)
    }

    @Test
    void testCheckGradleVersion_validAndThrowIfUnsupported() {
        assert !Utils.checkGradleVersion(GradleVersion.version('2.4'), true)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.4.1'), true)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.5'), true)
        assert !Utils.checkGradleVersion(GradleVersion.version('2.8'), true)
    }

    @Test
    void testCheckGradleVersion_invalid() {
        assert Utils.checkGradleVersion(GradleVersion.version('2.3'), false)
        assert Utils.checkGradleVersion(GradleVersion.version('2.9'), false)
    }

    @Test(expected=InvalidUserDataException)
    void testCheckGradleVersion_invalidBelowMinimum() {
        Utils.checkGradleVersion(GradleVersion.version('2.3'), true)
    }

    @Test(expected=InvalidUserDataException)
    void testCheckGradleVersion_invalidAboveMaximum() {
        Utils.checkGradleVersion(GradleVersion.version('2.9'), true)
    }

    @Test
    void testGetLowerCaseOSName() {
        // Redundant method call but included for clarity
        Utils.setFakeOSNone()
        String realOS = Utils.getLowerCaseOSName(false)
        assert realOS == Utils.getLowerCaseOSName(true)

        // Fake OS must be different from current OS
        if (Utils.isMacOSX()) {
            Utils.setFakeOSWindows()
        } else {
            Utils.setFakeOSMacOSX()
        }

        // OS should now change
        assert realOS != Utils.getLowerCaseOSName(false)
        // Ignoring fakeOS still gets realOS
        assert realOS == Utils.getLowerCaseOSName(true)
    }

    @Test
    void test_NativeOS() {
        // OS should be identified distinctively
        Utils.setFakeOSNone()
        // Wierd syntax is so that CompileStatic doesn't complain converting boolean to integer
        assert 1 == (Utils.isLinux() ? 1 : 0) + (Utils.isMacOSX() ? 1 : 0) + (Utils.isWindows() ? 1 : 0)
    }

    @Test
    void testSetFakeOSLinux() {
        Utils.setFakeOSLinux()
        assert Utils.isLinux()
        assert !Utils.isMacOSX()
        assert !Utils.isWindows()
    }

    @Test
    void testSetFakeOSMacOSX() {
        Utils.setFakeOSMacOSX()
        assert !Utils.isLinux()
        assert Utils.isMacOSX()
        assert !Utils.isWindows()
    }

    @Test
    void testSetFakeOSWindows() {
        Utils.setFakeOSWindows()
        assert !Utils.isLinux()
        assert !Utils.isMacOSX()
        assert Utils.isWindows()
    }

    @Test
    void testRequireMacOSX_Linux() {
        Utils.setFakeOSLinux()

        expectedException.expect(InvalidUserDataException)
        expectedException.expectMessage('Mac OS X is required for taskName')

        Utils.requireMacOSX('taskName')
    }

    @Test
    void testRequireMacOSX_MacOSX() {
        Utils.setFakeOSMacOSX()
        Utils.requireMacOSX('taskName')
        // NOTE: no exception thrown
    }

    @Test
    void testRequireMacOSX_Windows() {
        Utils.setFakeOSWindows()

        expectedException.expect(InvalidUserDataException)
        expectedException.expectMessage('Mac OS X is required for taskName')

        Utils.requireMacOSX('taskName')
    }

    @Test
    void testSeparatorChar_Linux() {
        Utils.setFakeOSLinux()
        assert '/' == Utils.fileSeparator()
    }

    @Test
    void testSeparatorChar_MacOSX() {
        Utils.setFakeOSMacOSX()
        assert '/' == Utils.fileSeparator()
    }

    @Test
    void testSeparatorChar_Windows() {
        Utils.setFakeOSWindows()
        assert '\\' == Utils.fileSeparator()
    }

    @Test
    void testPathSeparator_Linux() {
        Utils.setFakeOSLinux()
        assert ':' == Utils.pathSeparator()
    }

    @Test
    void testPathSeparator_MacOSX() {
        Utils.setFakeOSMacOSX()
        assert ':' == Utils.pathSeparator()
    }

    @Test
    void testPathSeparator_Windows() {
        Utils.setFakeOSWindows()
        assert ';' == Utils.pathSeparator()
    }

    @Test
    void testRelativizeNonParent_Parent() {
        File parent = new File('/A/B/C')
        File child = new File('/A/B/C/D/E/file')

        URI relative = parent.toURI().relativize(child.toURI())
        assert relative.toString() == Utils.relativizeNonParent(parent, child)
        assert 'D/E/file' == Utils.relativizeNonParent(parent, child)
    }

    @Test
    void testRelativizeNonParent_NonParent() {
        File src = new File('/A/B')
        File dst = new File('/A/C')
        assert '../C' == Utils.relativizeNonParent(src, dst)

        File src2 = new File('/A/B1/B2')
        File dst2 = new File('/A/C1/C2')
        assert '../../C1/C2' == Utils.relativizeNonParent(src2, dst2)
    }

    @Test
    void testRelativizeNonParent_Same() {
        File dir = new File('/A/B/C')
        assert '' == Utils.relativizeNonParent(dir, dir)
    }

    @Test
    void testRelativizeNonParent_Root() {
        File src = new File('/A/B/C')
        File dst = new File('/ab/bb/cb')
        assert '../../../ab/bb/cb' == Utils.relativizeNonParent(src, dst)
    }

    @Test(expected = InvalidUserDataException.class)
    void testThrowIfNoJavaPlugin_NoJavaPlugin() {
        Utils.throwIfNoJavaPlugin(proj)
    }

    @Test
    void testThrowIfNoJavaPlugin_JavaPlugin() {
        proj.pluginManager.apply(JavaPlugin)
        // Should not throw any exception
        Utils.throwIfNoJavaPlugin(proj)
    }

    @Test
    public void testGetLocalProperty_FileNotPresent() {
        String value = Utils.getLocalProperty(proj, 'debug.enabled')
        assert value == null

        value = Utils.getLocalProperty(proj, 'debug.enabled', 'true')
        assert value == 'true'
    }

    @Test
    public void testGetLocalProperty_KeyNotPresent() {
        File localProperties = proj.file('local.properties')
        localProperties.write('j2objc.release.enabled=false\n')

        String value = Utils.getLocalProperty(proj, 'debug.enabled')
        assert value == null

        value = Utils.getLocalProperty(proj, 'debug.enabled', 'false')
        assert value == 'false'
    }

    @Test
    public void testGetLocalProperty_KeyPresent() {
        File localProperties = proj.file('local.properties')
        localProperties.write('j2objc.debug.enabled=false\n')

        String value = Utils.getLocalProperty(proj, 'debug.enabled')
        assert value == 'false'

        value = Utils.getLocalProperty(proj, 'debug.enabled', 'true')
        assert value == 'false'
    }

    @Test
    public void testGetLocalProperty_RequestInvalidKey() {
        File localProperties = proj.file('local.properties')
        localProperties.write('#IGNORE')

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Requesting invalid property: requested-invalid-key')
        // Check list of valid keys is suggested by checking for a single entry:
        expectedException.expectMessage('debug.enabled')

        Utils.getLocalProperty(proj, 'requested-invalid-key')
    }

    @Test
    public void testGetLocalProperty_LocalPropertiesInvalidKey() {
        File localProperties = proj.file('local.properties')
        localProperties.write('j2objc.written-invalid-key')

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Invalid J2ObjC Gradle Plugin property: j2objc.written-invalid-key')
        expectedException.expectMessage("From local.properties: ${proj.file('local.properties')}")
        // Check list of valid keys is suggested by checking for a single entry:
        expectedException.expectMessage('debug.enabled')

        // Request a valid key
        Utils.getLocalProperty(proj, 'debug.enabled')
    }

    @Test
    public void testJ2objcHome_LocalProperties() {
        // Write j2objc path to local.properties file within the project
        String j2objcHome = File.createTempDir('J2OBJC_HOME', '').path
        // Backslashes on Windows are silently dropped when loading properties:
        j2objcHome = j2objcHome.replace('\\', '/')

        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHome\n")

        String j2objcHomeRead = Utils.j2objcHome(proj)
        assert j2objcHome.equals(TestingUtils.windowsToForwardSlash(j2objcHomeRead))
    }

    @Test
    public void testJ2objcHome_LocalPropertiesWithTrailingSlash() {
        // Write j2objc path to local.properties file within the project
        String j2objcHomePath = File.createTempDir('J2OBJC_HOME', '').absolutePath
        // Backslashes on Windows are silently dropped when loading properties:
        String j2objcHomePathWithSlash = (j2objcHomePath + '/').replace('\\', '/')

        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHomePathWithSlash\n")

        String j2objcHomeRead = Utils.j2objcHome(proj)
        assert j2objcHomePath.equals(j2objcHomeRead)
    }

    // TODO: testJ2objcHome_EnvironmentVariable

    @Test
    public void testSrcSet_NoSrcFiles() {
        // To avoid triggering Utils.throwIfNoJavaPlugin()
        proj.pluginManager.apply(JavaPlugin)
        SourceDirectorySet srcSet = Utils.srcSet(proj, 'main', 'java')

        assert 0 == srcSet.size()
    }

    // TODO: testSrcSet_SomeSrcFiles()

    @Test
    public void testSrcSet_GetSrcDirs() {
        proj.pluginManager.apply(JavaPlugin)
        SourceDirectorySet srcSet = Utils.srcSet(proj, 'main', 'java')
        String[] srcDirsPaths = srcSet.getSrcDirs().collect { File file ->
            return file.path
        }

        String[] expected = [proj.file('src/main/java').absolutePath]

        assert Arrays.equals(expected, srcDirsPaths)
    }

    @Test
    void testPrefixProperties_FileOnly() {
        // TODO: fix to use this.getClass().getResource(...) once Android Studio issue fixed
        // https://code.google.com/p/android/issues/detail?id=75991
        File prefixesProp = new File(
                'src/test/resources/com/github/j2objccontrib/j2objcgradle/tasks/prefixes.properties')

        List<String> translateArgs = new ArrayList<String>()
        translateArgs.add('--prefixes ' + prefixesProp.absolutePath)
        Properties properties = Utils.packagePrefixes(proj, translateArgs)

        Properties expected = new Properties()
        expected.setProperty('com.example.parent', 'ParentPrefixesFile')
        expected.setProperty('com.example.parent.subdir', 'SubdirPrefixesFile')
        expected.setProperty('com.example.other', 'OtherPrefixesFile')

        assert properties == expected
    }

    @Test
    void testPrefixProperties_FileAndArgs() {
        // TODO: repeat as above
        File prefixesProp = new File(
                'src/test/resources/com/github/j2objccontrib/j2objcgradle/tasks/prefixes.properties')

        List<String> translateArgs = new ArrayList<String>()
        // prefix is overwritten by prefixes.properties
        translateArgs.add('--prefix com.example.parent=ParentPrefixArg')
        translateArgs.add('--prefixes ' + prefixesProp.absolutePath)
        // prefix overwrites prefixes.properties
        translateArgs.add('--prefix com.example.parent.subdir=SubdirPrefixArg')

        Properties properties = Utils.packagePrefixes(proj, translateArgs)

        Properties expected = new Properties()
        expected.setProperty('com.example.parent', 'ParentPrefixesFile')
        expected.setProperty('com.example.parent.subdir', 'SubdirPrefixArg')
        expected.setProperty('com.example.other', 'OtherPrefixesFile')

        assert properties == expected
    }

    @Test
    void testFilenameCollisionCheck_NoCollisition() {
        FileCollection files = proj.files('DiffOne.java', 'DiffTwo.java')
        Utils.filenameCollisionCheck(files)
    }

    @Test(expected = InvalidUserDataException.class)
    void testFilenameCollisionCheck_Collision() {
        // Same filename but located in different paths
        FileCollection files = proj.files('dirOne/Same.java', 'dirTwo/Same.java')
        Utils.filenameCollisionCheck(files)
    }

    // TODO testAddJavaTrees() - needs nested folder of java source files for fileTree(...) operation

    @Test
    void testJ2objcLibs() {
        List<String> j2objcLibPaths = Utils.j2objcLibs('/J2OBJC_HOME', ['J2LibOne', 'J2LibTwo'])
        List<String> expected = ['/J2OBJC_HOME/lib/J2LibOne', '/J2OBJC_HOME/lib/J2LibTwo']
        assert expected == j2objcLibPaths
    }

    @Test
    public void testJoinedPathArg() {
        String absolutePath = TestingUtils.windowsNoFakeAbsolutePath(File.separator + 'absoluteFile')
        FileCollection fileCollection =
                proj.files(
                        'relative_file1',
                        'relative_file2',
                        absolutePath)
        String joinedPathArg = Utils.joinedPathArg(fileCollection)

        String expected =
                proj.file('relative_file1').absolutePath + File.pathSeparator +
                proj.file('relative_file2').absolutePath + File.pathSeparator +
                absolutePath
        assert expected == joinedPathArg
    }

    // TODO: testFilterJ2objcOutputForErrorLines()

    @Test
    void testMatchRegexOutputStreams_Fails() {
        ByteArrayOutputStream ostream = new ByteArrayOutputStream()
        ostream.write('written'.getBytes('utf-8'))

        assert null == Utils.matchRegexOutputs(ostream, ostream, /(NoMatch)/)
    }

    @Test
    void testMatchRegexOutputStreams_MatchString() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        stdout.write('written-stdout'.getBytes('utf-8'))
        stderr.write('written-stderr'.getBytes('utf-8'))

        // Match each regex against both OutputStreams
        assert 'stdout' == Utils.matchRegexOutputs(stdout, stderr, /(std+out)/)
        assert 'stderr' == Utils.matchRegexOutputs(stdout, stderr, /(std+err)/)
    }

    @Test
    void testMatchRegexOutputStreams_MatchNumber() {
        ByteArrayOutputStream ostream = new ByteArrayOutputStream()
        ostream.write('OK (15 tests)'.getBytes('utf-8'))

        String countStr = Utils.matchRegexOutputs(ostream, ostream, /OK \((\d+) tests?\)/)
        assert 15 == countStr.toInteger()
    }

    @Test
    void testTrimTrailingForwardSlash() {
        assert '/path/dir' == Utils.trimTrailingForwardSlash('/path/dir')
        assert '/path/dir' == Utils.trimTrailingForwardSlash('/path/dir/')
    }

    @Test
    void testEscapeSlashyString() {
        String regex = /forward-slash:\/, newline:\n, multi-digit:\d+/
        assert "/forward-slash:\\/, newline:\\n, multi-digit:\\d+/" == Utils.escapeSlashyString(regex)
    }

    @Test
    void testGreatestCommonPrefix() {
        assert "abc" == Utils.greatestCommonPrefix("abc", "abcd")
        assert "ab" == Utils.greatestCommonPrefix("abba", "abcd")
        assert "j2objc-PROJECT-" == Utils.greatestCommonPrefix("j2objc-PROJECT-debug", "j2objc-PROJECT-release")
    }

    @Test
    void testToQuotedString() {
        assert "'a','b','c'" == Utils.toQuotedList(['a', 'b', 'c'])
        assert "'abc'" == Utils.toQuotedList(['abc'])
        assert "''" == Utils.toQuotedList([''])
        assert "" == Utils.toQuotedList([])
    }

    @Test
    void testProjectExecLog() {
        ExecSpec execSpec = new ExecHandleBuilder()
        execSpec.setExecutable('/EXECUTABLE')
        execSpec.args('ARG_1')
        execSpec.args('ARG_2')
        execSpec.args('ARG_3', 'ARG_4')

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        stdout.write('written-stdout'.getBytes('utf-8'))
        stderr.write('written-stderr'.getBytes('utf-8'))

        // Command Succeeded
        String nativeWorkingDir = '/WORKING_DIR'
        if (Utils.isWindowsNoFake()) {
            nativeWorkingDir = 'C:\\WORKING_DIR'
        }
        execSpec.setWorkingDir(nativeWorkingDir)

        String execLogSuccess = Utils.projectExecLog(execSpec, stdout, stderr, true, null)
        String expectedLogSuccess =
                'Command Line Succeeded:\n' +
                '/EXECUTABLE ARG_1 ARG_2 ARG_3 ARG_4\n' +
                'Working Dir:\n' +
                nativeWorkingDir + '\n' +
                'Standard Output:\n' +
                'written-stdout\n' +
                'Error Output:\n' +
                'written-stderr'
        assert expectedLogSuccess.equals(execLogSuccess)

        // Command Failed
        // Normally this should be a distinct test but this avoid duplicating the setup code
        Exception cause = new InvalidUserDataException("I'm the cause of it all!")
        String execLogFailure = Utils.projectExecLog(execSpec, stdout, stderr, false, cause)
        String expectedLogFailure =
                'Command Line Failed:\n' +
                '/EXECUTABLE ARG_1 ARG_2 ARG_3 ARG_4\n' +
                'Working Dir:\n' +
                nativeWorkingDir + '\n' +
                'Cause:\n' +
                'org.gradle.api.InvalidUserDataException: I\'m the cause of it all!\n' +
                'Standard Output:\n' +
                'written-stdout\n' +
                'Error Output:\n' +
                'written-stderr'
        assert expectedLogFailure.equals(execLogFailure)
    }

    @Test
    void testSyncResourcesTo() {
        proj.pluginManager.apply(JavaPlugin)
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandDeleteAndReturn(
                proj.file('SYNC_DIR').absolutePath)
        mockProjectExec.demandMkDirAndReturn(
                proj.file('SYNC_DIR').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('SYNC_DIR').absolutePath,
                proj.file('src/main/resources').absolutePath,
                proj.file('src/test/resources').absolutePath)

        Utils.syncResourcesTo(proj, ['main', 'test'], proj.file('SYNC_DIR'))

        mockProjectExec.verify()
    }

    @Test
    // Tests intercepting and verifying call to project.copy(...)
    void testProjectCopy() {

        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandCopyAndReturn(
                '/DEST-DIR',
                '/INPUT-DIR-1', '/INPUT-DIR-2')

        Utils.projectCopy(proj, {
            from '/INPUT-DIR-1', '/INPUT-DIR-2'
            into '/DEST-DIR'
        })

        mockProjectExec.verify()
    }

    @Test
    // Tests intercepting and verifying call to project.exec(...)
    void testProjectCopy_TwoCalls() {
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandCopyAndReturn(
                '/DEST-1',
                '/INPUT-1A', '/INPUT-1B')
        mockProjectExec.demandCopyAndReturn(
                '/DEST-2',
                '/INPUT-2A', '/INPUT-2B')

        Utils.projectCopy(proj, {
            into '/DEST-1'
            from '/INPUT-1A', '/INPUT-1B'
        })
        Utils.projectCopy(proj, {
            into '/DEST-2'
            from '/INPUT-2A', '/INPUT-2B'
        })

        mockProjectExec.verify()
    }

    @Test
    // Tests intercepting and verifying call to project.delete(path1, path2)
    void testProjectDelete() {
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')

        mockProjectExec.demandDeleteAndReturn('/PATH1', '/PATH2')

        Utils.projectDelete(proj, '/PATH1', '/PATH2')

        mockProjectExec.verify()
    }

    @Test
    void testProjectExec_StdOut() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        // This will branch outputs to both the system and internal test OutputStreams
        // If this is useful, it can be extended to other unit tests
        TeeOutputStream stdoutTee = new TeeOutputStream(System.out, stdout)
        TeeOutputStream stderrTee = new TeeOutputStream(System.err, stderr)

        String executableIn = 'echo'
        List<String> argsIn = ['written-stdout']
        String stdoutExpected = 'written-stdout\n'
        // Windows command execution requires special care
        // See: https://github.com/j2objc-contrib/j2objc-gradle/issues/422
        // SO Help: http://stackoverflow.com/questions/515309/what-does-cmd-c-mean
        if (Utils.isWindowsNoFake()) {
            executableIn = 'cmd'
            argsIn = ['/C', 'echo', 'written-stdout']
            // Windows has carriage return and newline together
            stdoutExpected = 'written-stdout\r\n'
        }

        Utils.projectExec(proj, stdout, stderr, null, {
            executable executableIn
            args argsIn
            setStandardOutput stdoutTee
            setErrorOutput stderrTee
        })

        String stdoutActual = stdout.toString()
        // URLEncoder make debugging easier by showing carriage returns and newlines
        assert URLEncoder.encode(stdoutExpected, 'UTF-8').equals(URLEncoder.encode(stdoutActual, 'UTF-8'))
        assert stderr.toString().isEmpty()
    }

    @Test
    void testProjectExec_StdErr() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // TODO: get the command to write to stderr rather than faking it
        // Tried to do "args '>/dev/stderr'" but it's passed to echo command rather than shell
        stderr.write('fake-stderr'.getBytes('utf-8'))

        String executableIn = 'echo'
        List<String> argsIn = ['written-stdout']
        String stdoutExpected = 'written-stdout\n'
        String stderrExpected = 'fake-stderr'
        if (Utils.isWindowsNoFake()) {
            executableIn = 'cmd'
            argsIn = ['/C', 'echo', 'written-stdout']
            stdoutExpected = 'written-stdout\r\n'
        }

        Utils.projectExec(proj, stdout, stderr, null, {
            executable executableIn
            args argsIn
            setStandardOutput stdout
            setErrorOutput stderr
        })

        String stdoutActual = stdout.toString()
        String stderrActual = stderr.toString()
        // URLEncoder make debugging easier by showing carriage returns and newlines
        assert URLEncoder.encode(stdoutExpected, 'UTF-8').equals(URLEncoder.encode(stdoutActual, 'UTF-8'))
        assert URLEncoder.encode(stderrExpected, 'UTF-8').equals(URLEncoder.encode(stderrActual, 'UTF-8'))
    }

    // TODO: projectExec_NonZeroExit
    // Needs command line that outputs non-zero result

    @Test
    void testProjectExec_HelpfulErrorMessage() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // TODO: get executable passed to projectExec to write to stderr
        stdout.write('written-stdout'.getBytes('utf-8'))
        stderr.write('written-stderr'.getBytes('utf-8'))

        String executableIn = 'exit'
        List<String> argsIn = ['1']
        String expectedCmdLine = 'exit 1'
        String expectedExecExceptionMsg = "A problem occurred starting process 'command 'exit''"
        if (Utils.isWindowsNoFake()) {
            executableIn = 'cmd'
            argsIn = ['/C', 'exit', '1']
            expectedCmdLine = 'cmd /C exit 1'
            expectedExecExceptionMsg = "Process 'command 'cmd'' finished with non-zero exit value 1"
        }

        try {
            Utils.projectExec(proj, stdout, stderr, null, {
                executable executableIn
                args argsIn
                setStandardOutput stdout
                setErrorOutput stderr
            })
            assert false, 'Expected Exception'

        } catch (InvalidUserDataException exception) {
            String expected =
                    'org.gradle.api.InvalidUserDataException: Command Line Failed:\n' +
                    expectedCmdLine + '\n' +
                    'Working Dir:\n' +
                    proj.projectDir.absolutePath + '\n' +
                    'Cause:\n' +
                    "org.gradle.process.internal.ExecException: $expectedExecExceptionMsg\n" +
                    'Standard Output:\n' +
                    'written-stdout\n' +
                    'Error Output:\n' +
                    'written-stderr'
            String actual = exception.toString()
            assert expected.equals(actual)
        }
    }

    @Test
    void testProjectExec_MatchRegexFailed() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        String executableIn = 'echo'
        List<String> argsIn = ['written-stdout']
        String expectedCmdLine = 'echo written-stdout'
        if (Utils.isWindowsNoFake()) {
            executableIn = 'cmd'
            argsIn = ['/C', 'echo', 'written-stdout']
            expectedCmdLine = 'cmd /C echo written-stdout'
        }

        // String has escaped '/' and '\n' to test escaping
        String matchRegexOutputs = /(no\/match\n)/
        try {
            Utils.projectExec(proj, stdout, stderr, matchRegexOutputs, {
                executable executableIn
                args argsIn
                setStandardOutput stdout
                setErrorOutput stderr
            })
            assert false, 'Expected Exception'

        } catch (InvalidUserDataException exception) {
            String expected =
                    'org.gradle.api.InvalidUserDataException: Command Line Succeeded:\n' +
                    expectedCmdLine + '\n' +
                    'Working Dir:\n' +
                    proj.projectDir.absolutePath + '\n' +
                    'Cause:\n' +
                    'org.gradle.api.InvalidUserDataException: Unable to find expected expected output in stdout or stderr\n' +
                    'Failed Regex Match: /(no\\/match\\n)/\n' +
                    'Standard Output:\n' +
                    'written-stdout\n' +
                    '\n' +
                    'Error Output:\n'
            String actual = exception.toString()
            // Remove carriage returns as they complicate comparison on Windows
            if (Utils.isWindowsNoFake()) {
                actual = actual.replace('\r', '')
            }
            assert expected.equals(actual)
        }
    }

    @Test
    void testProjectExec_ThrowException() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        'echo',
                        'written-stdout',
                ],
                null,
                'written-stderr-INSTEAD-OF-STDOUT',
                new InvalidUserDataException('Faked-Exception'))

        try {
            Utils.projectExec(proj, stdout, stderr, null, {
                executable 'echo'
                args 'written-stdout'
                setStandardOutput stdout
                setErrorOutput stderr
            })
            assert false
        } catch (InvalidUserDataException exception) {
            assert exception.toString().contains('Faked-Exception')
        }

        mockProjectExec.verify()

        // Verifies that 'echo written-stdout' didn't reach the output streams
        assert '' == stdout.toString()
        assert 'written-stderr-INSTEAD-OF-STDOUT' == stderr.toString()
    }

    @Test
    // Common sequence is delete destDir, fill with required files, then execute
    void testProjectDeleteCopyCallSequence() {
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')

        mockProjectExec.demandDeleteAndReturn('/DELETE-1', '/DELETE-2')
        mockProjectExec.demandCopyAndReturn('/COPY-DEST', '/COPY-SRC-1', '/COPY-SRC-2')
        mockProjectExec.demandExecAndReturn(['echo', 'EXEC-CALL'])

        Utils.projectDelete(proj, '/DELETE-1', '/DELETE-2')
        Utils.projectCopy(proj, {
            into '/COPY-DEST'
            from '/COPY-SRC-1', '/COPY-SRC-2'
        })
        Utils.projectExec(proj, null, null, null, {
            executable 'echo'
            args 'EXEC-CALL'
        })

        mockProjectExec.verify()
    }

    @Test
    void testParseVersionComponents() {
        Assert.assertEquals(Utils.parseVersionComponents("0.9.8.2.1"),
                [0, 9, 8, 2, 1])
        Assert.assertEquals(Utils.parseVersionComponents("10.9.8.1"),
                [10, 9, 8, 1])
        Assert.assertEquals(Utils.parseVersionComponents("10.1-SNAPSHOT"),
                [10, Integer.MAX_VALUE])
    }

    @Test
    void testIsAtLeastVersion() {
        assert Utils.isAtLeastVersion("0.9.8.2.1", "0.9.8.2.1")
        assert !Utils.isAtLeastVersion("0.9.8.2", "0.9.8.2.1")
        assert Utils.isAtLeastVersion("0.9.8.2.1", "0.9.8.2")
        assert Utils.isAtLeastVersion("0.9.8.3", "0.9.8.2.1")
        assert Utils.isAtLeastVersion("1", "0.9.8.2.1")
        assert Utils.isAtLeastVersion("1-SNAPSHOT", "0.9.8.2.1")
        assert Utils.isAtLeastVersion("0.9.8.2.1-SNAPSHOT", "0.9.8.2.1")
        assert !Utils.isAtLeastVersion("0.9.8.1.2", "0.9.8.2.1")
        assert !Utils.isAtLeastVersion("0.7.9", "0.9.8.2.1")
    }

    @Test
    void testMaxArgs_Linux() {
        Utils.setFakeOSLinux()
        assert Integer.MAX_VALUE == Utils.maxArgs()
    }

    @Test
    void testMaxArgs_OSX() {
        Utils.setFakeOSMacOSX()
        assert 262144 == Utils.maxArgs()
    }

    @Test
    void testMaxArgs_Windows() {
        Utils.setFakeOSWindows()
        assert 8191 == Utils.maxArgs()
    }
}
