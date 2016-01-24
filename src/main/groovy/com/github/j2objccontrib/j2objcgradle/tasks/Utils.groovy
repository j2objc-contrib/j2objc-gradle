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
import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Nullable
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.internal.ExecException
import org.gradle.util.GradleVersion

import java.util.regex.Matcher

/**
 * Internal utilities supporting plugin implementation.
 */
// Without access to the project, logging is performed using the
// static 'log' variable added during decoration with this annotation.
@Slf4j
@CompileStatic
class Utils {
    // TODO: ideally bundle j2objc binaries with plugin jar and load at runtime with
    // TODO: ClassLoader.getResourceAsStream(), extract, chmod and then execute

    // Prevent construction of this class, confines usage to static methods
    private Utils() { }

    static boolean checkGradleVersion(boolean throwIfUnsupported) {
        return checkGradleVersion(GradleVersion.current(), throwIfUnsupported)
    }

    @VisibleForTesting
    static boolean checkGradleVersion(GradleVersion gradleVersion, boolean throwIfUnsupported) {
        String errorMsg = ''

        final GradleVersion minGradleVersion = GradleVersion.version('2.4')
        if (gradleVersion.compareTo(GradleVersion.version('2.4')) < 0) {
            errorMsg = "J2ObjC Gradle Plugin requires minimum Gradle version: $minGradleVersion"
        }

        final GradleVersion unsupportedGradleVersion = GradleVersion.version('2.9')
        if (gradleVersion.compareTo(unsupportedGradleVersion) >= 0) {
            errorMsg = "Please use Gradle 2.8 as $unsupportedGradleVersion is unsupported:\n" +
                       "https://github.com/j2objc-contrib/j2objc-gradle/issues/568"
        }

        if (!errorMsg.isEmpty()) {
            if (throwIfUnsupported) {
                throw new InvalidUserDataException(errorMsg)
            } else {
                return true
            }
        }
        return false
    }

    static List<Integer> parseVersionComponents(String ver) {
        return ver.tokenize('.').collect({String versionComponent ->
            try {
                return Integer.parseInt(versionComponent)
            } catch (NumberFormatException nfe) {
                // Keep it simple.  If the version provided doesn't meet a simple N.N.N format,
                // assume the user knows what they are doing and keep going.  The maximum integer
                // provides this behavior.
                return Integer.MAX_VALUE
            }
        })
    }

    static boolean isAtLeastVersion(String version, String minVersion) {
        List<Integer> minVersionComponents = parseVersionComponents(minVersion)
        List<Integer> versionComponents = parseVersionComponents(version)
        for (int i = 0; i < Math.min(minVersionComponents.size(), versionComponents.size()); i++) {
            if (versionComponents[i] > minVersionComponents[i]) {
                return true
            } else if (versionComponents[i] < minVersionComponents[i]) {
                return false
            }
        }
        // Each existing component was equal.  If the requested version is at least as long, we're good.
        return versionComponents.size() >= minVersionComponents.size()
    }


    private static String fakeOSName = ''

    /* This allows faking of is(Linux|Windows|MacOSX) methods but misses java.io.File separators.
     *
     * The setFakeOsNone() should be called in the test unit @After method to isolate test state.
     */
    @VisibleForTesting
    static void setFakeOSLinux() {
        fakeOSName = 'Linux'
    }

    @VisibleForTesting
    static void setFakeOSMacOSX() {
        fakeOSName = 'Mac OS X'
    }

    @VisibleForTesting
    static void setFakeOSWindows() {
        fakeOSName = 'Windows'
    }

    // Unset fake os, should be used in @After method
    @VisibleForTesting
    static void setFakeOSNone() {
        fakeOSName = ''
    }

    @VisibleForTesting
    static String getLowerCaseOSName(boolean ignoreFakeOSName) {

        String osName = System.getProperty('os.name')
        if (!ignoreFakeOSName) {
            if (!fakeOSName.isEmpty()) {
                osName = fakeOSName
            }
        }
        osName = osName.toLowerCase()
        return osName
    }

    static boolean isLinux() {
        String osName = getLowerCaseOSName(false)
        // http://stackoverflow.com/a/18417382/1509221
        return osName.contains('nux')
    }

    static boolean isMacOSX() {
        String osName = getLowerCaseOSName(false)
        // http://stackoverflow.com/a/18417382/1509221
        return osName.contains('mac') || osName.contains('darwin')
    }

