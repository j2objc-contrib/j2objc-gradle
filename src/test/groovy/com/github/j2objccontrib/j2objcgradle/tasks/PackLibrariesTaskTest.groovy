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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    void setUp() {
        // Mac OS X is the only OS that can run this task
        Utils.setFakeOSMacOSX()
        (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createJ2objcConfig: true
        ))
    }

    @Test
    void testPackLibraries_Windows() {
        Utils.setFakeOSWindows()

        PackLibrariesTask j2objcPackLibraries =
                (PackLibrariesTask) proj.tasks.create(name: 'j2objcPackLibraries', type: PackLibrariesTask) {
                    buildType = 'Debug'
                }

        expectedException.expect(InvalidUserDataException.class)
        expectedException.expectMessage('Mac OS X is required for j2objcPackLibraries task')

        j2objcPackLibraries.packLibraries()
    }

    @Test
    void testPackLibraries() {
        // Expected Activity
        MockProjectExec mockProjectExec = new MockProjectExec(proj, '/J2OBJC_HOME')
        mockProjectExec.demandDeleteAndReturn(
                proj.file("build/packedBinaries/$proj.name-j2objcStaticLibrary/iosDebug").absolutePath)
        mockProjectExec.demandExecAndReturn(
                [
                        'xcrun',
                        'lipo',

                        '-create',
                        '-output', "/PROJECT_DIR/build/packedBinaries/$proj.name-j2objcStaticLibrary/iosDebug/lib$proj.name-j2objc.a",

                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${j2objcConfig.supportedArchs[0]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${j2objcConfig.supportedArchs[1]}Debug/lib$proj.name-j2objc.a",
                        "/PROJECT_DIR/build/binaries/$proj.name-j2objcStaticLibrary/${j2objcConfig.supportedArchs[2]}Debug/lib$proj.name-j2objc.a"
                ])
        assert j2objcConfig.supportedArchs.size() == 3, 'Need to update list of arguments above'

        PackLibrariesTask j2objcPackLibraries =
                (PackLibrariesTask) proj.tasks.create(name: 'j2objcPackLibraries', type: PackLibrariesTask) {
                    buildType = 'Debug'
                }

        j2objcPackLibraries.packLibraries()

        mockProjectExec.verify()
    }
}
