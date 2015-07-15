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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test;

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
    public void testJ2objcHome_LocalProperties() {
        // Write j2objc path to local.properties file within the project
        String j2objcHomeWritten = File.createTempDir('J2OBJC_HOME', '').path
        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHomeWritten\n")

        String j2objcHomeRead = Utils.j2objcHome(proj)
        assert j2objcHomeWritten == j2objcHomeRead
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
    void testMatchNumberRegex() {
        int count = Utils.matchNumberRegex("15 CYCLES FOUND", /(\d+) CYCLES FOUND/)
        assert count == 15
    }

    @Test(expected = InvalidUserDataException.class)
    void testMatchNumberRegex_NoMatch() {
        int count = Utils.matchNumberRegex("AA CYCLES FOUND", /(\d+) CYCLES FOUND/)
    }

    @Test(expected = InvalidUserDataException.class)
    void testMatchNumberRegex_NotNumber() {
        int count = Utils.matchNumberRegex("AA CYCLES FOUND", /(.*) CYCLES FOUND/)
    }
}