    static boolean isWindows() {
        String osName = getLowerCaseOSName(false)
        return osName.contains('windows')
    }

    static boolean isWindowsNoFake() {
        String osName = getLowerCaseOSName(true)
        return osName.contains('windows')
    }

    static void requireMacOSX(String taskName) {
        if (!isMacOSX()) {
            throw new InvalidUserDataException(
                    "Mac OS X is required for $taskName. Use `translateOnlyMode` on Windows or Linux:\n" +
                    'https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-develop-on-windows-or-linux')
        }
    }

    // Same as File.separator but can be faked using setFakeOSXXXX()
    static String fileSeparator() {
        if (isWindows()) {
            return '\\'
        } else {
            return '/'
        }
    }

    // Same as File.pathSeparator but can be faked using setFakeOSXXXX()
    static String pathSeparator() {
        if (isWindows()) {
            return ';'
        } else {
            return ':'
        }
    }

    // Same as URI.relativize(...) method but works for non-parent directories
    // by adding '../../' when necessary to find a common parent
    static String relativizeNonParent(File src, File dst) {
        URI dstUri = dst.toURI()
        URI relativized
        String upDirPrefix = ''

        while (true) {
            relativized = src.toURI().relativize(dstUri)
            if (relativized != dstUri) {
                // Relative path found
                break
            }

            upDirPrefix += '../'
            src = src.getParentFile()
            assert (src != null)
        }

        trimTrailingForwardSlash(upDirPrefix + relativized.toString())
    }

    static void throwIfNoJavaPlugin(Project proj) {
        if (!proj.plugins.hasPlugin('java')) {
            String message =
                    "J2ObjC Gradle Plugin: missing 'java' plugin for '${proj.name}' project.\n" +
                    "This is a requirement for using J2ObjC. Please see:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/#quick-start-guide"
            throw new InvalidUserDataException(message)
        }
    }

    // Add valid keys here
    // Use camelCase and order alphabetically
    private static final List<String> PROPERTIES_VALID_KEYS =
            Collections.unmodifiableList(Arrays.asList(
        'debug.enabled',
        'enabledArchs',
        'home',
        'release.enabled',
        'translateOnlyMode'
    ))

    private static final String PROPERTY_KEY_PREFIX = 'j2objc.'

    /**
     * Retrieves the local properties with highest precedence:
     * 1.  local.properties value like j2objc.name1.name2 when present.
     * 2.  environment variable like J2OBJC_NAME1_NAME2 when present.
     * 3.  defaultValue.
     */
    static String getLocalProperty(Project proj, String key, String defaultValue = null) {

        // Check for requesting invalid key
        if (!(key in PROPERTIES_VALID_KEYS)) {
            throw new InvalidUserDataException(
                    "Requesting invalid property: $key\n" +
                    "Valid Keys: $PROPERTIES_VALID_KEYS")
        }
        File localPropertiesFile = new File(proj.rootDir, 'local.properties')
        String result = null
        if (localPropertiesFile.exists()) {
            Properties localProperties = new Properties()
            localPropertiesFile.withInputStream {
                localProperties.load it
            }

            // Check valid key in local.properties for everything with PROPERTY_KEY_PREFIX
            localProperties.keys().each { String propKey ->
                if (propKey.startsWith(PROPERTY_KEY_PREFIX)) {
                    String withoutPrefix =
                            propKey.substring(PROPERTY_KEY_PREFIX.length(), propKey.length())
                    if (!(withoutPrefix in PROPERTIES_VALID_KEYS)) {
                        throw new InvalidUserDataException(
                                "Invalid J2ObjC Gradle Plugin property: $propKey\n" +
                                "From local.properties: $localPropertiesFile.absolutePath\n" +
                                "Valid Keys: $PROPERTIES_VALID_KEYS")
                    }
                }
            }

            result = localProperties.getProperty(PROPERTY_KEY_PREFIX + key, null)
        }
        if (result == null) {
            // debug.enabled becomes J2OBJC_DEBUG_ENABLED
            String envName = 'J2OBJC_' + key.replace('.', '_').toUpperCase(Locale.ENGLISH)
            // TODO: Unit tests.
            result = System.getenv(envName)
        }
        return result == null ? defaultValue : result
    }

