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
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.util.PatternSet
/**
 * Resolves `j2objc*` dependencies into their `j2objc` constructs:
 * <p/>
 * <ul>
 * <li><b>j2objcTranslationClosure</b> - The plugin will translate only the subset of
 * the configuration's source jars that are actually used by this project's
 * code (via --build-closure), and
 * compile and link the translated code directly into this project's libraries.
 * Note that if multiple projects use j2objcTranslationClosure with the same
 * external library, you will likely get duplicate symbol definition errors
 * when linking them together.  Consider instead creating a separate Gradle
 * project for that external library using j2objcTranslation.
 * </li>
 * <li><b>j2objcTranslation</b> - The plugin will translate the entire source jar
 * provided in this configuration. Usually, this configuration is used
 * to translate a single external Java library into a standalone Objective C library, that
 * can then be linked (via j2objcLinkage) into your projects.
 * </li>
 * <li><b>j2objcLinkage</b> - The plugin will include the headers of, and link to
 * the static library within, the referenced project.  Usually this configuration
 * is used with other projects (your own, or external libraries translated
 * with j2objcTranslation) that the J2ObjC Gradle Plugin has also been applied to.
 * </li>
 * </ul>
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
        project.configurations.getByName('j2objcTranslationClosure').each { File it ->
            // These are the resolved files, NOT the dependencies themselves.
            // Usually source jars.
            visitTranslationClosureFile(it)
        }
        project.configurations.getByName('j2objcTranslation').each { File it ->
            // These are the resolved files, NOT the dependencies themselves.
            // Usually source jars.
            visitTranslationSourceJar(it)
        }
        project.configurations.getByName('j2objcLinkage').dependencies.each {
            visitLink(it)
        }
    }

    protected void visitTranslationClosureFile(File depFile) {
        j2objcConfig.translateSourcepaths(depFile.absolutePath)
        j2objcConfig.enableBuildClosure()
    }

    private static final String EXTRACTION_TASK_NAME = 'j2objcTranslatedLibraryExtraction'

    /**
     * Adds to the main java sourceSet a to-be-generated directory that contains the contents
     * of `j2objcTranslation` dependency libraries (if any).
     */
    static void configureSourceSets(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention)
        SourceSet sourceSet = javaConvention.sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
        String dir = "${project.buildDir}/translationExtraction"
        sourceSet.java.srcDirs(project.file(dir))
        Copy copy = project.tasks.create(EXTRACTION_TASK_NAME, Copy,
                {Copy task ->
                    task.into(project.file(dir))
                    // If two libraries define the same file, fail early.
                    task.duplicatesStrategy = DuplicatesStrategy.FAIL
                })
        project.tasks.getByName(sourceSet.compileJavaTaskName).dependsOn(copy)
    }

    // Copy contents of sourceJarFile to build/translationExtraction
    protected void visitTranslationSourceJar(File sourceJarFile) {
        if (!sourceJarFile.absolutePath.endsWith('.jar')) {
            String msg = "`j2objcTranslation` dependencies can only handle " +
                         "source jar files, not ${sourceJarFile.absolutePath}"
            throw new InvalidUserDataException(msg)
        }
        PatternSet pattern = new PatternSet()
        pattern.include('**/*.java')
        Copy copy = project.tasks.getByName(EXTRACTION_TASK_NAME) as Copy
        copy.from(project.zipTree(sourceJarFile).matching(pattern))
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
