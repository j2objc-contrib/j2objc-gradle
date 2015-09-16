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
import com.github.j2objccontrib.j2objcgradle.tasks.Utils
import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil
/**
 * j2objcConfig is used to configure the plugin with the project's build.gradle.
 *
 * All paths are resolved using Gradle's <a href="https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html#file(java.lang.Object)">project.file(...)</a>
 *
 *
 * Basic Example:
 *
 * j2objcConfig {
 *     xcodeProjectDir '../ios'
 *     xcodeTarget 'IOS-APP'
 *     finalConfigure()
 * }
 *
 *
 * Complex Example:
 *
 * TODO...
 *
 */
@CompileStatic
class J2objcConfig {

    static J2objcConfig from(Project project) {
        return project.extensions.findByType(J2objcConfig)
    }

    final protected Project project

    J2objcConfig(Project project) {
        assert project != null
        this.project = project

        // The NativeCompilation effectively provides further extensions
        // to the Project, by configuring the 'Objective-C' native build plugin.
        // We don't want to expose the instance to clients of the 'j2objc' plugin,
        // but we also need to configure this object via methods on J2objcConfig.
        nativeCompilation = new NativeCompilation(project)

        // Provide defaults for assembly output locations.
        destSrcMainDir = new File(project.buildDir, 'j2objcOutputs/src/main').absolutePath
        destSrcTestDir = new File(project.buildDir, 'j2objcOutputs/src/test').absolutePath
        destLibDir = new File(project.buildDir, 'j2objcOutputs/lib').absolutePath
    }

    /**
     * Where to assemble generated main libraries.
     * <p/>
     * Defaults to $buildDir/j2objcOutputs/lib
     */
    String destLibDir = null

    /**
     * Where to assemble generated main source and resources files.
     * <p/>
     * Defaults to $buildDir/j2objcOutputs/src/main/objc
     */
    String destSrcMainDir = null

    /**
     * Where to assemble generated test source and resources files.
     * <p/>
     * Can be the same directory as destDir
     * Defaults to $buildDir/j2objcOutputs/src/test/objc
     */
    String destSrcTestDir = null

    // Private helper methods
    // Should use instead of accessing client set 'dest' strings
    File getDestLibDirFile() {
        return project.file(destLibDir)
    }
    File getDestSrcDirFile(String sourceSetName, String fileType) {
        assert sourceSetName in ['main', 'test']
        assert fileType in ['objc', 'resources']

        File destSrcDir = null
        if (sourceSetName == 'main') {
            destSrcDir = project.file(destSrcMainDir)
        } else if (sourceSetName == 'test') {
            destSrcDir = project.file(destSrcTestDir)
        } else {
            assert false, "Unsupported sourceSetName: $sourceSetName"
        }

        return project.file(new File(destSrcDir, fileType))
    }

    /**
     * Generated source files directories, e.g. from dagger annotations.
     * <p/>
     * The plugin will ignore changes in this directory so they must
     * be limited to files generated solely from files within your
     * main and/or test sourceSets.
     */
    List<String> generatedSourceDirs = new ArrayList<>()
    /**
     * Add generated source files directories, e.g. from dagger annotations.
     * <p/>
     * The plugin will ignore changes in this directory so they must
     * be limited to files generated solely from files within your
     * main and/or test sourceSets.
     *
     * @param generatedSourceDirs adds generated source directories for j2objc translate
     */
    void generatedSourceDirs(String... generatedSourceDirs) {
        appendArgs(this.generatedSourceDirs, 'generatedSourceDirs', generatedSourceDirs)
    }


    // CYCLEFINDER
    /**
     * Command line arguments for j2objc cycle_finder.
     * <p/>
     * A list of all possible arguments can be found here:
     * http://j2objc.org/docs/cycle_finder.html
     */
    List<String> cycleFinderArgs = new ArrayList<>()
    /**
     * Add command line arguments for j2objc cycle_finder.
     * <p/>
     * A list of all possible arguments can be found here:
     * http://j2objc.org/docs/cycle_finder.html
     *
     * @param cycleFinderArgs add args for 'cycle_finder' tool
     */
    void cycleFinderArgs(String... cycleFinderArgs) {
        appendArgs(this.cycleFinderArgs, 'cycleFinderArgs', cycleFinderArgs)
    }
    /**
     * Expected number of cycles, defaults to all those found in JRE.
     * <p/>
     * This is an exact number rather than minimum as any change is significant.
     */
    // TODO(bruno): convert to a default whitelist and change expected cyles to 0
    int cycleFinderExpectedCycles = 40


