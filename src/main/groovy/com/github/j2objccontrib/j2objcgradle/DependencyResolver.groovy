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

package com.github.j2objccontrib.j2objcgradle

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * Resolves `j2objcTranslation` and 'j2objcLinkage' dependencies into their `j2objc` constructs.
 */
@PackageScope
@CompileStatic
class DependencyResolver {

    final Project project
    final J2objcConfig j2objcConfig

    DependencyResolver(Project project, J2objcConfig j2objcConfig) {
        this.project = project
        this.j2objcConfig = j2objcConfig
    }

    void configureAll() {
        project.configurations.getByName('j2objcTranslation').each { File it ->
            // These are the resolved files, NOT the dependencies themselves.
            visitTranslateFile(it)
        }
        project.configurations.getByName('j2objcLinkage').dependencies.each {
            visitLink(it)
        }
    }

    protected void visitTranslateFile(File depFile) {
        j2objcConfig.translateSourcepaths(depFile.absolutePath)
        j2objcConfig.enableBuildClosure()
    }

    protected void visitLink(Dependency dep) {
        if (dep instanceof ProjectDependency) {
            visitLinkProjectDependency((ProjectDependency) dep)
        } else if (dep instanceof SelfResolvingDependency) {
            visitLinkSelfResolvingDependency((SelfResolvingDependency) dep)
        } else {
            visitLinkGenericDependency(dep)
        }
    }

    protected void visitLinkSelfResolvingDependency(
            SelfResolvingDependency dep) {
        // TODO: handle native prebuilt libraries as files.
        throw new UnsupportedOperationException("Cannot automatically link J2ObjC dependency: $dep")
    }

    protected void visitLinkProjectDependency(ProjectDependency dep) {
        Project beforeProject = dep.dependencyProject
        // We need to have j2objcConfig on the beforeProject configured first.
        project.evaluationDependsOn beforeProject.path

        if (!beforeProject.plugins.hasPlugin(JavaPlugin)) {
            String message = "$beforeProject is not a Java project.\n" +
                             "dependsOnJ2ObjcLib can only automatically resolve a\n" +
                             "dependency on a Java project also converted using the\n" +
                             "J2ObjC Gradle Plugin."
            throw new InvalidUserDataException(message)
        }

        if (!beforeProject.plugins.hasPlugin(J2objcPlugin)) {
            String message = "$beforeProject does not use the J2ObjC Gradle Plugin.\n" +
                             "dependsOnJ2objcLib can be used only with another project that\n" +
                             "itself uses the J2ObjC Gradle Plugin."
            throw new InvalidUserDataException(message)
        }

        // Build and test the java/objc libraries and the objc headers of
        // the other project first.
        // Since we assert the presence of the J2objcPlugin above,
        // we are guaranteed that the java plugin, which creates the jar task,
        // is also present.
        project.tasks.getByName('j2objcPreBuild').dependsOn {
            return [beforeProject.tasks.getByName('j2objcBuild'),
                    beforeProject.tasks.getByName('jar')]
        }
        AbstractArchiveTask jarTask = beforeProject.tasks.getByName('jar') as AbstractArchiveTask
        project.logger.debug("$project:j2objcTranslate must use ${jarTask.archivePath}")
        j2objcConfig.translateClasspaths += jarTask.archivePath.absolutePath
        j2objcConfig.nativeCompilation.dependsOnJ2objcLib(beforeProject)
    }

    protected void visitLinkGenericDependency(Dependency dep) {
        throw new UnsupportedOperationException("Cannot automatically link J2ObjC dependency: $dep")
    }
}
