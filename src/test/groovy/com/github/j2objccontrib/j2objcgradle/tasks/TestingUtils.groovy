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
import com.github.j2objccontrib.j2objcgradle.J2objcPlugin
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder

import java.util.concurrent.atomic.AtomicInteger

/**
 * Util methods for testing projects.
 */

// Without access to the project, logging is performed using the
// static 'log' variable added during decoration with this annotation.
@Slf4j
class TestingUtils {

    static AtomicInteger projectIndex = new AtomicInteger()

    static final class ProjectConfig {
        /** to apply the J2objcPlugin */
        boolean applyJ2objcPlugin = false
        /** to apply the JavaPlugin (forced true if applyJ2objcPlugin = true) */
        boolean applyJavaPlugin = false
        /** to create the j2objcConfig extension (forced true if applyJ2objcPlugin = true) */
        boolean createJ2objcConfig = false
        /** to create projectDir/build/reports for writing output */
        boolean createReportsDir = false
        /** the parent project for this project, if any */
        Project rootProject = null
    }

    /**
     * Setup the project for testing purposes
     *
     * Configuration in setup() or individual test:
     * (proj, j2objcHome, j2objcConfig) = TestingUtils.setupProject(new ProjectConfig(...))
     *
     * @return [Project proj, String j2objcHome, J2objcConfig j2objcConfig] multiple assignment
     */
    static Object[] setupProject(ProjectConfig config) {
        // Builds temporary project
        Project proj = ProjectBuilder.builder()
                .withParent(config.rootProject)
                .withName("testProject${projectIndex.getAndIncrement()}")
                .build()
        if (config.rootProject != null) {
            // When using a child project, the project's directory (a subdirectory of the root)
            // is not created automatically.
            assert proj.projectDir.mkdir()
        }

        if (config.applyJavaPlugin || config.applyJ2objcPlugin) {
            // To satisfy Utils.throwIfNoJavaPlugin()
            proj.pluginManager.apply(JavaPlugin)
        }

        // To satisfy Utils.J2objcHome()
        String j2objcHome = File.createTempDir('J2OBJC_HOME', '').path
        File localProperties = proj.file('local.properties')
        localProperties.write("j2objc.home=$j2objcHome\n")

        J2objcConfig j2objcConfig = null
        if (config.applyJ2objcPlugin) {
            proj.pluginManager.apply(J2objcPlugin)
            j2objcConfig = (J2objcConfig) proj.extensions.getByName('j2objcConfig')
        } else if (config.createJ2objcConfig) {
            // can't use the config from the J2objcPlugin:
            j2objcConfig = proj.extensions.create('j2objcConfig', J2objcConfig, proj)
        }

        if (config.createReportsDir) {
            // Can't write @OutputFile in "projectDir/build/reports" unless directory exists
            File reportsDir = new File(proj.buildDir, "reports")
            assert reportsDir.mkdirs()
        }

        return [proj, j2objcHome, j2objcConfig]
    }

    static Set<? extends Task> getTaskDependencies(Project proj, String name) {
        // Strange API requires passing the task as a parameter to getDependencies.
        Task task = proj.tasks.getByName(name)
        return task.taskDependencies.getDependencies(task)
    }
}
