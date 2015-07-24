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
import com.github.j2objccontrib.j2objcgradle.NativeCompilation
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
class PackLibrariesTaskTest {

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
    void testPackLibraries() {
        // Expected Activity
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandDeleteAndReturn(
                "$proj.projectDir/build/packedBinaries/$proj.name-j2objcStaticLibrary/iosDebug")
        mockProjectExec.demandExecAndReturn(
                [
                        'xcrun',
                        'lipo',

                        '-create',
                        '-output', "/PROJECT_DIR/build/packedBinaries/$proj.name-j2objcStaticLibrary/iosDebug/lib$proj.name-j2objc.a",

                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${NativeCompilation.ALL_SUPPORTED_ARCHS[0]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${NativeCompilation.ALL_SUPPORTED_ARCHS[1]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${NativeCompilation.ALL_SUPPORTED_ARCHS[2]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${NativeCompilation.ALL_SUPPORTED_ARCHS[3]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${NativeCompilation.ALL_SUPPORTED_ARCHS[4]}Debug/lib$proj.name-j2objc.a"
                ])
        assert NativeCompilation.ALL_SUPPORTED_ARCHS.size() == 5, 'Need to update list of arguments above'

        PackLibrariesTask j2objcPackLibraries =
                (PackLibrariesTask) proj.tasks.create(name: 'j2objcPL', type: PackLibrariesTask) {
                    buildType = 'Debug'
                }

        j2objcPackLibraries.packLibraries()

        mockProjectExec.verify()
    }
}
