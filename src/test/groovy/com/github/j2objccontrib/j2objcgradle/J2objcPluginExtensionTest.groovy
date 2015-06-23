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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.ConfigureUtil
import org.junit.Before
import org.junit.Test;

/**
 * J2objcPluginExtension tests.
 */
public class J2objcPluginExtensionTest {

    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    public void testConstructor() {
        J2objcPluginExtension ext = new J2objcPluginExtension(proj)

        assert ext.destSrcDir == proj.buildDir.absolutePath + '/j2objcOutputs/src/main/objc'
        assert ext.destSrcDirTest == proj.buildDir.absolutePath + '/j2objcOutputs/src/test/objc'
        assert ext.destLibDir == proj.buildDir.absolutePath + '/j2objcOutputs/lib'
    }

    @Test
    public void testFinalConfigure() {
        J2objcPluginExtension ext = new J2objcPluginExtension(proj)

        assert !ext.finalConfigured
        ext.finalConfigure()
        assert ext.finalConfigured
    }

    @Test
    public void testCycleFinderArgs_SimpleConfig() {
        J2objcPluginExtension ext = new J2objcPluginExtension(proj)

        ext.cycleFinderArgs.add('')

        assert ext.cycleFinderArgs == new ArrayList<>([''])
    }

    @Test
    public void testTranslateArgs() {
        J2objcPluginExtension ext = new J2objcPluginExtension(proj)

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
    public void testconfigureArgs() {
        List<String> args = new ArrayList()
        J2objcPluginExtension.appendArgs(args, 'testArgs', '-arg1', '-arg2')

        List<String> expected = Arrays.asList('-arg1', '-arg2')
        assert expected == args
    }

    @Test(expected = IllegalArgumentException.class)
    public void testconfigureArgs_Null() {
        List<String> args = new ArrayList()
        J2objcPluginExtension.appendArgs(args, 'testArgs', null)
    }

    @Test(expected = IllegalArgumentException.class)
    public void testconfigureArgs_Spaces() {
        List<String> args = new ArrayList()
        J2objcPluginExtension.appendArgs(args, 'testArgs', '-arg1 -arg2')
    }
}
