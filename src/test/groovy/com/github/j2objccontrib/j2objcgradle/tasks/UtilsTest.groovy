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

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

/**
 * Utils tests.
 */
class UtilsTest {

    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    void testIsWindows() {
        // TODO: also test for correctness
        // For now it only tests that the call runs successfully
        Utils.isWindows()
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
    public void testJ2objcHome_LocalProperties() {
        // Write j2objc path to local.properties file within the project
        String j2objcHomeWritten = File.createTempDir('J2OBJC_HOME', '').path
        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHomeWritten\n")

        String j2objcHomeRead = Utils.j2objcHome(proj)
        assert j2objcHomeWritten.equals(j2objcHomeRead)
    }

    @Test
    public void testJ2objcHome_LocalPropertiesWithTrailingSlash() {
        // Write j2objc path to local.properties file within the project
        String j2objcHomePath = File.createTempDir('J2OBJC_HOME', '').path
        String j2objcHomePathWithSlash = j2objcHomePath + '/'
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

        String[] expected = ["${proj.projectDir}/src/main/java"]

        assert Arrays.equals(expected, srcDirsPaths)
    }

    @Test
    void testPrefixProperties_FileOnly() {
        // TODO: fix to use this.getClass().getResource(...) once Android Studio issue fixed
        // https://code.google.com/p/android/issues/detail?id=75991
        File prefixesProp = new File(
                'src/test/resources/com/github/j2objccontrib/j2objcgradle/tasks/prefixes.properties')

        List<String> translateArgs = new ArrayList<String>()
        translateArgs.add("--prefixes ${prefixesProp.absolutePath}")
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
        translateArgs.add("--prefixes ${prefixesProp.absolutePath}")
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
        FileCollection fileCollection = proj.files("file1", "file2", "/absoluteFile")
        String joinedPathArg = Utils.joinedPathArg(fileCollection)

        String expected = "${proj.projectDir}/file1:${proj.projectDir}/file2:/absoluteFile"
        assert expected == joinedPathArg
    }

    // TODO: testFilterJ2objcOutputForErrorLines()

    @Test
    void projectExec_StdOut() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        // This will branch outputs to both the system and internal test OutputStreams
        // If this is useful, it can be extended to other unit tests
        TeeOutputStream stdoutTee = new TeeOutputStream(System.out, stdout)
        TeeOutputStream stderrTee = new TeeOutputStream(System.err, stderr)

        Utils.projectExec(proj, stdout, stderr, null, {
            executable 'echo'
            args 'written-stdout'
            setStandardOutput stdoutTee
            setErrorOutput stderrTee
        })

        // newline is added at end of stdout/stderr
        assert stdout.toString().equals('written-stdout\n')
        assert stderr.toString().isEmpty()
    }

    @Test
    void projectExec_StdErr() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // TODO: get the command to write to stderr rather than faking it
        // Tried to do "args '>/dev/stderr'" but it's passed to echo command rather than shell
        stderr.write('fake-stderr'.getBytes('utf-8'))

        Utils.projectExec(proj, stdout, stderr, null, {
            executable 'echo'
            args 'echo-stdout'
            setStandardOutput stdout
            setErrorOutput stderr
        })

        // newline is added at end of stdout/stderr
        assert stdout.toString().equals('echo-stdout\n')
        assert stderr.toString().equals('fake-stderr')
    }

    // TODO: projectExec_NonZeroExit
    // Needs command line that outputs non-zero result

    @Test
    void projectExec_HelpfulErrorMessage() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        // TODO: get command to write to stderr
        stdout.write('fake-stdout'.getBytes('utf-8'))
        stderr.write('fake-stderr'.getBytes('utf-8'))

        try {
            Utils.projectExec(proj, stdout, stderr, null, {
                executable 'exit'
                args '1'
                setStandardOutput stdout
                setErrorOutput stderr
            })
            assert false, 'Expected Exception'

        } catch (InvalidUserDataException exception) {
            String expected =
                    'org.gradle.api.InvalidUserDataException: Command Line Failed:\n' +
                    'exit 1\n' +
                    'Cause:\n' +
                    "org.gradle.process.internal.ExecException: A problem occurred starting process 'command 'exit''\n" +
                    'Standard Output:\n' +
                    'fake-stdout\n' +
                    'Error Output:\n' +
                    'fake-stderr'
            assert exception.toString().equals(expected)
        }
    }

    @Test
    void projectExec_MatchRegexFailed() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // String has escaped '/' and '\n' to test escaping
        String matchRegexOutputs = /(no\/match\n)/
        try {
            Utils.projectExec(proj, stdout, stderr, matchRegexOutputs, {
                executable 'echo'
                args 'echo-stdout'
                setStandardOutput stdout
                setErrorOutput stderr
            })
            assert false, 'Expected Exception'

        } catch (InvalidUserDataException exception) {
            String expected =
                    'org.gradle.api.InvalidUserDataException: Command Line Succeeded (failure cause listed below):\n' +
                    'echo echo-stdout\n' +
                    'Cause:\n' +
                    'org.gradle.api.InvalidUserDataException: Unable to find expected expected output in stdout or stderr\n' +
                    'Failed Regex Match: /(no\\/match\\n)/\n' +
                    'Standard Output:\n' +
                    'echo-stdout\n' +
                    '\n' +
                    'Error Output:\n'
            assert exception.toString().equals(expected)
        }
    }

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

        assert null != Utils.matchRegexOutputs(stdout, stderr, /(std+out)/).equals('stdout')
        assert null != Utils.matchRegexOutputs(stdout, stderr, /(std+err)/).equals('stderr')
    }

    @Test
    void testMatchRegexOutputStreams_MatchNumber() {
        ByteArrayOutputStream ostream = new ByteArrayOutputStream()
        ostream.write('OK (15 tests)'.getBytes('utf-8'))

        String countStr = Utils.matchRegexOutputs(ostream, ostream, /OK \((\d+) tests?\)/)
        assert 15 == countStr.toInteger()
    }

    @Test
    void testEscapeSlashyString() {
        String regex = /forward-slash:\/,newline:\n,multi-digit:\d+/
        assert "/forward-slash:\\/,newline:\\n,multi-digit:\\d+/" == Utils.escapeSlashyString(regex)
    }

    @Test
    void testLogDebugExecSpecOutput() {
        ExecHandleBuilder execHandleBuilder = new ExecHandleBuilder()
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        stderr.write('written-stdout'.getBytes('utf-8'))
        stdout.write('written-stderr'.getBytes('utf-8'))

        // No validation of results, only that the code runs without error
        Utils.logDebugExecSpecOutput(stdout, stderr, execHandleBuilder)
    }
}
