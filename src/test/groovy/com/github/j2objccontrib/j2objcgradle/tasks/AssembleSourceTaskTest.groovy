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
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * TestTask tests.
 */
class AssembleSourceTaskTest {

    private Project proj
    private String j2objcHome
    private J2objcConfig j2objcConfig

    @Before
    void setUp() {
        (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createJ2objcConfig: true
        ))
    }

    @Test
    void testAssembleSource() {
        // Expected Activity
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandDeleteAndReturn(
                "$proj.projectDir/build/j2objcOutputs/src/main/objc")
        mockProjectExec.demandCopyAndReturn({
                includeEmptyDirs = false
                from "$proj.projectDir/build/j2objcSrcGen"
                into "$proj.projectDir/build/j2objcOutputs/src/main/objc"
                exclude "**/*Test.h"
                exclude "**/*Test.m"
        })
        mockProjectExec.demandDeleteAndReturn(
                "$proj.projectDir/build/j2objcOutputs/src/test/objc")
        mockProjectExec.demandCopyAndReturn({
            includeEmptyDirs = false
            from "$proj.projectDir/build/j2objcSrcGen"
            into "$proj.projectDir/build/j2objcOutputs/src/test/objc"
            include "**/*Test.h"
            include "**/*Test.m"
        })

        AssembleSourceTask j2objcAssembleSource =
                (AssembleSourceTask) proj.tasks.create(name: 'j2objcAS', type: AssembleSourceTask) {
                    srcGenDir proj.file('build/j2objcSrcGen')
                }

        j2objcAssembleSource.assembleSource()

        mockProjectExec.verify()
    }
}