    // MUST be used only in @Input getJ2objcHome() methods to ensure up-to-date checks are correct
    // @Input getJ2objcHome() method can be used freely inside the task action
    static String j2objcHome(Project proj) {
        String j2objcHome = getLocalProperty(proj, 'home')
        if (j2objcHome == null) {
            throwJ2objcConfigFailure(proj, "J2ObjC Home not set.")
        }
        File j2objcHomeFile = new File(j2objcHome)
        if (!j2objcHomeFile.exists()) {
            throwJ2objcConfigFailure(proj, "J2ObjC Home directory not found, expected at $j2objcHome.")
        }
        // File removes trailing slashes cause problems with string concatenation logic
        return j2objcHomeFile.absolutePath
    }

    static File j2objcJar(Project proj) {
        return proj.file("${j2objcHome(proj)}/lib/j2objc.jar")
    }

    static File cycleFinderJar(Project proj) {
        return proj.file("${j2objcHome(proj)}/lib/cycle_finder.jar")
    }

    static void throwJ2objcConfigFailure(Project proj, String preamble) {
        String propFile = "${proj.rootDir.absolutePath}/local.properties"
        // This can be null in tests!
        J2objcConfig config = J2objcConfig.from(proj)
        String ver = config == null ? '(unknown version)' : config.j2objcVersion
        String message = ">>>>>>>>>>>>>>>> J2ObjC Tool Configuration Failure <<<<<<<<<<<<<<<<\n" +
                         "$preamble\n" +
                         "\n" +
                         "If you do not have a J2ObjC v${ver} distribution,\n" +
                         "you can initiate a default installation of J2ObjC\n" +
                         "by running the following from a Terminal:\n" +
                         "\n" +
                         "  J2OBJC_ROOT=~/j2objcDist\n" +
                         // Create a distribution parent directory for all versions.
                         "  mkdir -p \$J2OBJC_ROOT; pushd \$J2OBJC_ROOT\n" +
                         // Download an official release.
                         "  curl -L https://github.com/google/j2objc/releases/download/${ver}/j2objc-${ver}.zip > j2objc-${ver}.zip\n" +
                         // Unzip the distribution (only).
                         "  unzip j2objc-${ver}.zip; popd\n" +
                         // Eliminate any existing value in local.properties.
                         "  sed -i '' '/j2objc.home/d' $propFile\n" +
                         // Add a new value for j2objc.home to local.properties.
                         "  echo j2objc.home=\$J2OBJC_ROOT/j2objc-${ver} >> $propFile\n" +
                         "\n" +
                         "Then rerun your Gradle build.\n" +
                         "\n" +
                         "For advanced configuration of J2ObjC, please see http://j2objc.org/\n" +
                         "If J2ObjC v${ver} is already installed, set J2ObjC Home either:\n" +
                         "1) in a 'local.properties' file in the project root directory as:\n" +
                         "   j2objc.home=/LOCAL_J2OBJC_PATH\n" +
                         "2) as the J2OBJC_HOME system environment variable\n" +
                         "\n" +
                         "If both are configured the value in the properties file will be used."
        throw new InvalidUserDataException(message)
    }

    static boolean j2objcHasOsxDistribution(Project proj) {
        return proj.file("${j2objcHome(proj)}/lib/macosx").exists()
    }

    // Reads properties file and arguments from translateArgs (last argument takes precedence)
    //   --prefixes dir/prefixes.properties --prefix com.ex.dir=Short --prefix com.ex.dir2=Short2
    // TODO: separate this out to a distinct argument that's added to translateArgs
    // TODO: @InputFile conversion for this
    static Properties packagePrefixes(Project proj, List<String> translateArgs) {
        Properties props = new Properties()
        String joinedTranslateArgs = translateArgs.join(' ')
        Matcher matcher = (joinedTranslateArgs =~ /--prefix(|es)\s+(\S+)/)
        int start = 0
        while (matcher.find(start)) {
            start = matcher.end()
            Properties newProps = new Properties()
            String argValue = matcher.group(2)
            if (matcher.group(1) == "es") {
                // --prefixes prefixes.properties
                // trailing space confuses FileInputStream
                String prefixesPath = argValue.trim()
                log.debug "Loading prefixesPath: $prefixesPath"
                newProps.load(new FileInputStream(proj.file(prefixesPath).path))
            } else {
                // --prefix com.example.dir=CED
                newProps.load(new StringReader(argValue.trim()))
            }
            props.putAll(newProps)
        }

        log.debug 'Package Prefixes: http://j2objc.org/docs/Package-Prefixes.html'
        for (key in props.keys()) {
            log.debug "Package Prefix Property: $key : ${props.getProperty((String) key)}"
        }

        return props
    }

