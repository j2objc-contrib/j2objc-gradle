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
import org.gradle.api.Project
import org.junit.Before
import org.junit.Test

/**
 * TestTask tests.
 */
class AssembleResourcesTaskTest {

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
    void testAssembleResources_Basic() {
        // Expected Activity
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')

        // Main
        mockProjectExec.demandDeleteAndReturn(
                proj.file('build/j2objcOutputs/src/main/resources').absolutePath)
        mockProjectExec.demandMkDirAndReturn(
                proj.file('build/j2objcOutputs/src/main/resources').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcOutputs/src/main/resources').absolutePath,
                        proj.file('src/main/resources').absolutePath)

        // Test
        mockProjectExec.demandDeleteAndReturn(
                proj.file('build/j2objcOutputs/src/test/resources').absolutePath)
        mockProjectExec.demandMkDirAndReturn(
                proj.file('build/j2objcOutputs/src/test/resources').absolutePath)
        mockProjectExec.demandCopyAndReturn(
                proj.file('build/j2objcOutputs/src/test/resources').absolutePath,
                proj.file('src/test/resources').absolutePath)

        AssembleResourcesTask j2objcAssembleResources =
                (AssembleResourcesTask) proj.tasks.create(name: 'j2objcAR', type: AssembleResourcesTask)

        j2objcAssembleResources.assembleResources()

        mockProjectExec.verify()
    }
}
