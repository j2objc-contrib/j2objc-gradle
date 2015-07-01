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
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

/**
 * TranslateTask tests.
 */
class TranslateTaskTest {

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
    }

    // TODO: add java source files to the test cases
    // TODO: perhaps even better, point the project towards an existing example

    @Test
    void translate_BasicArguments() {
        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)

        TranslateTask j2objcTranslate = (TranslateTask) proj.tasks.create(
                name: 'j2objcTranslate', type: TranslateTask) {
            srcGenDir = proj.file("${proj.buildDir}/j2objcSrcGen")
        }

        MockProjectExec mockProjectExec = new MockProjectExec(proj, j2objcHome)
        mockProjectExec.demandExec(
                [
                        '/J2OBJC_HOME/j2objc',
                        '-d', '/PROJECT_DIR/build/j2objcSrcGen',
                        '-sourcepath', '/PROJECT_DIR/src/main/java:/PROJECT_DIR/src/test/java',
                        '-classpath', '/J2OBJC_HOME/lib/j2objc_annotations.jar:/J2OBJC_HOME/lib/j2objc_guava.jar:/J2OBJC_HOME/lib/j2objc_junit.jar:/J2OBJC_HOME/lib/jre_emul.jar:/J2OBJC_HOME/lib/javax.inject-1.jar:/J2OBJC_HOME/lib/jsr305-3.0.0.jar:/J2OBJC_HOME/lib/mockito-core-1.9.5.jar:/PROJECT_DIR/build/classes'
                ])

        IncrementalTaskInputs incrementalTaskInputs = new IncrementalTaskInputs() {
            @Override
            boolean isIncremental() { return false }
            @Override
            void outOfDate(Action<? super InputFileDetails> outOfDateAction) { }
            @Override
            void removed(Action<? super InputFileDetails> removedAction) { }
        }

        j2objcTranslate.translate(incrementalTaskInputs)

        mockProjectExec.verify()
    }
}
