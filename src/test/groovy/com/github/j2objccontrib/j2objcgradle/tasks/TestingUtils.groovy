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
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder

/**
 * Util methods for testing projects.
 */

// Without access to the project, logging is performed using the
// static 'log' variable added during decoration with this annotation.
@Slf4j
class TestingUtils {

    /**
     * Setup the project for testing purposes
     *
     * Configuration in setup() or individual test:
     * (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(true)
     *
     * @param createReportsDir to create projectDir/build/reports for writing output
     * @return [Project proj, String j2objcHome, J2objcConfig j2objcConfig] multiple assignment
     */
    static Object[] setupProject(boolean createReportsDir) {
        // Builds temporary project
        Project proj = ProjectBuilder.builder().build()

        // To satisfy Utils.throwIfNoJavaPlugin()
        proj.pluginManager.apply(JavaPlugin)

        // To satisfy Utils.J2objcHome()
        String j2objcHome = File.createTempDir('J2OBJC_HOME', '').path
        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHome\n")

        // For configuration of plugin inputs
        J2objcConfig j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)

        if (createReportsDir) {
            // Can't write @OutputFile in "projectDir/build/reports" unless directory exists
            File reportsDir = new File(proj.buildDir, "reports")
            assert reportsDir.mkdirs()
        }

        return [proj, j2objcHome, j2objcConfig]
    }
}
