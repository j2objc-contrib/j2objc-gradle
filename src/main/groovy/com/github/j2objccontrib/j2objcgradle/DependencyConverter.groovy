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
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency

/**
 * Converts `[test]compile` dependencies to their
 * `j2objcTranslationClosure` and/or `j2objc[Test]Linkage` equivalents, depending on the type
 * of dependency and whether or not they are already provided in native code:
 * <p/>
 * External classfile .jar libraries you depend on via `compile` will be
 * converted to `j2objcTranslationClosure` dependencies of the corresponding source .jar libraries.
 * Gradle projects you depend on via `compile` or `testCompile` will be converted to
 * `j2objcLinkage` or `j2objcTestLinkage` dependencies of the corresponding generated Objective C include headers
 * and static library.  See {@link DependencyResolver} for details on the differences
 * between these configurations.
 * <p/>
 * They will be resolved to appropriate `j2objc` constructs using DependencyResolver.
 */
@PackageScope
@CompileStatic
class DependencyConverter {

    final Project project
    final J2objcConfig j2objcConfig

    // List of `group:name`
    // TODO: Handle versioning.
    static final List<String> J2OBJC_DEFAULT_LIBS = [
            'com.google.guava:guava',
            'junit:junit',
            'org.mockito:mockito-core',
            'com.google.j2objc:j2objc-annotations',
            'org.hamcrest:hamcrest-core',
            'com.google.protobuf:protobuf-java']

    DependencyConverter(Project project, J2objcConfig j2objcConfig) {
        this.project = project
        this.j2objcConfig = j2objcConfig
    }

    void configureAll() {
        project.configurations.getByName('compile').dependencies.each {
            visit(it, false)
        }
        project.configurations.getByName('testCompile').dependencies.each {
            visit(it, true)
        }
    }

    protected void visit(Dependency dep, boolean isTest) {
        if (dep instanceof ProjectDependency) {
            // ex. `compile project(':peer1')`
            visitProjectDependency(dep as ProjectDependency, isTest)
        } else if (dep instanceof SelfResolvingDependency) {
            // ex. `compile fileTree(dir: 'libs', include: ['*.jar'])`
            visitSelfResolvingDependency(dep as SelfResolvingDependency, isTest)
        } else if (dep instanceof ExternalModuleDependency) {
            // ex. `compile "com.google.code.gson:gson:2.3.1"`
            visitExternalModuleDependency(dep as ExternalModuleDependency, isTest)
        } else {
            // Everything else
            visitGenericDependency(dep, isTest)
        }
    }

    protected void failOnBuildClosureForTests(Dependency dep, boolean isTest) {
        if (isTest) {
            String msg = "Cannot translate testCompile dependency $dep using --build-closure; " +
                         "please build this as a separate Gradle project, and then add a testCompile " +
                         "dependency to that project.\n" +
                         "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/dependencies.md#build-standalone-third-party-library"
            throw new InvalidUserDataException(msg)
        }
    }

    protected void visitSelfResolvingDependency(
            SelfResolvingDependency dep, boolean isTest) {
        failOnBuildClosureForTests(dep, isTest)
        project.logger.debug("j2objc dependency converter: Translating file dep: $dep")
        project.configurations.getByName('j2objcTranslationClosure').dependencies.add(
                dep.copy())
    }

    protected void visitProjectDependency(ProjectDependency dep, boolean isTest) {
        project.logger.debug("j2objc dependency converter: Linking Project: $dep")
        project.configurations.getByName(isTest ? 'j2objcTestLinkage' : 'j2objcLinkage').dependencies.add(
                dep.copy())
    }

    protected void visitExternalModuleDependency(ExternalModuleDependency dep, boolean isTest) {
        project.logger.debug("j2objc dependency converter: External module dep: $dep")
        // If the dep is already in the j2objc dist, ignore it.
        if (J2OBJC_DEFAULT_LIBS.contains("${dep.group}:${dep.name}".toString())) {
            // TODO: A more correct method might be converting these into our own
            // form of SelfResolvingDependency that specifies which j2objc dist lib
            // to use.
            project.logger.debug("-- Skipped J2OBJC_DEFAULT_LIB: $dep")
            return
        }
        failOnBuildClosureForTests(dep, isTest)
        project.logger.debug("-- Copied as source: $dep")
        String group = dep.group == null ? '' : dep.group
        String version = dep.version == null ? '' : dep.version
        // TODO: Make this less fragile.  What if sources don't exist for this artifact?
        project.dependencies.add('j2objcTranslationClosure', "${group}:${dep.name}:${version}:sources")
    }

    protected void visitGenericDependency(Dependency dep, boolean isTest) {
        failOnBuildClosureForTests(dep, isTest)
        project.logger.warn("j2objc dependency converter: Unknown dependency type: $dep; copying naively")
        project.configurations.getByName('j2objcTranslationClosure').dependencies.add(
                dep.copy())
    }
}
