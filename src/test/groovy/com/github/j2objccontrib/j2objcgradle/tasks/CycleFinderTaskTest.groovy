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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.process.internal.ExecException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

/**
 * CycleFinderTask tests.
 */
public class CycleFinderTaskTest {

    private Project proj
    private String j2objcHome

    @Before
    void setUp() {
        proj = ProjectBuilder.builder().build()

        // For Utils.throwIfNoJavaPlugin()
        proj.pluginManager.apply(JavaPlugin)

        // For Utils.J2objcHome()
        j2objcHome = File.createTempDir('J2OBJC_HOME', '').path
        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHome\n")

        // For reportFile
        proj.getBuildDir().mkdir()
        File reportDir = new File("${proj.buildDir}/reports")
        reportDir.mkdir()
    }

    // TODO: add java source files to the test cases
    // TODO: perhaps even better, point the project towards an existing example

    @Test
    public void cycleFinderWithExec_SimpleModeSuccess() {

        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        assert 40 == j2objcConfig.cycleFinderExpectedCycles

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExec(
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar',
                ],
                'IGNORE\n40 CYCLES FOUND\nIGNORE',
                null,
                new ExecException('Non-Zero Exit'))

        j2objcCycleFinder.cycleFinderWithExec(mockProjectExec.projectProxyInstance())

        mockProjectExec.verify()
    }

    @Test(expected = IllegalArgumentException.class)
    public void cycleFinderWithExec_SimpleModeFailure() {

        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        assert 40 == j2objcConfig.cycleFinderExpectedCycles

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExec(
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar',
                ],
                // Note the '50' cycles instead of expected 45
                'IGNORE\n50 CYCLES FOUND\nIGNORE',
                null,
                new ExecException('Non-Zero Exit'))

        j2objcCycleFinder.cycleFinderWithExec(mockProjectExec.projectProxyInstance())

        mockProjectExec.verify()
    }

    @Test
    public void cycleFinderWithExec_AdvancedModeSuccess() {

        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.translateArgs('--no-package-directories')
        j2objcConfig.cycleFinderExpectedCycles = 0
        j2objcConfig.cycleFinderArgs('--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt')
        j2objcConfig.cycleFinderArgs('--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf')

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExec(
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar',
                        '--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt',
                        '--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
                ])

        j2objcCycleFinder.cycleFinderWithExec(mockProjectExec.projectProxyInstance())

        mockProjectExec.verify()
    }

    @Test(expected = IllegalArgumentException.class)
    public void cycleFinderWithExec_AdvancedModeFailure() {

        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        j2objcConfig.translateArgs('--no-package-directories')
        j2objcConfig.cycleFinderExpectedCycles = 0
        j2objcConfig.cycleFinderArgs('--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt')
        j2objcConfig.cycleFinderArgs('--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf')

        CycleFinderTask j2objcCycleFinder = (CycleFinderTask) proj.tasks.create(
                name: 'j2objcCycleFinder', type: CycleFinderTask) {
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExec(
                [
                        '/J2OBJC_HOME/cycle_finder',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar',
                        '--whitelist', '/J2OBJC_REPO/jre_emul/cycle_whitelist.txt',
                        '--sourcefilelist', '/J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
                ],
                'IGNORE\n2 CYCLES FOUND\nIGNORE',
                null,
                new ExecException('Non-Zero Exit'))

        j2objcCycleFinder.cycleFinderWithExec(mockProjectExec.projectProxyInstance())

        mockProjectExec.verify()
    }
}
