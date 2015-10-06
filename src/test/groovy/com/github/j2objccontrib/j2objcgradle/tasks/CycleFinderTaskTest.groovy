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
import org.gradle.process.internal.ExecException
import org.junit.Before
import org.junit.Test

/**
 * CycleFinderTask tests.
 */
class CycleFinderTaskTest {

    private Project proj
    private String j2objcHome
    private J2objcConfig j2objcConfig

    @Before
    void setUp() {
        // Default to native OS except for specific tests
        Utils.setFakeOSNone()
        (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createReportsDir: true,
                createJ2objcConfig: true
        ))
    }

    // TODO: add java source files to the test cases
    // TODO: perhaps even better, point the project towards an existing example

    @Test
    void cycleFinder_Simple_NoFiles_Success() {
        // Expected number of cycles when using simple method
        assert 40 == j2objcConfig.cycleFinderExpectedCycles

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/J2OBJC_HOME/lib/hamcrest-core-1.3.jar:/J2OBJC_HOME/lib/protobuf_runtime.jar:/PROJECT_DIR/build/classes',
                ],
                // expectedWindowsExecutableAndArgs
                [
                        'java',
                        '-jar',
                        '/J2OBJC_HOME/lib/cycle_finder.jar',
                ],
                'IGNORE\n40 CYCLES FOUND\nIGNORE',  // stdout
                null,  // stderr
                new ExecException('Non-Zero Exit'))

        j2objcCycleFinder.cycleFinder()

        mockProjectExec.verify()
    }

    @Test
    void cycleFinder_Windows() {
        Utils.setFakeOSWindows()

        // Expected number of cycles when using simple method
        assert 40 == j2objcConfig.cycleFinderExpectedCycles

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        'INVALID-NEEDS-WINDOWS-SUBSTITUTION',
                        '-sourcepath', '/PROJECT_DIR/src/main/java;/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/J2OBJC_HOME/lib/hamcrest-core-1.3.jar:/J2OBJC_HOME/lib/protobuf_runtime.jar:/PROJECT_DIR/build/classes',
                ],
                // expectedWindowsExecutableAndArgs
                [
                        'java',
                        '-jar',
                        '/J2OBJC_HOME/lib/cycle_finder.jar',
                ],
                'IGNORE\n40 CYCLES FOUND\nIGNORE',  // stdout
                null,  // stderr
                new ExecException('Non-Zero Exit'))

        j2objcCycleFinder.cycleFinder()

        mockProjectExec.verify()
    }

    @Test(expected = InvalidUserDataException.class)
    void cycleFinder_Simple_NoFiles_Failure() {
        assert 40 == j2objcConfig.cycleFinderExpectedCycles

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/J2OBJC_HOME/lib/hamcrest-core-1.3.jar:/J2OBJC_HOME/lib/protobuf_runtime.jar:/PROJECT_DIR/build/classes',
                ],
                // expectedWindowsExecutableAndArgs
                [
                        'java',
                        '-jar',
                        '/J2OBJC_HOME/lib/cycle_finder.jar',
                ],
                // NOTE: '50' cycles instead of expected 40
                '50 CYCLES FOUND',  // stdout
                null,  // stderr
                new ExecException('Non-Zero Exit'))

        try {
            j2objcCycleFinder.cycleFinder()
        } catch (Exception exception) {
            // Catch expected exception, do verifications, then throw again
            mockProjectExec.verify()
            throw exception
        }
    }

    @Test
    void cycleFinder_Advanced_NoFiles_Success() {
        j2objcConfig.translateArgs('--no-package-directories')
        j2objcConfig.cycleFinderExpectedCycles = 0
        j2objcConfig.cycleFinderArgs('--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt')
        j2objcConfig.cycleFinderArgs('--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf')

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/J2OBJC_HOME/lib/hamcrest-core-1.3.jar:/J2OBJC_HOME/lib/protobuf_runtime.jar:/PROJECT_DIR/build/classes',
                        '--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt',
                        '--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
                ],
                // expectedWindowsExecutableAndArgs
                [
                        'java',
                        '-jar',
                        '/J2OBJC_HOME/lib/cycle_finder.jar',
                ],
                '0 CYCLES FOUND',  // stdout
                null,  // stderr
                null)

        j2objcCycleFinder.cycleFinder()

        mockProjectExec.verify()
    }

    @Test(expected = InvalidUserDataException.class)
    void cycleFinder_Advanced_NoFiles_Failure() {
        j2objcConfig.translateArgs('--no-package-directories')
        j2objcConfig.cycleFinderExpectedCycles = 0
        j2objcConfig.cycleFinderArgs('--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt')
        j2objcConfig.cycleFinderArgs('--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf')

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExecAndReturn(
                null,
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/J2OBJC_HOME/lib/hamcrest-core-1.3.jar:/J2OBJC_HOME/lib/protobuf_runtime.jar:/PROJECT_DIR/build/classes',
                        '--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt',
                        '--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
                ],
                // expectedWindowsExecutableAndArgs
                [
                        'java',
                        '-jar',
                        '/J2OBJC_HOME/lib/cycle_finder.jar',
                ],
                'IGNORE\n2 CYCLES FOUND\nIGNORE',  // stdout
                null,  // stderr
                new ExecException('Non-Zero Exit'))

        try {
            j2objcCycleFinder.cycleFinder()
        } catch (Exception exception) {
            // Catch expected exception, do verifications, then throw again
            mockProjectExec.verify()
            throw exception
        }
    }
}