    // TRANSLATE
    /**
     * Command line arguments for j2objc translate.
     * <p/>
     * A list of all possible arguments can be found here:
     * http://j2objc.org/docs/j2objc.html
     */
    List<String> translateArgs = new ArrayList<>()
    /**
     * Add command line arguments for j2objc translate.
     * <p/>
     * A list of all possible arguments can be found here:
     * http://j2objc.org/docs/j2objc.html
     *
     * @param translateArgs add args for the 'j2objc' tool
     */
    void translateArgs(String... translateArgs) {
        appendArgs(this.translateArgs, 'translateArgs', translateArgs)
    }

    /**
     * Enables --build-closure, which translates classes referenced from the
     * list of files passed for translation, using the
     * {@link #translateSourcepaths}.
     */
    void enableBuildClosure() {
        if (!translateArgs.contains('--build-closure')) {
            translateArgs('--build-closure')
        }
    }

    /**
     *  Local jars for translation e.g.: "lib/json-20140107.jar", "lib/somelib.jar".
     *  This will be added to j2objc as a '-classpath' argument.
     */
    List<String> translateClasspaths = new ArrayList<>()
    /**
     *  Local jars for translation e.g.: "lib/json-20140107.jar", "lib/somelib.jar".
     *  This will be added to j2objc as a '-classpath' argument.
     *
     *  @param translateClasspaths add libraries for -classpath argument
     */
    void translateClasspaths(String... translateClasspaths) {
        appendArgs(this.translateClasspaths, 'translateClasspaths', translateClasspaths)
    }

    /**
     * Source jars for translation e.g.: "lib/json-20140107-sources.jar"
     */
    List<String> translateSourcepaths = new ArrayList<>()
    /**
     * Source jars for translation e.g.: "lib/json-20140107-sources.jar"
     *
     *  @param translateSourcepaths args add source jar for translation
     */
    void translateSourcepaths(String... translateSourcepaths) {
        appendArgs(this.translateSourcepaths, 'translateSourcepaths', translateSourcepaths)
    }

    /**
     * True iff only translation (and cycle finding, if applicable) should be attempted,
     * skipping all compilation, linking, and testing tasks.
     */
    boolean translateOnlyMode = false


    // Do not use groovydoc, this option should remain undocumented.
    // WARNING: Do not use this unless you know what you are doing.
    // If true, incremental builds will be supported even if --build-closure is included in
    // translateArgs. This may break the build in unexpected ways if you change the dependencies
    // (e.g. adding new files or changing translateClasspaths). When you change the dependencies and
    // the build breaks, you need to do a clean build.
    boolean UNSAFE_incrementalBuildClosure = false

    /**
     * Experimental functionality to automatically configure dependencies.
     * Consider you have dependencies like:
     * <pre>
     * dependencies {
     *     compile project(':peer1')                  // type (1)
     *     compile 'com.google.code.gson:gson:2.3.1'  // type (3)
     *     compile 'com.google.guava:guava:18.0'      // type (2)
     *     testCompile 'junit:junit:4.11'             // type (2)
     * }
     * </pre>
     * Project dependencies (1) will be added as a `j2objcLink` dependency.
     * Libraries already included in j2objc (2) will be ignored.
     * External libraries in Maven (3) will be added in source JAR form to
     * `j2objcTranslate`, and translated using `--build-closure`.
     * Dependencies must be fully specified before you call finalConfigure().
     * <p/>
     * This will become the default when stable in future releases.
     */
    boolean autoConfigureDeps = false

    /**
     * Additional Java libraries that are part of the j2objc distribution.
     * <p/>
     * For example:
     * <pre>
     * translateJ2objcLibs = ["j2objc_junit.jar", "jre_emul.jar"]
     * </pre>
     */
    // J2objc default libraries, from $J2OBJC_HOME/lib/...
    // TODO: auto add libraries based on java dependencies, warn on version differences
    List<String> translateJ2objcLibs = [
            // Comments indicate difference compared to standard libraries...
            // Memory annotations, e.g. @Weak, @AutoreleasePool
            "j2objc_annotations.jar",
            // Libraries that have CycleFinder fixes, e.g. @Weak and code removal
            "j2objc_guava.jar", "j2objc_junit.jar", "jre_emul.jar",
            // Libraries that don't need CycleFinder fixes
            "javax.inject-1.jar", "jsr305-3.0.0.jar",
            "mockito-core-1.9.5.jar"]

