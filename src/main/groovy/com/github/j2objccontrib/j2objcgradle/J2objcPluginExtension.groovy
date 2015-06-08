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

import groovy.transform.PackageScope
import org.gradle.api.Project
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil
/**
 * Further configuration uses the following fields, setting them in j2objcConfig within build.gradle
 */
class J2objcPluginExtension {

    // Where to assemble generated main source files.
    // Defaults to $buildDir/j2objcOutputs/src/main/objc
    String destSrcDir = null

    // Where to assemble generated test source files.
    // Can be the same directory as destDir.
    // Defaults to $buildDir/j2objcOutputs/src/test/objc
    String destSrcDirTest = null

    // Where to assemble generated main libraries.
    // Defaults to $buildDir/j2objcOutputs/lib
    String destLibDir = null

    // Only generated source files, e.g. from dagger annotations. The script will
    // ignore changes in this directory so they must be limited to files generated
    // solely from files within your main and/or test sourceSets.
    String[] generatedSourceDirs = []

    // CYCLEFINDER
    // TODO(bruno): consider enabling cycleFinder by default
    boolean cycleFinderSkip = true
    // Flags copied verbatim to cycle_finder command
    // Would prefer default of null but that can't be used for @Input
    // Warning will ask user to configure this within j2objcConfig
    String cycleFinderFlags = null
    // Expected number of cycles, defaults to all those found in JRE
    // TODO(bruno): convert to a default whitelist and change expected cyles to 0
    int cycleFinderExpectedCycles = 40

    // TRANSLATE
    // Flags copied verbatim to j2objc command
    // A list of all possible flag can be found here
    // https://github.com/google/j2objc/blob/master/translator/src/main/resources/com/google/devtools/j2objc/J2ObjC
    // .properties
    String translateFlags = "--no-package-directories --static-accessor-methods"
    // -classpath library additions from ${projectDir}/lib/, e.g.: "json-20140107.jar", "somelib.jar"
    String[] translateClassPaths = []

    // WARNING: Do not use this unless you know what you are doing.
    // If true, incremental builds will be supported even if --build-closure is included in
    // translateFlags. This may break the build in unexpected ways if you change the dependencies
    // (e.g. adding new files or changing translateClassPaths). When you change the dependencies and
    // the build breaks, you need to do a clean build.
    boolean UNSAFE_incrementalBuildClosure = false

    // Additional libraries that are part of the j2objc release
    // TODO: warn if different versions than testCompile from Java plugin
    // TODO: just import everything in the j2objc/lib/ directory?
    // Xcode doesn't support directories for packages, so all files must be output
    // to a single directory. This check makes sure that the name don't collide.
    boolean filenameCollisionCheck = true
    // J2objc default libraries, from $J2OBJC_HOME/lib/...
    String[] translateJ2objcLibs = [
            // Memory annotations, e.g. @Weak, @AutoreleasePool
            "j2objc_annotations.jar",
            // Libraries that have CycleFinder fixes, e.g. @Weak and code removal
            "j2objc_guava.jar", "j2objc_junit.jar", "jre_emul.jar",
            // Libraries that don't need CycleFinder fixes
            "javax.inject-1.jar", "jsr305-3.0.0.jar",
            "mockito-core-1.9.5.jar"]

    // Filter on files to translate.
    // This filter is applied on top of all files within the 'main' and 'test'
    // java sourceSets, example:
    // translatePattern {
    //     include '**/*.java'
    //     exclude '**/SomeNativeCode.java'
    // }
    // If no pattern is specified, all files within the sourceSets are translated.
    PatternSet translatePattern = null
    // DSL method to conveniently configure the translatePattern.
    def translatePattern(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PatternSet) Closure cl) {
        if (translatePattern == null) {
            translatePattern = new PatternSet()
        }
        return ConfigureUtil.configure(cl, translatePattern)
    }

    // Translation task additional paths
    String translateSourcepaths = null

    // Set to true if java project dependencies of the current project should be appended to the sourcepath
    // automatically.  You will most likely want to use --build-closure in the translateFlags as well.
    boolean appendProjectDependenciesToSourcepath = false

    // TEST
    // Skip test task if true
    boolean testSkip = false
    // Flags copied verbatim to testrunner command
    String testFlags = ""

    // Filter on files to test.  Note this has no effect on which tests are
    // translated, just which tests are executed by the j2objcTest task.
    // This filter is applied on top of all files within the 'test'
    // java sourceSet, example:
    // testPattern {
    //     include '**/*.java'
    //     exclude '**/SomeNativeCodeTest.java'
    // }
    // If no pattern is specified, all files within the sourceSet are tested.
    PatternSet testPattern = null
    // DSL method to conveniently configure the testPattern.
    def testPattern(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PatternSet) Closure cl) {
        if (testPattern == null) {
            testPattern = new PatternSet()
        }
        return ConfigureUtil.configure(cl, testPattern)
    }

    // Warn if no tests are executed
    boolean testExecutedCheck = true

    // LINK
    // directory of the target Xcode project
    // TODO(bruno): figure out what this should be "${projectDir}/Xcode"
    String xcodeProjectDir = null
    // Xcode target the generated files should be linked to
    String xcodeTarget = null

    // Configures defaults whose values are dependent on the project.
    @PackageScope
    def configureDefaults(Project project) {
        // Provide defaults for assembly output locations.
        if (destSrcDir == null) {
            destSrcDir = "${project.buildDir}/j2objcOutputs/src/main/objc"
        }
        if (destSrcDirTest == null) {
            destSrcDirTest = "${project.buildDir}/j2objcOutputs/src/test/objc"
        }
        if (destLibDir == null) {
            destLibDir = "${project.buildDir}/j2objcOutputs/lib"
        }
    }
}