    /*
     * Throws exception if two filenames match, even if in distinct directories.
     * This is important for referencing files with Xcode.
     */

    static void filenameCollisionCheck(FileCollection files) {
        HashMap<String, File> nameMap = [:]
        for (file in files) {
            log.debug "CollisionCheck: ${file.name}, ${file.absolutePath}"
            if (nameMap.containsKey(file.name)) {
                File prevFile = nameMap.get(file.name)
                String message =
                        "File name collision detected:\n" +
                        "  ${prevFile.path}\n" +
                        "  ${file.path}\n" +
                        "\n" +
                        "To disable this check (which may overwrite files), modify build.gradle:\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    forceFilenameCollisionCheck false\n" +
                        "}\n"
                throw new InvalidUserDataException(message)
            }
            nameMap.put(file.name, file)
        }
    }

    // Retrieves the configured source directories from the Java plugin SourceSets.
    // This includes the files for all Java source within these directories.
    static SourceDirectorySet srcSet(Project proj, String sourceSetName, String fileType) {
        throwIfNoJavaPlugin(proj)

        assert fileType == 'java' || fileType == 'resources'
        assert sourceSetName == 'main' || sourceSetName == 'test'
        JavaPluginConvention javaConvention = proj.getConvention().getPlugin(JavaPluginConvention)
        SourceSet sourceSet = javaConvention.sourceSets.findByName(sourceSetName)
        // For standard fileTypes 'java' and 'resources,' per contract this cannot be null.
        SourceDirectorySet srcDirSet = fileType == 'java' ? sourceSet.java : sourceSet.resources
        return srcDirSet
    }

    // Add list of java path to a FileCollection as a FileTree
    static FileTree javaTrees(Project proj, List<String> treePaths) {
        List<? extends FileTree> trees =
            treePaths.collect({ String treePath -> proj.fileTree(dir: treePath, includes: ["**/*.java"]) })
        return new UnionFileTree("javaTrees_j2objc", (Collection<? extends FileTree>) trees)
    }

    static List<String> j2objcLibs(String j2objcHome,
                                   List<String> libraries) {
        return libraries.collect { String library ->
            return "$j2objcHome/lib/$library"
        }
    }

    // Convert FileCollection to joined path arg, e.g. "src/Some.java:src/Another.java"
    static String joinedPathArg(FileCollection files) {
        String[] paths = []
        files.each { File file ->
            paths += file.path
        }
        // OS specific separator, i.e. ":" on OS X and ";" on Windows
        return paths.join(pathSeparator())
    }

    static String trimTrailingForwardSlash(String path) {
        assert path != null
        if (path.endsWith('/')) {
            // Remove last character
            return path[0..-2]
        }
        return path
    }

    // Convert regex to string for display, wrapping it with /.../
    // From Groovy-Lang: "Only forward slashes need to be escaped with a backslash"
    // http://docs.groovy-lang.org/latest/html/documentation/#_slashy_string
    static String escapeSlashyString(String regex) {
        return '/' + regex.replace('/', '\\/') + '/'
    }