    /**
     * Additional native libraries that are part of the j2objc distribution.
     * <p/>
     * For example:
     * <pre>
     * linkJ2objcLibs = ["guava", "jsr305"]
     * </pre>
     */
    // J2objc default libraries, from $J2OBJC_HOME/lib/..., without '.a' extension.
    // TODO: auto add libraries based on java dependencies, warn on version differences
    List<String> linkJ2objcLibs = ['guava', 'j2objc_main', 'javax_inject', 'jsr305']


    // TODO: warn if different versions than testCompile from Java plugin
    /**
     * Makes sure that the translated filenames don't collide.
     * <p/>
     * Recommended if you choose to use --no-package-directories.
     */
    boolean filenameCollisionCheck = true

    /**
     * Sets the filter on files to translate.
     * <p/>
     * If no pattern is specified, all files within the sourceSets are translated.
     * <p/>
     * This filter is applied on top of all files within the 'main' and 'test'
     * java sourceSets.  Use {@link #translatePattern(groovy.lang.Closure)} to
     * configure.
     */
    PatternSet translatePattern = null
    /**
     * Configures the {@link #translatePattern}.
     * <p/>
     * Calling this method repeatedly further modifies the existing translatePattern,
     * and will create an empty translatePattern if none exists.
     * <p/>
     * For example:
     * <pre>
     * translatePattern {
     *     exclude 'CannotTranslateFile.java'
     *     exclude '**&#47;CannotTranslateDir&#47;*.java'
     *     include '**&#47;CannotTranslateDir&#47;AnExceptionToInclude.java'
     * }
     * </pre>
     * @see
     * <a href="https://docs.gradle.org/current/userguide/working_with_files.html#sec:file_trees">Gradle User Guide</a>
     */
    PatternSet translatePattern(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PatternSet) Closure cl) {
        if (translatePattern == null) {
            translatePattern = new PatternSet()
        }
        return ConfigureUtil.configure(cl, translatePattern)
    }

    /**
     * A mapping from source file names (in the project Java sourcesets) to alternate
     * source files.
     * Both before and after names (keys and values) are evaluated using project.file(...).
     * <p/>
     * Mappings can be used to have completely different implementations in your Java
     * jar vs. your Objective-C library.  This can be especially useful when compiling
     * a third-party library and you need to provide non-trivial OCNI implementations
     * in Objective-C.
     */
    Map<String, String> translateSourceMapping = [:]

    /**
     * Adds a new source mapping.
     * @see #translateSourceMapping
     */
    void translateSourceMapping(String before, String after) {
        translateSourceMapping.put(before, after)
    }

    /**
     * @see #dependsOnJ2objcLib(org.gradle.api.Project)
     * @deprecated Use `dependencies { j2objcLinkage project(':beforeProjectName') }` or
     * `autoConfigureDeps = true` instead.
     */
    // TODO: Do this automatically based on project dependencies.
    @Deprecated
    void dependsOnJ2objcLib(String beforeProjectName) {
        //noinspection GrDeprecatedAPIUsage
        dependsOnJ2objcLib(project.project(beforeProjectName))
    }

    protected NativeCompilation nativeCompilation
    /**
     * Uses the generated headers and compiled j2objc libraries of the given project when
     * compiling this project.
     * <p/>
     * Generally every cross-project 'compile' dependency should have a corresponding
     * call to dependsOnJ2objcLib.
     * <p/>
     * It is safe to use this in conjunction with --build-closure.
     * <p/>
     * Do not also include beforeProject's java source or jar in the
     * translateSourcepaths or translateClasspaths, respectively.  Calling this method
     * is preferable and sufficient.
     *
     * @deprecated Use `dependencies { j2objcLinkage project(':beforeProjectName') }` or
     * `autoConfigureDeps=true` instead.
     */
    // TODO: Phase this API out, and have J2ObjC-applied project dependencies controlled
    // solely via `j2objcLinkage` configuration.
    @CompileStatic(TypeCheckingMode.SKIP)
    @Deprecated
    void dependsOnJ2objcLib(Project beforeProject) {
        project.dependencies {
            j2objcLinkage beforeProject
        }
    }

    /**
     * Which architectures will be built and supported in packed ('fat') libraries.
     * <p/>
     * The three ios_arm* architectures are for iPhone and iPad devices, while
     * ios_i386 and ios_x86_64 are for their simulators.
     * <p/>
     * By default, only common modern iOS architectures will be built:
     * ios_arm64, ios_armv7, ios_x86_64.  You may choose to add any of the remaining
     * entries from NativeCompilation.ALL_SUPPORTED_ARCHS (ios_i386 and ios_armv7s)
     * to support all possible iOS architectures. Listing any new architectures outside of
     * ALL_SUPPORTED_ARCHS will fail the build.
     * <p/>
     * Removing an architecture here will cause that architecture not to be built
     * and corresponding gradle tasks to not be created.
     * <pre>
     * supportedArchs = ['ios_arm64']  // Only build libraries for 64-bit iOS devices
     * </pre>
     * The options are:
     * <ul>
     * <li>'ios_arm64' => iPhone 5S, 6, 6 Plus
     * <li>'ios_armv7' => iPhone 4, 4S, 5
     * <li>'ios_i386' => iOS Simulator on 32-bit OS X
     * <li>'ios_x86_64' => iOS Simulator on 64-bit OS X
     * </ul>
     * @see NativeCompilation#ALL_SUPPORTED_ARCHS
     */
    // Public to allow assignment of array of targets as shown in example
    List<String> supportedArchs = ['ios_arm64', 'ios_armv7', 'ios_x86_64']

    /**
     * An architecture is active if it is both supported ({@link #supportedArchs})
     * and enabled in the current environment via the comma-separated j2objc.enabledArchs
     * value in local.properties.
     * <p/>
     * If no j2objc.enabledArchs value is specified in local.properties, all supported
     * architectures are also active, otherwise the intersection of supportedArchs
     * and j2objc.enabledArchs is used.
     */
    List<String> getActiveArchs() {
        // null is the default value, since an explicit empty string means no architectures
        // are enabled.
        String archsCsv = Utils.getLocalProperty(project, 'enabledArchs', null)
        if (archsCsv == null) {
            return supportedArchs
        }
        List<String> enabledArchs = archsCsv.split(',').toList()
        // Given `j2objc.enabledArchs=` we will have one architecture of empty string,
        // instead we want no architectures at all in this case.
        enabledArchs.remove('')
        List<String> invalidArchs = enabledArchs.minus(
                NativeCompilation.ALL_SUPPORTED_ARCHS.clone() as List<String>).toList()
        if (!invalidArchs.isEmpty()) {
            throw new InvalidUserDataException("Invalid 'enabledArchs' entry: " + invalidArchs.join(', '))
        }
        // Keep the return value sorted to prevent changes in intersection ordering
        // from forcing a rebuild.
        return supportedArchs.intersect(enabledArchs).toList().sort()
    }

    // TEST
    /**
     * Command line arguments for j2objcTest task.
     */
    List<String> testArgs = new ArrayList<>()
    /**
     * Add command line arguments for j2objcTest task.
     *
     * @param testArgs add args for the 'j2objcTest' task
     */
    void testArgs(String... testArgs) {
        appendArgs(this.testArgs, 'testArgs', testArgs)
    }

    /**
     * j2objcTest will fail if it runs less than the expected number of tests; set to 0 to disable.
     * <p/>
     * It is a minimum so adding a unit test doesn't break the j2objc build.
     */
    int testMinExpectedTests = 1

    /**
     * Filter on files to test.  Note this has no effect on which tests are
     * translated, just which tests are executed by the j2objcTest task.
     * <p/>
     * If no pattern is specified, all files within the 'test' sourceSet are translated.
     * <p/>
     * This filter is applied on top of all files within the 'main' and 'test'
     * java sourceSets.  Use {@link #testPattern(groovy.lang.Closure)} to
     * configure.
     */
    PatternSet testPattern = null
    /**
     * Configures the {@link #testPattern}
     * <p/>
     * Calling this method repeatedly further modifies the existing testPattern,
     * and will create an empty testPattern if none exists.
     * <p/>
     * For example:
     * <pre>
     * translatePattern {
     *     exclude 'CannotTranslateFileTest.java'
     *     exclude '**&#47;CannotTranslateDir&#47;*.java'
     *     include '**&#47;CannotTranslateDir&#47;AnExceptionToIncludeTest.java'
     * }
     * </pre>
     * @see
     * <a href="https://docs.gradle.org/current/userguide/working_with_files.html#sec:file_trees">Gradle User Guide</a>
     */
    PatternSet testPattern(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PatternSet) Closure cl) {
        if (testPattern == null) {
            testPattern = new PatternSet()
        }
        return ConfigureUtil.configure(cl, testPattern)
    }

    // Native build customization.
    /**
     * Directories of Objective-C source to compile in addition to the
     * translated source.
     */
    // Native build accepts empty array but throws exception on empty List<String>
    // "srcDirs j2objcConfig.extraObjcSrcDirs" line in NativeCompilation
    String[] extraObjcSrcDirs = []
    /**
     * Add directories of Objective-C source to compile in addition to the
     * translated source.
     *
     * @param extraObjcSrcDirs add directories for Objective-C source to be compiled
     */
    void extraObjcSrcDirs(String... extraObjcSrcDirs) {
        verifyNoSpaceArgs('extraObjcSrcDirs', extraObjcSrcDirs)
        for (String arg in extraObjcSrcDirs) {
            this.extraObjcSrcDirs += arg
        }
    }
    /**
     * Additional arguments to pass to the native compiler.
     */
    // Native build accepts empty array but throws exception on empty List<String>
    String[] extraObjcCompilerArgs = []
    /**
     * Add arguments to pass to the native compiler.
     *
     * @param extraObjcCompilerArgs add arguments to pass to the native compiler.
     */
    void extraObjcCompilerArgs(String... extraObjcCompilerArgs) {
        verifyNoSpaceArgs('extraObjcCompilerArgs', extraObjcCompilerArgs)
        for (String arg in extraObjcCompilerArgs) {
            this.extraObjcCompilerArgs += arg
        }
    }
    /**
     * Additional arguments to pass to the native linker.
     */
    // Native build accepts empty array but throws exception on empty List<String>
    String[] extraLinkerArgs = []
    /**
     * Add arguments to pass to the native linker.
     *
     * @param extraLinkerArgs add arguments to pass to the native linker.
     */
    void extraLinkerArgs(String... extraLinkerArgs) {
        verifyNoSpaceArgs('extraLinkerArgs', extraLinkerArgs)
        for (String arg in extraLinkerArgs) {
            this.extraLinkerArgs += arg
        }
    }

    /**
     * Additional native libraries to link to.
     */
    List<Map> extraNativeLibs = []
    /**
     * Add additional native library to link to.
     * <p/>
     * For example, if you built native library 'utils' in project 'common':
     * <pre>
     * extraNativeLib project: 'common', library: 'utils', linkage: 'static'
     * </pre>
     */
    void extraNativeLib(Map spec) {
        extraNativeLibs.add(spec)
    }


    // XCODE
    /**
     * Directory of the target Xcode project.
     *
     * Suggested location is '../ios'
     * See J2ObjC Plugin <a href="https://github.com/j2objc-contrib/j2objc-gradle/blob/master/README.md#folder-structure">Folder Structure</a>
     *
     */
    String xcodeProjectDir = null
    /**
     * Xcode app target the generated library should be linked to.
     *
     * This will automatically add linkage for any target that starts with the
     * same name. This should include any Test and Watch targets. For the example of
     * xcodeTarget == 'IOS-APP', it will add targets for 'IOS-APPTests',
     * 'IOS-APP WatchKit App' & 'IOS-APP WatchKit Extension' (if they exist).
     */
    String xcodeTarget = null


    protected boolean finalConfigured = false
    /**
     * Configures the j2objc build.  Must be called at the very
     * end of your j2objcConfig block.
     */
    @VisibleForTesting
    void finalConfigure() {
        validateConfiguration()
        // Conversion of compile and testCompile dependencies occurs optionally.
        if (autoConfigureDeps) {
            convertDeps()
        }
        // Resolution of j2objcTranslateSource dependencies occurs always.
        // This lets users turn off autoConfigureDeps but manually set j2objcTranslateSource.
        resolveDeps()
        configureNativeCompilation()
        configureTaskState()
        finalConfigured = true
    }

    protected void validateConfiguration() {
        assert destLibDir != null
        assert destSrcMainDir != null
        assert destSrcTestDir != null
    }

    protected void configureNativeCompilation() {
        // TODO: When Gradle makes it possible to modify a native build config
        // after initial creation, we can remove this, and have methods on this object
        // mutate the existing native model { } block.  See:
        // https://discuss.gradle.org/t/problem-with-model-block-when-switching-from-2-2-1-to-2-4/9937
        nativeCompilation.apply(project.file("${project.buildDir}/j2objcSrcGen"))
    }

    protected void convertDeps() {
        new DependencyConverter(project, this).configureAll()
    }

    protected void resolveDeps() {
        new DependencyResolver(project, this).configureAll()
    }

    protected void configureTaskState() {
        // Disable only if explicitly present and not true.
        boolean debugEnabled = Boolean.parseBoolean(Utils.getLocalProperty(project, 'debug.enabled', 'true'))
        boolean releaseEnabled = Boolean.parseBoolean(Utils.getLocalProperty(project, 'release.enabled', 'true'))
        // Enable only if explicitly present in either the project config OR the local config.
        boolean translateOnlyMode = this.translateOnlyMode ||
                                    Boolean.parseBoolean(Utils.getLocalProperty(project, 'translateOnlyMode', 'false'))

        if (!translateOnlyMode) {
            Utils.requireMacOSX('Native Compilation of translated code task')
        }

        project.tasks.all { Task task ->
            String name = task.name
            // For convenience, disable all debug and/or release tasks if the user desires.
            // Note all J2objcPlugin-created tasks are of the form `j2objc.*(Debug|Release)?`
            // however Native plugin-created tasks (on our behalf) are of the form `.*((D|d)ebug|(R|r)elease).*(j|J)2objc.*'
            // so these patterns find all such tasks.
            if (name.contains('j2objc') || name.contains('J2objc')) {
                if (!debugEnabled && (name.contains('debug') || name.contains('Debug'))) {
                    task.enabled = false
                }
                if (!releaseEnabled && (name.contains('release') || name.contains('Release'))) {
                    task.enabled = false
                }
            }

            // Support translation-only mode.
            if (translateOnlyMode) {
                // First pattern matches all native-compilation tasks.
                // Second pattern matches plugin-specific tasks beyond translation.
                if ((name =~ /^.*((J|j)2objc(Executable|StaticLibrary|SharedLibrary|Objc))$/).matches() ||
                    (name =~ /^j2objc(Assemble|PackLibraries|Test)(Debug|Release)$/).matches()) {
                    task.enabled = false
                }
            }
        }
    }

    boolean isFinalConfigured() {
        return finalConfigured
    }

    // Provides a subset of "args" interface from project.exec as implemented by ExecHandleBuilder:
    // https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/groovy/org/gradle/process/internal/ExecHandleBuilder.java
    // Allows the following:
    // j2objcConfig {
    //     translateArgs '--no-package-directories', '--prefixes', 'prefixes.properties'
    // }
    @VisibleForTesting
    static void appendArgs(List<String> listArgs, String nameArgs, String... args) {
        verifyNoSpaceArgs(nameArgs, args)
        listArgs.addAll(Arrays.asList(args))
    }

    // Verify that no argument contains a space
    @VisibleForTesting
    static void verifyNoSpaceArgs(String nameArgs, String... args) {
        if (args == null) {
            throw new InvalidUserDataException("$nameArgs == null!")
        }
        for (String arg in args) {
            if (arg.contains(' ')) {
                String rewrittenArgs = "'" + arg.split(' ').join("', '") + "'"
                throw new InvalidUserDataException(
                        "'$arg' argument should not contain spaces and be written out as distinct entries:\n" +
                        "$nameArgs $rewrittenArgs")
            }
        }
    }

    @VisibleForTesting
    void testingOnlyPrepConfigurations() {
        // When testing we don't always want to apply the entire plugin
        // before calling finalConfigure.
        project.configurations.create('j2objcTranslationClosure')
        project.configurations.create('j2objcTranslation')
        project.configurations.create('j2objcLinkage')
    }
}
