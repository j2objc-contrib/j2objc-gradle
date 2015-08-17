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
class AssembleLibrariesTaskTest {

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
    void testAssembleLibraries() {
        // Expected Activity
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandCopyAndReturn({
            includeEmptyDirs = true
            from "$proj.projectDir/build/binaries/$proj.name-j2objcStaticLibrary"
            from "$proj.projectDir/build/packedBinaries/$proj.name-j2objcStaticLibrary"
            into "$proj.projectDir/build/j2objcOutputs/lib"
            include '*Debug/*.a'
        })

        AssembleLibrariesTask j2objcAssembleLibraries =
                (AssembleLibrariesTask) proj.tasks.create(name: 'j2objcAL', type: AssembleLibrariesTask) {
                    buildType = 'Debug'
                    srcLibDir = proj.file("$proj.buildDir/binaries/$proj.name-j2objcStaticLibrary")
                    srcPackedLibDir = proj.file("$proj.buildDir/packedBinaries/$proj.name-j2objcStaticLibrary")
                }

        j2objcAssembleLibraries.assembleLibraries()

        mockProjectExec.verify()
    }
}
