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
import groovy.transform.EqualsAndHashCode
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher

/**
 * Updates the Xcode project with j2objc generated files and resources.
 * <p/>
 * This uses the CocoaPods dependency system. For more details see
 * https://cocoapods.org/.
 * <p/>
 * It creates a podspec file and inserts it into your project's pod file.
 * If you haven't create a pod file yet you have to run `pod init` in your
 * project folder before you run this task.
 */
@CompileStatic
class XcodeTask extends DefaultTask {


    public static final String targetStartRegex = /^\s*target\s+'([^']*)'\s+do\s*$/
    public static final String targetNamedRegex = /^\s*target\s+'TARGET'\s+do\s*$/
    public static final String targetEndRegex = /^\s*end\s*/

    public static final String podMethodsStart = "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block"
    public static final String podMethodsEnd = "# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END"
    public static final String podMethodStartRegexOLD = /^\s*((def\s*j2objc_)|(# J2ObjC Gradle Plugin)).*/

    public static final String neverMatchesRegex = /a^/  // http://stackoverflow.com/a/940840/1509221

    @Input @Optional
    String getXcodeProjectDir() { return J2objcConfig.from(project).xcodeProjectDir }

    @Input
    boolean getXcodeTargetsManualConfig() { return J2objcConfig.from(project).xcodeTargetsManualConfig }

    boolean isTaskActive() { return getXcodeProjectDir() != null }

    @Input
    // List of all dependencies
    List<PodspecDetails> getPodspecDependencies() {
        if (!isTaskActive()) {
            // Optimization for only calculating dependencies where needed
            return []
        }
        return getPodspecDependencies(getProject(), new HashSet<Project>())
    }

    @Input
    List<String> getXcodeTargetsIos() { return J2objcConfig.from(project).xcodeTargetsIos }
    @Input
    List<String> getXcodeTargetsOsx() { return J2objcConfig.from(project).xcodeTargetsOsx }
    @Input
    List<String> getXcodeTargetsWatchos() { return J2objcConfig.from(project).xcodeTargetsWatchos }

    @Input
    String getMinVersionIos() { return J2objcConfig.from(project).minVersionIos }
    @Input
    String getMinVersionOsx() { return J2objcConfig.from(project).minVersionOsx }
    @Input
    String getMinVersionWatchos() { return J2objcConfig.from(project).minVersionWatchos }

    // TODO: remove '2' suffix
    // The '2' suffix here is purely to get around what appears to be a groovy compiler bug.
    // groovy output includes: "BUG! exception in phase 'instruction selection'"
    @Input
    List<String> getXcodeDebugConfigurations2() { return J2objcConfig.from(project).xcodeDebugConfigurations }
    @Input
    List<String> getXcodeReleaseConfigurations2() { return J2objcConfig.from(project).xcodeReleaseConfigurations }


    @OutputFile
    File getPodfileFile() {
        return project.file(new File(getXcodeProjectDir(), '/Podfile'))
    }

    @EqualsAndHashCode
    // Must be serializable to be used as an @Input
    static class PodspecDetails implements Serializable {
        // Increment this when the serialization output changes
        private static final long serialVersionUID = 1L;

        String projectName
        File podspecDebug
        File podspecRelease
        List<String> xcodeDebugConfigurations
        List<String> xcodeReleaseConfigurations

        PodspecDetails(String projectNameIn, File podspecDebugIn, File podspecReleaseIn,
                       List<String> xcodeDebugConfigurationsIn, List<String> xcodeReleaseConfigurationsIn) {
            projectName = projectNameIn
            podspecDebug = podspecDebugIn
            podspecRelease = podspecReleaseIn
            xcodeDebugConfigurations = xcodeDebugConfigurationsIn
            xcodeReleaseConfigurations = xcodeReleaseConfigurationsIn
        }

        String getPodMethodName() {
            // Valid Ruby name requires replacing all non-alphanumeric characters with underscore
            return "j2objc_$projectName".toString().replaceAll(/[^a-zA-Z0-9]/, '_')
        }

        @SuppressWarnings(['unused', 'grvy:org.codenarc.rule.unused.UnusedPrivateMethodRule'])
        private static void writeObject(ObjectOutputStream s) throws IOException {
            s.defaultWriteObject();
        }

        @SuppressWarnings(['unused', 'grvy:org.codenarc.rule.unused.UnusedPrivateMethodRule'])
        private static void readObject(ObjectInputStream s) throws IOException {
            s.defaultReadObject();
        }
    }

    static class XcodeTargetDetails {
        List<String> xcodeTargetsIos
        List<String> xcodeTargetsOsx
        List<String> xcodeTargetsWatchos
        String minVersionIos
        String minVersionOsx
        String minVersionWatchos

        XcodeTargetDetails(
                List<String> xcodeTargetsIosIn, List<String> xcodeTargetsOsxIn, List<String> xcodeTargetsWatchosIn,
                String minVersionIosIn, String minVersionOsxIn, String minVersionWatchosIn) {
            xcodeTargetsIos = xcodeTargetsIosIn
            xcodeTargetsOsx = xcodeTargetsOsxIn
            xcodeTargetsWatchos = xcodeTargetsWatchosIn
            minVersionIos = minVersionIosIn
            minVersionOsx = minVersionOsxIn
            minVersionWatchos = minVersionWatchosIn
        }
    }


    @TaskAction
    void xcodeConfig() {
        Utils.requireMacOSX('j2objcXcode task')

        if (!isTaskActive()) {
            logger.debug("j2objcXcode task disabled for ${project.name}")
            return
        }

//        // TODO: figure out how to display error when not configured on root project
//        String message =
//                "xcodeProjectDir need to be configured in ${project.name}'s build.gradle.\n" +
//                "The directory should point to the location containing your Xcode project,\n" +
//                "including the IOS-APP.xccodeproj file.\n" +
//                "\n" +
//                "j2objcConfig {\n" +
//                "    xcodeProjectDir '../ios'\n" +
//                "}\n" +
//                "\n" +
//                "Alternatively disable the j2objcXcode task if you wish to do your own Xcode build.\n"
//        "Also see the guidelines for the folder structure:\n" +
//        "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#what-is-the-recommended-folder-structure-for-my-app\n"
//        throw new InvalidUserDataException(message)

        // link the podspec in pod file
        File podfile = getPodfileFile()
        if (!podfile.exists()) {

            // TODO: offer to run the setup commands
            String xcodeAbsPath = project.file(getXcodeProjectDir()).absolutePath
            String message =
                    "No podfile exists in the xcodeProjectDir directory:\n" +
                    "    ${podfile.path}\n" +
                    "\n" +
                    "To fix this:\n" +
                    "\n" +
                    "1) Set xcodeProjectDir to the directory containing 'IOS-APP.xcodeproj':\n" +
                    "    current value: ${getXcodeProjectDir()}\n" +
                    "    absolute path: $xcodeAbsPath\n" +
                    "\n" +
                    "2) Within that directory, create the Podfile with:\n" +
                    "    (cd $xcodeAbsPath && pod init)\n" +
                    "\n" +
                    "If the pod command isn't found, then install CocoaPods:\n" +
                    "    sudo gem install cocoapods\n" +
                    "\n" +
                    "NOTE: After building, open the '.xcworkspace' file in Xcode. Opening '.xcodeproj' will fail.\n" +
                    "\n" +
                    "NOTE: When working with Swift, setup your bridging header:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-develop-with-swift"


            throw new InvalidUserDataException(message)
        }
        logger.debug("Pod exists at path: ${getXcodeProjectDir()}")

        // Write Podfile based on all the podspecs from dependent projects
        List<PodspecDetails> podspecDetailsList = getPodspecDependencies()

        XcodeTargetDetails xcodeTargetDetails = new XcodeTargetDetails(
                getXcodeTargetsIos(), getXcodeTargetsOsx(), getXcodeTargetsWatchos(),
                getMinVersionIos(), getMinVersionOsx(), getMinVersionWatchos())

        writeUpdatedPodfileIfNeeded(podspecDetailsList, xcodeTargetDetails, xcodeTargetsManualConfig, podfile)

        // install the pod
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        try {
            logger.debug('XcodeTask - projectExec - pod install:')
            Utils.projectExec(project, stdout, stderr, null, {
                setWorkingDir project.file(getXcodeProjectDir())
                executable 'pod'
                args 'install'
                setStandardOutput stdout
                setErrorOutput stderr
            })

        } catch (Exception exception) {  // NOSONAR
            if (exception.getMessage().find(
                    "A problem occurred starting process 'command 'pod''")) {
                String message =
                        exception.toString() +
                        '\n' +
                        'Please install CocoaPods to use j2objcXcode (https://cocoapods.org):\n' +
                        '    sudo gem install cocoapods'
                throw new InvalidUserDataException(message, exception)
            }
            // unrecognized errors are rethrown:
            throw exception
        }
    }


    /**
     * Retrieve a list of debug and release podspecs to update Xcode
     *
     * @param proj Root project from which to search transitive dependencies
     * @param visitedProjects Set of visited projects to avoid repeat visits
     * @return List of Files corresponding to debug / release pair of podspecs
     *         Even entries in the list are debug podspecs, odd for release podspecs
     */
    @VisibleForTesting
    List<PodspecDetails> getPodspecDependencies(Project proj, Set<Project> visitedProjects) {

        // Find podspecs generated by this project
        List<PodspecDetails> podspecs = new ArrayList<>()
        Task task = proj.tasks.getByName('j2objcPodspec')
        assert (task != null)
        PodspecTask j2objcPodspec = (PodspecTask) task

        logger.debug("${proj.getName()} project podspecs: " +
                     "${j2objcPodspec.getPodNameDebug()}, ${j2objcPodspec.getPodNameRelease()}")
        podspecs.add(new PodspecDetails(proj.getName(),
                j2objcPodspec.getPodspecDebug(), j2objcPodspec.getPodspecRelease(),
                getXcodeDebugConfigurations2(), getXcodeReleaseConfigurations2()))

        J2objcConfig j2objcConfig = proj.getExtensions().getByType(J2objcConfig)
        j2objcConfig.getBeforeProjects().each { Project beforeProject ->
            podspecs.addAll(getPodspecDependencies(beforeProject, visitedProjects))
        }

        return podspecs
    }

    /**
     * Extracts xcode targets in Podfile.
     */
    @VisibleForTesting
    static List<String> extractXcodeTargets(List<String> podfileLines) {
        List<String> xcodeTargets = new ArrayList<>()
        for (line in podfileLines) {
            Matcher matcher = (line =~ targetStartRegex)
            if (matcher.find()) {
                xcodeTargets.add(matcher.group(1))
            }
        }
        return xcodeTargets
    }

    /**
     * Strips certain lines from podfile.
     *
     * Stripping is applied from startRegex, stopping immediately before targetEndRegex,
     * the line is removed if and only if it matches stripRegex.
     * Throws if startRegex is found but not targetEndRegex.
     */
    @VisibleForTesting
    static List<String> regexStripLines(
            List<String> podfileLines,
            String startRegex, String endRegex, String stripRegex) {

        return regexModifyLines(
                podfileLines,
                false,  // insertAfterStart
                false,  // matchExactlyOnce
                true,  // preserveEndLine
                startRegex, endRegex, stripRegex, null)
    }

    /**
     * Replaces lines between two regexps, including start and end lines
     */
    @VisibleForTesting
    static List<String> regexReplaceLines(
            List<String> podfileLines, boolean preserveEndLine,
            String startRegex, String endRegex, List<String> newPodFileLines) {

        return regexModifyLines(
                podfileLines,
                true,  // insertAfterStart
                true,  // matchExactlyOnce
                preserveEndLine,
                startRegex,
                endRegex,
                /.*/,  // strip everything
                newPodFileLines)
    }

    /**
     * Insert new lines in to podfile between startRegex to targetEndRegex.
     *
     * Throws error if no match for startRegex or targetEndRegex.
     */
    @VisibleForTesting
    static List<String> regexInsertLines(
            List<String> podfileLines, boolean insertAfterStart,
            String startRegex, String endRegex, List<String> insertLines) {

        return regexModifyLines(
                podfileLines,
                insertAfterStart,
                true,  // matchExactlyOnce
                false,  // preserveEndLine
                startRegex,
                endRegex,
                neverMatchesRegex,  // no striping of anything, so no replacement
                insertLines)
    }

    /**
     * Insert new lines in to podfile before line
     *
     * Throws error if no match for regex.
     */
    @VisibleForTesting
    static List<String> regexInsertLinesBefore(
            List<String> podfileLines, String insertBeforeRegex, List<String> insertLines) {

        return regexModifyLines(
                podfileLines,
                false,  // insertAfterStart - insert before targetEndRegex
                true,  // matchExactlyOnce
                true,  // preserveEndLine
                /.*/,  // startRegex
                insertBeforeRegex,  // targetEndRegex
                neverMatchesRegex,  // no striping of anything, so no replacement
                insertLines)
    }

    /**
     * Modify Podfile lines between startRegex to targetEndRegex.
     * @param podfileLines to be modified
     * @param insertAfterStart true to add after startRegex, false to add before targetEndRegex
     * @param matchExactlyOnce throw error if no startRegex / targetEndRegex match found
     * @param preserveEndLine so retain even if when it matches stripRegex
     * @param startRegex to match line against
     * @param endRegex to match line against
     * @param insertLines to be added, can be null
     *
     * @throws InvalidUserDataException for unpaired startRegex / targetEndRegex or no matches
     * when matchExactlyOnce is true
     */
    @VisibleForTesting
    static List<String> regexModifyLines(
            List<String> podfileLines,
            boolean insertAfterStart, boolean matchExactlyOnce, boolean preserveEndLine,
            String startRegex, String endRegex, String stripRegex, List<String> insertLines) {

        List<String> result = new ArrayList<>()
        boolean active = false
        boolean done = false

        for (line in podfileLines) {
            if (done) {
                result.add(line)
            } else {
                boolean startMatch = false
                boolean endMatch = false

                if (!active) {
                    (line =~ startRegex).find() {
                        active = true
                        startMatch = true
                        assert !done
                    }
                } else {
                    // active == true
                    (line =~ endRegex).find() {
                        endMatch = true
                        // Insert lines before end
                        if (!insertAfterStart && insertLines != null) {
                            result.addAll(insertLines)
                        }
                    }
                }

                // Drop lines in active section when matches stripRegex
                if (!active || !(line =~ stripRegex).find() || (preserveEndLine && endMatch)) {
                    result.add(line)
                }

                // Insert lines after start
                if (insertAfterStart && startMatch && insertLines != null) {
                    result.addAll(insertLines)
                }

                if (endMatch) {
                    // Now inactive again
                    active = false
                    if (matchExactlyOnce) {
                        done = true
                    }
                }
            }
        }

        if (active) {
            throw new InvalidUserDataException(
                    "Failed to find targetEndRegex: ${Utils.escapeSlashyString(endRegex)}\n" +
                    podfileLines.join('\n') + "\n" +
                    "\n" +
                    "For a complex podfile, it may need manual configuration:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-manually-configure-the-cocoapods-podfile")
        }
        if (matchExactlyOnce && !done) {
            throw new InvalidUserDataException(
                    "Failed to find startRegex: ${Utils.escapeSlashyString(startRegex)}\n" +
                    podfileLines.join('\n') + "\n" +
                    "\n" +
                    "For a complex podfile, it may need manual configuration:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-manually-configure-the-cocoapods-podfile")
        }
        return result
    }

    /**
     * Checks if Podfile line matches regex
     */
    @VisibleForTesting
    static boolean regexMatchesLine(List<String> podfileLines, String regex) {
        return podfileLines.any { String line ->
            return (line =~ regex)
        }
    }

    /**
     * Adds new pod method when podfile has never had pod method before
     *
     * Most likely added before "target 'IOS-APP' do". If no 'correct'
     * place is found, it will be appended at the end of the podfile.
     */
    @VisibleForTesting
    static List<String> addNewPodMethod(List<String> podfileLines, List<String> insertLines) {

        println "addNewPodMethod: $insertLines"

        List<String> preambleLines = [
            'source ',
            'platform ',
            'xcodeproj ',
            'workspace ',
            'inhibit_all_warnings!',
            'use_frameworks!',
            'pod ',
            '#']  // comment line

        // Default is to add at the end of the Podfile
        int insertIndex = podfileLines.size()

        int lineIdx = 0
        for (podfileLine in podfileLines) {
            boolean matchesPreambleLine = false
            String trimmed = podfileLine.trim()
            for (String preambleLine in preambleLines) {
                if (trimmed.startsWith(preambleLine)) {
                    matchesPreambleLine = true
                    break
                }
            }

            if (!matchesPreambleLine && !trimmed.isEmpty()) {
                insertIndex = lineIdx
                break
            }
            lineIdx++
        }

        // Prompts insert after end of the array
        podfileLines.addAll(insertIndex, insertLines)
        return podfileLines
    }

    /**
     * Modify in place the existing podfile.
     */
    @VisibleForTesting
    static void writeUpdatedPodfileIfNeeded(
            List<PodspecDetails> podspecDetailsList,
            XcodeTargetDetails xcodeTargetDetails,
            boolean xcodeTargetsManualConfig,
            File podfile) {

        List<String> oldPodfileLines = podfile.readLines()
        List<String> newPodfileLines = new ArrayList<String>(oldPodfileLines)

        newPodfileLines = updatePodfile(
                newPodfileLines, podspecDetailsList, xcodeTargetDetails, xcodeTargetsManualConfig, podfile)

        // Write file only if it's changed
        if (!oldPodfileLines.equals(newPodfileLines)) {
            podfile.write(newPodfileLines.join('\n'))
        }
    }

    @VisibleForTesting
    static List<String> updatePodfile(
            List<String> podfileLines,
            List<PodspecDetails> podspecDetailsList,
            XcodeTargetDetails xcodeTargetDetails,
            boolean xcodeTargetsManualConfig,
            File podfile) {

        List<String> newPodfileLines

        boolean xcodeTargetsAllEmpty =
                xcodeTargetDetails.xcodeTargetsIos.isEmpty() &&
                xcodeTargetDetails.xcodeTargetsOsx.isEmpty() &&
                xcodeTargetDetails.xcodeTargetsWatchos.isEmpty()

        if (xcodeTargetsManualConfig) {
            if (!xcodeTargetsAllEmpty) {
                throw new InvalidUserDataException(
                        "Xcode targets and versions must all be blank when using xcodeTargetsManualConfig.\n" +
                        "Please update j2objcConfig in your gradle file by removing:\n" +
                        "1) xcodeTargetsIos, xcodeTargetsOsx & xcodeTargetsWatchos\n" +
                        "2) minVersionIos, minVersionOsx & minVersionWatchos")
            }
            newPodfileLines = podfileLines
        } else {
            // xcodeTargetsManualConfig = false  (default)
            List<String> podfileTargets = extractXcodeTargets(podfileLines)
            verifyTargets(xcodeTargetDetails.xcodeTargetsIos, podfileTargets, 'xcodeTargetsIos')
            verifyTargets(xcodeTargetDetails.xcodeTargetsOsx, podfileTargets, 'xcodeTargetsOsx')
            verifyTargets(xcodeTargetDetails.xcodeTargetsWatchos, podfileTargets, 'xcodeTargetsWatchos')

            if (xcodeTargetsAllEmpty) {
                if (podfileTargets.isEmpty()) {
                    // No targets found indicates complex podfile
                    throw new InvalidUserDataException(
                            "You must configure the xcode targets for the J2ObjC Gradle Plugin.\n" +
                            "The Plugin was unable to find the targets (regex matching ${Utils.escapeSlashyString(targetStartRegex)}),\n" +
                            "so you will need to manually configure this. See instructions:\n" +
                            "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-manually-configure-the-cocoapods-podfile")
                } else {
                    // Common case
                    throw new InvalidUserDataException(
                            "You must configure the xcode targets for the J2ObjC Gradle Plugin.\n" +
                            "It must be a subset of the valid targets: '${podfileTargets.join("', '")}'\n" +
                            "\n" +
                            "j2objcConfig {\n" +
                            "    xcodeTargetsIos 'IOS-APP', 'IOS-APPTests'  // example\n" +
                            "}\n" +
                            "\n" +
                            "Can be optionally configured for xcodeTargetsOsx and xcodeTargetsWatchos\n" +
                            "\n" +
                            "NOTE: if your Podfile is too complex, you may need manual configuration:\n" +
                            "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-manually-configure-the-cocoapods-podfile")
                }
            }
            newPodfileLines = updatePodfileTargets(podfileLines, podspecDetailsList, xcodeTargetDetails)
        }

        // update pod methods
        newPodfileLines = updatePodMethods(newPodfileLines, podspecDetailsList, podfile)

        return newPodfileLines
    }

    private static verifyTargets(List<String> xcodeTargets, List<String> podfileTargets, xcodeTargetsName) {
        xcodeTargets.each { String xcodeTarget ->
            if (! podfileTargets.contains(xcodeTarget)) {
                if (podfileTargets.isEmpty()) {
                    throw new InvalidUserDataException(
                            "Invalid j2objcConfig { $xcodeTargetsName '$xcodeTarget' }\n" +
                            "The J2ObjC Gradle Plugin can't determine the possible targets,\n" +
                            "so this may need manual configuration:\n" +
                            "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-manually-configure-the-cocoapods-podfile")
                } else {
                    // Should be more common error
                    throw new InvalidUserDataException(
                            "Invalid j2objcConfig { $xcodeTargetsName '$xcodeTarget' }\n" +
                            "Must be one of the valid targets: '${podfileTargets.join("', '")}'")
                }
            }
        }
    }

    @VisibleForTesting
    static List<String> updatePodMethods(
            List<String> podfileLines, List<PodspecDetails> podspecDetailsList, File podfile) {

        // create new methods
        List<String> insertLines = new ArrayList<>()
        insertLines.add(podMethodsStart)
        podspecDetailsList.each { PodspecDetails podspecDetails ->
            insertLines.addAll(podMethodLines(podspecDetails, podfile))
        }
        insertLines.add(podMethodsEnd)

        // New Format => update in place
        if (regexMatchesLine(podfileLines, podMethodsStart)) {
            return regexReplaceLines(podfileLines, false, podMethodsStart, podMethodsEnd, insertLines)
        }

        // Old format => update to new format (occurs for v0.5.0 => v0.5.1)
        if (regexMatchesLine(podfileLines, podMethodStartRegexOLD)) {
            insertLines.add('')
            return regexReplaceLines(podfileLines, true, podMethodStartRegexOLD, targetStartRegex, insertLines)
        }

        // No existing podMethod, add blank lines to wrap then insert
        insertLines.add(0, '')
        insertLines.add('')
        return addNewPodMethod(podfileLines, insertLines)
    }

    @VisibleForTesting
    static List<String> podMethodLines(
            PodspecDetails podspecDetails, File podfile) {

        // Inputs:
        //   podNameMethod:     j2objc_PROJECT
        //   podfile:           /SRC/ios/Podfile
        //   podspecDebug:      /SRC/PROJECT/j2objc-PROJECT-debug.podspec
        // Output:
        //   podspecNameDebug:  j2objc-PROJECT-debug
        //   pathDebug:         ../PROJECT/build   (relative path to podfile)
        String podspecDebugName = podspecDetails.podspecDebug.getName()
        String podspecReleaseName = podspecDetails.podspecRelease.getName()
        assert podspecDebugName.endsWith('.podspec') && podspecDebugName.endsWith('.podspec')
        podspecDebugName = podspecDebugName.replace('.podspec', '')
        podspecReleaseName = podspecReleaseName.replace('.podspec', '')

        // Relative paths are between parent directories
        String pathDebug = Utils.relativizeNonParent(podfile.getParentFile(), podspecDetails.podspecDebug.getParentFile())
        String pathRelease = Utils.relativizeNonParent(podfile.getParentFile(), podspecDetails.podspecRelease.getParentFile())

        // Search for pod within the xcodeTarget, until "end" is found for that target
        // Either update pod line in place or add line if pod doesn't exist
        List<String> podMethodLines = new ArrayList<>()
        podMethodLines.add("def ${podspecDetails.getPodMethodName()}".toString())
        if (!podspecDetails.xcodeDebugConfigurations.isEmpty()) {
            String configs = Utils.toQuotedList(podspecDetails.xcodeDebugConfigurations)
            podMethodLines.add("    pod '$podspecDebugName', :configuration => [$configs], :path => '$pathDebug'".toString())
        }
        if (!podspecDetails.xcodeReleaseConfigurations.isEmpty()) {
            String configs = Utils.toQuotedList(podspecDetails.xcodeReleaseConfigurations)
            podMethodLines.add("    pod '$podspecReleaseName', :configuration => [$configs], :path => '$pathRelease'".toString())
        }
        podMethodLines.add("end")
        return podMethodLines
    }

    /**
     * Add a podspec to a podfile. Update in place if it already exists.
     *
     * @param addPodline if false then remove existing line if any
     * @return updated copy of Podfile (may be identical to input)
     */
    @VisibleForTesting
    static List<String> updatePodfileTargets(
            List<String> podfileLines,
            List<PodspecDetails> podspecDetailsList,
            XcodeTargetDetails xcodeTargetDetails) {

        // Strip the following:
        // 1) pod method calls
        // 2) v0.4.3 and earlier inlined pod methods
        // 3) 'platform :' lines for ios, osx & watchos
        String stripRegex = /^\s*((j2objc_)|(pod 'j2objc)|(platform\s)).*/

        List<String> newPodfileLines =
                regexStripLines(podfileLines, targetStartRegex, targetEndRegex, stripRegex)

        List<String> insertLines = new ArrayList<>()
        insertLines.add('    platform :INVALID')
        podspecDetailsList.each { PodspecDetails podspecDetails ->
            insertLines.add("    ${podspecDetails.getPodMethodName()}".toString())
        }

        xcodeTargetDetails.xcodeTargetsIos.each { String iosTarget ->
            insertLines.set(0, "    platform :ios, '${xcodeTargetDetails.minVersionIos}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', iosTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, targetEndRegex, insertLines)
        }
        xcodeTargetDetails.xcodeTargetsOsx.each { String osxTarget ->
            insertLines.set(0, "    platform :osx, '${xcodeTargetDetails.minVersionOsx}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', osxTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, targetEndRegex, insertLines)
        }
        xcodeTargetDetails.xcodeTargetsWatchos.each { String watchosTarget ->
            insertLines.set(0, "    platform :watchos, '${xcodeTargetDetails.minVersionWatchos}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', watchosTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, targetEndRegex, insertLines)
        }
        return newPodfileLines
    }
}
