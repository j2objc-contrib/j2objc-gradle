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

package com.github.j2objccontrib.j2objcgradle

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.ConfigureUtil
import org.junit.Before
import org.junit.Test

/**
 * J2objcConfig tests.
 */
@CompileStatic
class J2objcConfigTest {

    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    void testConstructor() {
        J2objcConfig ext = new J2objcConfig(proj)
        String projectDir = proj.projectDir.absolutePath

        assert ext.destSrcMainDir == projectDir + '/build/j2objcOutputs/src/main'
        assert ext.destSrcTestDir == projectDir + '/build/j2objcOutputs/src/test'
        assert ext.destLibDir == projectDir + '/build/j2objcOutputs/lib'
    }

    @Test
    // All variations of main/test and objc/resources
    void testGetDestDirFile_AllVariations() {
        J2objcConfig ext = new J2objcConfig(proj)
        String pDir = proj.projectDir.absolutePath

        assert pDir + '/build/j2objcOutputs/lib' == ext.getDestLibDirFile().absolutePath
        assert pDir + '/build/j2objcOutputs/src/main/objc' == ext.getDestSrcDirFile('main', 'objc').absolutePath
        assert pDir + '/build/j2objcOutputs/src/test/objc' == ext.getDestSrcDirFile('test', 'objc').absolutePath
        assert pDir + '/build/j2objcOutputs/src/main/resources' == ext.getDestSrcDirFile('main', 'resources').absolutePath
        assert pDir + '/build/j2objcOutputs/src/test/resources' == ext.getDestSrcDirFile('test', 'resources').absolutePath
    }

    @Test
    void testFinalConfigure() {
        J2objcConfig ext = new J2objcConfig(proj)

        assert !ext.finalConfigured
        ext.finalConfigure()
        assert ext.finalConfigured
    }

    @Test
    void testCycleFinderArgs_SimpleConfig() {
        J2objcConfig ext = new J2objcConfig(proj)

        ext.cycleFinderArgs.add('')

        assert ext.cycleFinderArgs == new ArrayList<>([''])
    }

    @Test
    @CompileStatic(TypeCheckingMode.SKIP)
    void testTranslateArgs() {
        J2objcConfig ext = new J2objcConfig(proj)

        // To test similarly to how it would be configured:
        // j2objcConfig {
        //      translateArgs '--no-package-directories'
        //      translateArgs '--prefixes', 'prefixes.properties'
        // }
        ConfigureUtil.configure(
                {
                    translateArgs '--no-package-directories'
                    translateArgs '--prefixes', 'prefixes.properties'
                }, ext)

        List<String> expected = new ArrayList<>(
                ['--no-package-directories', '--prefixes', 'prefixes.properties'])
        assert ext.translateArgs == expected
    }

    @Test
    void testconfigureArgs() {
        List<String> args = new ArrayList()
        J2objcConfig.appendArgs(args, 'testArgs', '-arg1', '-arg2')

        List<String> expected = Arrays.asList('-arg1', '-arg2')
        assert expected == args
    }

    @Test(expected = InvalidUserDataException.class)
    void testconfigureArgs_Null() {
        List<String> args = new ArrayList()
        J2objcConfig.appendArgs(args, 'testArgs', null)
    }

    @Test(expected = InvalidUserDataException.class)
    void testconfigureArgs_Spaces() {
        List<String> args = new ArrayList()
        J2objcConfig.appendArgs(args, 'testArgs', '-arg1 -arg2')
    }
}
