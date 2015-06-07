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

/**
 * Further configuration uses the following fields, setting them in j2objcConfig within build.gradle
 */
class J2objcPluginExtension {

    // Where to copy generated files (excludes test code and executable)
    String destDir = null

    // Where to copy generated test files (excludes executable)
    // If null, generated test files are discarded for final output.
    // Can be the same directory as destDir.
    String destDirTest = null

    // Only generated source files, e.g. from dagger annotations. The script will
    // ignore changes in this directory so they must be limited to generated files.
    String[] generatedSourceDirs = []

    // TODO: consider removing support for non Java plugin projects
    // Input source files for this plugin can be configured in a few ways.  The
    // plugin will use the first valid option:
    // 1. The flags below.  You can override 0 to 4 of the flags; any that are null
    //    will backoff to the following.
    // 2. Project SourceSets.  For example, if you use the 'java' or related plugins.
    // 3. "Conventional" defaults for each directory, namely:
    //    src/{main|test}/{java|resources}.  For example, if you use an automatically
    //    converted Ant build.
    //
    // WARNING:
    // We recommend using the standard java plugin (or derivatives) instead of
    // configuring this plugin's source directories manually:
    //     apply plugin: 'java'
    // If you use the standard java plugin, any source set customization will be
    // applied to this plugin as well, and many other plugins will understand
    // your customizations without duplicating configuration.
    String[] customMainSrcDirs = null  // defaults to ['src/main/java']
    String[] customTestSrcDirs = null  // defaults to ['src/test/java']
    String[] customMainResourcesSrcDirs = null  // defaults to ['src/main/resources']
    String[] customTestResourcesSrcDirs = null  // defaults to ['src/test/resources']

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
    String translateFlags = "--no-package-directories"
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
    // Filter on files to translate:
    // a) Regexes are ignored if null
    // b) Matches on path + filename
    // c) Must match IncludeRegex and NOT match ExcludeRegex
    // Example:
    //     translateExcludeRegex ".*/src/(main|test)/java/com/example/EXCLUDE_DIR/.*"
    //     translateIncludeRegex ".*/TranslateOnlyMeAnd(|Test)\\.java"
    // Would prefer to set as null but this doesn't work as @Input for incremental compile
    String translateExcludeRegex = "^\$"
    String translateIncludeRegex = "^.*\$"

    private def classNameToFileRegex(def className) {
        // Gradle java plugin convention dictates java roots end
        // with a java directory.
        return '^.*/java/' + className.replace('.', '/') + '\\.java$'
    }

    private def regexOr(def origRegex, def newPattern) {
        if (origRegex == null || origRegex.empty) {
            return newPattern
        }
        return "(${origRegex})|(${newPattern})"
    }

    // TODO: Consider improved include/exclude patterns, See issue #57.
    // Excludes a fully qualified class name from translation.
    // Example: translateExcludeClass 'java.lang.String'
    def translateExcludeClass(def className) {
        translateExcludeRegex = regexOr(translateExcludeRegex, classNameToFileRegex(className))
    }
    // Includes a fully qualified class name in translation.
    // Example: translateIncludeClass 'java.lang.String'
    def translateIncludeClass(def className) {
        translateIncludeRegex = regexOr(translateIncludeRegex, classNameToFileRegex(className))
    }

    // TODO: consider moving to include(s) / exclude(s) structure as used for filetree

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
    // Filter tests, applied in addition to translate filters (see above)
    // Would prefer to set as null but this doesn't work as @Input for incremental compile
    String testExcludeRegex = "^\$"
    String testIncludeRegex = "^.*\$"
    // Warn if no tests are executed
    boolean testExecutedCheck = true

    // LINK
    // directory of the target Xcode project
    // TODO(bruno): figure out what this should be "${projectDir}/Xcode"
    String xcodeProjectDir = null
    // Xcode target the generated files should be linked to
    String xcodeTarget = null
}
