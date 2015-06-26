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

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

/**
 * TestTask tests.
 */
public class TestTaskTest {

    private Project proj

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()
    }

    @Test
    public void testGetTestNames_Simple() {

        // These are nonsense paths for files that don't exist
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/SubdirClass.java",
                "${proj.rootDir}/src/test/java/com/example/other/OtherClass.java"])
        Properties noPackagePrefixes = new Properties()

        List<String> testNames = TestTask.getTestNames(proj, srcFiles, noPackagePrefixes)

        List<String> expectedTestNames = [
                "com.example.parent.ParentClass",
                "com.example.parent.subdir.SubdirClass",
                "com.example.other.OtherClass"]

        assert expectedTestNames == testNames
    }

    @Test
    public void testGetTestNames_PackagePrefixes() {
        Properties packagePrefixes = new Properties()
        packagePrefixes.setProperty('com.example.parent', 'PrntPrefix')
        packagePrefixes.setProperty('com.example.parent.subdir', 'SubPrefix')
        packagePrefixes.setProperty('com.example.other', 'OthPrefix')

        // These are nonsense paths for files that don't exist
        FileCollection srcFiles = proj.files([
                "${proj.rootDir}/src/test/java/com/example/parent/ParentOneClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/ParentTwoClass.java",
                "${proj.rootDir}/src/test/java/com/example/parent/subdir/SubdirClass.java",
                "${proj.rootDir}/src/test/java/com/example/other/OtherClass.java",
                "${proj.rootDir}/src/test/java/com/example/noprefix/NoPrefixClass.java"])

        List<String> testNames = TestTask.getTestNames(proj, srcFiles, packagePrefixes)

        List<String> expectedTestNames = [
                "PrntPrefixParentOneClass",
                "PrntPrefixParentTwoClass",
                "SubPrefixSubdirClass",
                "OthPrefixOtherClass",
                // No package prefix in this case
                "com.example.noprefix.NoPrefixClass"]

        assert expectedTestNames == testNames
    }
}