    static String greatestCommonPrefix(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return a.substring(0, i);
            }
        }
        return a.substring(0, minLength);
    }

    // Matches regex, return first match as string, must have >1 capturing group
    // Return first capture group, comparing stderr first then stdout
    // Returns null for no match
    static String matchRegexOutputs(
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream stderr,
            @Nullable String regex) {

        if (regex == null) {
            return null
        }

        Matcher stdMatcher = (stdout.toString() =~ regex)
        Matcher errMatcher = (stderr.toString() =~ regex)
        // Requires a capturing group in the regex
        String assertFailMsg =
                "matchRegexOutputs must have '(...)' capture group, regex: " +
                escapeSlashyString(regex)
        assert stdMatcher.groupCount() >= 1, assertFailMsg
        assert errMatcher.groupCount() >= 1, assertFailMsg

        if (errMatcher.find()) {
            return errMatcher.group(1)
        }
        if (stdMatcher.find()) {
            return stdMatcher.group(1)
        }

        return null
    }

    static String toQuotedList(List<String> listString) {
        if (listString.isEmpty()) {
            return ''
        } else {
            return "'${listString.join("','")}'"
        }
    }

    @VisibleForTesting
    static String projectExecLog(
            ExecSpec execSpec, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr,
            boolean execSucceeded, Exception exception) {
        // Add command line and stderr to make the error message more useful
        // Chain to the original ExecException for complete stack trace

        String msg
        // The command line can be long, so highlight more important details below
        if (execSucceeded) {
            msg = 'Command Line Succeeded:\n'
        } else {
            msg = 'Command Line Failed:\n'
        }

        msg += execSpec.getCommandLine().join(' ') + '\n'

        // Working Directory appears to always be set
        if (execSpec.getWorkingDir() != null) {
            msg += 'Working Dir:\n'
            msg += execSpec.getWorkingDir().absolutePath + '\n'
        }

        // Use 'Cause' instead of 'Caused by' to help distinguish from exceptions
        if (exception != null) {
            msg += 'Cause:\n'
            msg += exception.toString() + '\n'
        }

        // Stdout and stderr
        msg += stdOutAndErrToLogString(stdout, stderr)
        return msg
    }

    static String stdOutAndErrToLogString(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return 'Standard Output:\n' +
                stdout.toString() + '\n' +
                'Error Output:\n' +
                stderr.toString()
    }

    static boolean isProjectExecNonZeroExit(Exception exception) {
        return (exception instanceof InvalidUserDataException) &&
               // TODO: improve indentification of non-zero exits?
               (exception?.getCause() instanceof ExecException)
    }

    // Sync main and or test resources, deleting destination directory and then recreating it
    // TODO: make the sync more efficient, e.g. Gradle Sync task or rsync
    static WorkResult syncResourcesTo(Project proj, List<String> sourceSetNames, File destDir) {
        projectDelete(proj, destDir)
        projectMkDir(proj, destDir)
        return projectCopy(proj, {
            sourceSetNames.each { String sourceSetName ->
                assert sourceSetName in ['main', 'test']
                srcSet(proj, sourceSetName, 'resources').srcDirs.each {
                    from it
                }
            }
            into destDir
        })
    }

    /**
     * Copy content to directory by calling project.copy(closure)
     *
     * Must be called instead of project.copy(...) to allow mocking of project calls in testing.
     *
     * @param proj Calls proj.copy {...} method
     * @param closure CopySpec closure
     */
    // See projectExec for explanation of the code
    @CompileStatic(TypeCheckingMode.SKIP)
    static WorkResult projectCopy(Project proj,
                                  @ClosureParams(value = SimpleType.class, options = "org.gradle.api.file.CopySpec")
                                  @DelegatesTo(CopySpec)
                                          Closure closure) {
        proj.copy {
            (delegate as CopySpec).with closure
        }
    }

    /**
     * Delete a directory by calling project.delete(...)
     *
     * Must be called instead of project.delete(...) to allow mocking of project calls in testing.
     *
     * @param proj Calls proj.delete(...) method
     * @param paths Variable length list of paths to be deleted, can be String or File
     */
    // See projectExec for explanation of the code
    @CompileStatic(TypeCheckingMode.SKIP)
    static boolean projectDelete(Project proj, Object... paths) {
        return proj.delete(paths)
    }

    /**
     * Delete a directory and recreate it using project.delete(...) and project.mkdir(...)
     *
     * Must be called instead of project.delete(...) to allow mocking of project calls in testing.
     * May fail in the case where the parent directory doesn't exist. This is because it uses
     * Project.mkdir(...) instead of File.mkdirs(...). Note that if the parameter is an
     * @OutputDirectory, then the directory is automatically created before the task runs.
     *
     * @param proj Calls proj.delete(...) method and then project.mkdir(...)
     * @param paths Variable length list of paths to be deleted, can be String or File
     */
    // See projectExec for explanation of the code
    @CompileStatic(TypeCheckingMode.SKIP)
    static boolean projectClearDir(Project proj, File path) {
        proj.delete(path)
        proj.mkdir(path)
    }

    /**
     * Executes command line and returns result by calling project.exec(...)
     *
     * Throws exception if command fails or non-null regex doesn't match stdout or stderr.
     * The exceptions have detailed information on command line, stdout, stderr and failure cause.
     * Must be called instead of project.exec(...) to allow mocking of project calls in testing.
     *
     * @param proj Calls proj.exec {...} method
     * @param stdout To capture standard output
     * @param stderr To capture standard output
     * @param matchRegexOutputsRequired Throws exception if stdout/stderr don't match regex.
     *        Matches each OutputStream separately, not in combination. Ignored if null.
     * @param closure ExecSpec type for proj.exec {...} method
     * @return ExecResult from the method
     */
    // See http://melix.github.io/blog/2014/01/closure_param_inference.html
    //
    // TypeCheckingMode.SKIP allows Project.exec to be mocked via metaclass in TestingUtils.groovy.
    // ClosureParams allows type checking to enforce that the first param ('it') to the Closure is
    // an ExecSpec. DelegatesTo allows type checking to enforce that the delegate is ExecSpec.
    // Together this emulates the functionality of ExecSpec.with(Closure).
    //
    // We are using a non-API-documented assumption that the delegate is an ExecSpec.  If the
    // implementation changes, this will fail at runtime.
    // TODO: In Gradle 2.5, we can switch to strongly-typed Actions, like:
    // https://docs.gradle.org/2.5/javadoc/org/gradle/api/Project.html#copy(org.gradle.api.Action)
    @CompileStatic(TypeCheckingMode.SKIP)
    static ExecResult projectExec(
            Project proj,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream stderr,
            @Nullable String matchRegexOutputsRequired,
            @ClosureParams(value = SimpleType.class, options = "org.gradle.process.ExecSpec")
            @DelegatesTo(ExecSpec)
                    Closure closure) {

        ExecSpec execSpec = null
        ExecResult execResult
        boolean execSucceeded = false

        try {
            execResult = proj.exec {
                execSpec = delegate as ExecSpec
                (execSpec).with closure
            }
            execSucceeded = true
            if (matchRegexOutputsRequired) {
                if (!matchRegexOutputs(stdout, stderr, matchRegexOutputsRequired)) {
                    // Exception thrown here to output command line
                    throw new InvalidUserDataException(
                            'Unable to find expected expected output in stdout or stderr\n' +
                            'Failed Regex Match: ' + escapeSlashyString(matchRegexOutputsRequired))
                }
            }

        } catch (Exception exception) {  // NOSONAR
            // ExecException is most common, which indicates "non-zero exit"
            String exceptionMsg = projectExecLog(execSpec, stdout, stderr, execSucceeded, exception)
            throw new InvalidUserDataException(exceptionMsg, exception)
        }

        log.debug(projectExecLog(execSpec, stdout, stderr, execSucceeded, null))

        return execResult
    }

    /**
     * Delete a directory by calling project.mkdir(...)
     *
     * Must be called instead of project.mkdir(...) to allow mocking of project calls in testing.
     *
     * @param proj Calls proj.mkdir(...) method
     * @param paths Variable length list of paths to be deleted, can be String or File
     */
    // See projectExec for explanation of the code
    @CompileStatic(TypeCheckingMode.SKIP)
    static boolean projectMkDir(Project proj, Object path) {
        return proj.mkdir(path)
    }

    static FileCollection mapSourceFiles(Project proj, FileCollection files,
                                         Map<String, String> sourceMapping) {
        for (String before : sourceMapping.keySet()) {
            if (files.contains(proj.file(before))) {
                // Replace the before file with the after file.
                files = files.minus(proj.files(before)).plus(
                        proj.files(sourceMapping.get(before)))
            }
        }
        return files
    }


    // Max number of characters for OS command line
    static int maxArgs() {
        if (isMacOSX()) {
            // http://www.in-ulm.de/~mascheck/various/argmax/
            return 262144
        }
        if (isWindows()) {
            // Assume Windows XP or later which has max of 8191
            // https://support.microsoft.com/en-us/kb/830473
            return 8191
        }
        if (isLinux()) {
            // v2.6.23 (released 2007) or later limit is 1/4 of stack size,
            // so Linux is presumed to have no limit
            // http://www.in-ulm.de/~mascheck/various/argmax/
            return Integer.MAX_VALUE
        }
        assert false
    }
}
