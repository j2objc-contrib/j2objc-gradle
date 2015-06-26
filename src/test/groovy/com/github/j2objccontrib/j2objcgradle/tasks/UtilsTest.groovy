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
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test;

/**
 * Utils tests.
 */
public class UtilsTest {

    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    public void testIsWindows() {
        // TODO: also test for correctness
        // For now it only tests that the call runs successfully
        Utils.isWindows()
    }

    @Test(expected = InvalidUserDataException.class)
    public void testThrowIfNoJavaPlugin_NoJavaPlugin() {
        Utils.throwIfNoJavaPlugin(proj)
    }

    // TODO: testThrowIfNoJavaPlugin_JavaPluginExists

    // TODO: testSrcDirs() - requires 'java' plugin

    // TODO: testSourcepathJava() - requires 'java' plugin

    // TODO: testJ2objcHome()

    @Test
    public void testPrefixProperties_FileOnly() {
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
    public void testPrefixProperties_FileAndArgs() {
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
    public void testFilenameCollisionCheck_NoCollisition() {
        FileCollection files = proj.files('DiffOne.java', 'DiffTwo.java')
        Utils.filenameCollisionCheck(files)
    }

    @Test(expected = InvalidUserDataException.class)
    public void testFilenameCollisionCheck_Collision() {
        // Same filename but located in different paths
        FileCollection files = proj.files('dirOne/Same.java', 'dirTwo/Same.java')
        Utils.filenameCollisionCheck(files)
    }

    // TODO: testAddJavaFiles()

    @Test
    public void testAbsolutePathOrEmpty() {
        String path = Utils.absolutePathOrEmpty(proj, new ArrayList<String>(['One/', 'Two/']))

        String absPath = proj.rootDir.absolutePath
        assert path == ":$absPath/One:$absPath/Two".toString()
    }

    @Test
    public void testAbsolutePathOrEmpty_Empty() {
        String path = Utils.absolutePathOrEmpty(proj, new ArrayList<String>())

        assert path == ''
    }

    @Test
    public void testGetClassPathArg() {
        String classPathArg = Utils.getClassPathArg(
                proj, "/J2OBJC_HOME",
                new ArrayList<String>(['LibOne', 'LibTwo']),
                new ArrayList<String>(['J2LibOne', 'J2LibTwo']))

        String absPath = proj.rootDir.absolutePath
        assert classPathArg == "$absPath/LibOne:$absPath/LibTwo:/J2OBJC_HOME/lib/J2LibOne:/J2OBJC_HOME/lib/J2LibTwo".toString()
    }

    // TODO: testFilterJ2objcOutputForErrorLines()

    @Test
    public void testMatchNumberRegex() {
        int count = Utils.matchNumberRegex("15 CYCLES FOUND", /(\d+) CYCLES FOUND/)
        assert count == 15
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMatchNumberRegex_NoMatch() {
        int count = Utils.matchNumberRegex("AA CYCLES FOUND", /(\d+) CYCLES FOUND/)
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMatchNumberRegex_NotNumber() {
        int count = Utils.matchNumberRegex("AA CYCLES FOUND", /(.*) CYCLES FOUND/)
    }
}
