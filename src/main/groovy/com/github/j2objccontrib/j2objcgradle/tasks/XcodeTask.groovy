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
    public static final String podMethodStartRegex = /^\s*((def\s*j2objc_)|(# J2ObjC Gradle Plugin)).*/
    public static final String endRegex = /^\s*end\s*/

    @Input @Optional
    String getXcodeProjectDir() { return J2objcConfig.from(project).xcodeProjectDir }

    @Input
    boolean isOnlyAddJ2ObjcToPodfile() { return J2objcConfig.from(project).onlyAddJ2ObjcToPodfile }

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

        PodspecDetails(String projectNameIn, File podspecDebugIn, File podspecReleaseIn) {
            projectName = projectNameIn
            podspecDebug = podspecDebugIn
            podspecRelease = podspecReleaseIn
        }

        String getPodMethodName() {
            // Valid Ruby name requires replacing all non-alphanumeric characters with underscore
            return "j2objc_$projectName".replaceAll(/[^a-zA-Z0-9]/, '_')
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

        writeUpdatedPodfileIfNeeded(podspecDetailsList, xcodeTargetDetails,!isOnlyAddJ2ObjcToPodfile(), podfile)

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
                j2objcPodspec.getPodspecDebug(), j2objcPodspec.getPodspecRelease()))

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
     * Stripping is applied from startRegex, stopping immediately before endRegex,
     * the line is removed if and only if it matches stripRegex.
     * Throws if startRegex is found but not endRegex.
     */
    @VisibleForTesting
    static List<String> regexStripLines(List<String> podfileLines, boolean multipleMatches,
                                        String startRegex, String endRegex, String stripRegex) {
        List<String> result = new ArrayList<>()
        boolean active = false
        boolean completedFirstMatch = false

        for (line in podfileLines) {
            if (completedFirstMatch && !multipleMatches) {
                // Ignoring 2nd and later matches
                result.add(line)
            } else {
                if ((line =~ startRegex).find()) {
                    active = true
                }
                if ((line =~ endRegex).find()) {
                    active = false
                    completedFirstMatch = true
                }
                // strip line only within 'active' range
                if (!active || !(line =~ stripRegex).find()) {
                    result.add(line)
                }
            }
        }
        if (active) {
            throw new InvalidUserDataException(
                    "Failed to find endRegex: ${Utils.escapeSlashyString(endRegex)}\n" +
                    podfileLines.join('\n'))
        }
        return result
    }

    /**
     * Insert new lines in to podfile between startRegex to endRegex.
     *
     * Throws error for no match or multiple matches.
     */
    @VisibleForTesting
    static List<String> regexInsertLines(List<String> podfileLines, boolean insertAfterStart,
                                         String startRegex, String endRegex, List<String> insertLines) {
        List<String> result = new ArrayList<>()
        boolean active = false
        boolean done = false

        for (line in podfileLines) {
            if (done) {
                result.add(line)
            } else {
                boolean startFoundThisLoop = false
                (line =~ startRegex).find() {
                    active = true
                    startFoundThisLoop = true
                    assert !done
                }
                (line =~ endRegex).find() {
                    if (active) {
                        if (!insertAfterStart) {
                            result.addAll(insertLines)
                        }
                        active = false
                        done = true
                    }
                }
                result.add(line)

                if (startFoundThisLoop && insertAfterStart) {
                    result.addAll(insertLines)
                }
            }
        }

        if (active) {
            throw new InvalidUserDataException(
                    "Failed to find endRegex: ${Utils.escapeSlashyString(endRegex)}\n" +
                    podfileLines.join('\n'))
        }
        if (!done) {
            throw new InvalidUserDataException(
                    "Failed to find startRegex: ${Utils.escapeSlashyString(startRegex)}\n" +
                    podfileLines.join('\n'))
        }
        return result
    }

    /**
     * Modify in place the existing podfile.
     */
    @VisibleForTesting
    static void writeUpdatedPodfileIfNeeded(
            List<PodspecDetails> podspecDetailsList,
            XcodeTargetDetails xcodeTargetDetails, boolean updateTargets,
            File podfile) {

        List<String> oldPodfileLines = podfile.readLines()
        List<String> newPodfileLines = new ArrayList<String>(oldPodfileLines)

        newPodfileLines = updatePodfile(
                newPodfileLines, podspecDetailsList, xcodeTargetDetails,updateTargets, podfile)

        // Write file only if it's changed
        if (!oldPodfileLines.equals(newPodfileLines)) {
            podfile.write(newPodfileLines.join('\n'))
        }
    }

    @VisibleForTesting
    static List<String> updatePodfile(
            List<String> podfileLines,
            List<PodspecDetails> podspecDetailsList,
            XcodeTargetDetails xcodeTargetDetails,boolean updateTargets,
            File podfile) {

        if(updateTargets){
            List<String> podfileTargets = extractXcodeTargets(podfileLines)
            verifyTargets(xcodeTargetDetails.xcodeTargetsIos, podfileTargets, 'xcodeTargetsIos')
            verifyTargets(xcodeTargetDetails.xcodeTargetsOsx, podfileTargets, 'xcodeTargetsOsx')
            verifyTargets(xcodeTargetDetails.xcodeTargetsWatchos, podfileTargets, 'xcodeTargetsWatchos')

            if (xcodeTargetDetails.xcodeTargetsIos.isEmpty() &&
                xcodeTargetDetails.xcodeTargetsOsx.isEmpty() &&
                xcodeTargetDetails.xcodeTargetsWatchos.isEmpty()) {
                // Give example for configuring iOS as that's the common case
                throw new InvalidUserDataException(
                        "You must configure the xcode targets for the J2ObjC Gradle Plugin.\n" +
                        "It must be a subset of the valid targets: '${podfileTargets.join("', '")}'\n" +
                        "\n" +
                        "j2objcConfig {\n" +
                        "    xcodeTargetsIos 'IOS-APP', 'IOS-APPTests'  // example\n" +
                        "}\n" +
                        "\n" +
                        "Can be optionally configured for xcodeTargetsOsx and xcodeTargetsWatchos\n")
            }
        }

        // update pod methods
        List<String> newPodfileLines = updatePodMethods(podfileLines, podspecDetailsList, podfile)

        // update pod targets
        if(updateTargets){
             newPodfileLines = updatePodfileTargets(newPodfileLines, podspecDetailsList, xcodeTargetDetails)
        }

        return newPodfileLines
    }

    private static verifyTargets(List<String> xcodeTargets, List<String> podfileTargets, xcodeTargetsName) {
        xcodeTargets.each { String xcodeTarget ->
            if (! podfileTargets.contains(xcodeTarget)) {
                throw new InvalidUserDataException(
                        "Invalid j2objcConfig { $xcodeTargetsName '$xcodeTarget' }\n" +
                        "Must be one of the valid targets: '${podfileTargets.join("', '")}'")
            }
        }
    }

    @VisibleForTesting
    static List<String> updatePodMethods(
            List<String> podfileLines, List<PodspecDetails> podspecDetailsList, File podfile) {

        // strip all old methods
        // Note: use of preserveEndLine=true so that the targetStartRegex isn't removed
        List<String> newPodfileLines =
                regexStripLines(podfileLines, false, podMethodStartRegex, targetStartRegex, /.*/)

        // create new methods
        List<String> insertLines = new ArrayList<>()
        insertLines.add('# J2ObjC Gradle Plugin - DO NOT MODIFY from here to the first target')
        podspecDetailsList.each { PodspecDetails podspecDetails ->
            insertLines.addAll(podMethodLines(podspecDetails, podfile))
            insertLines.add('')
        }

        // insert new methods immediately before first target
        newPodfileLines = regexInsertLines(newPodfileLines, false, /.*/, targetStartRegex, insertLines)

        return newPodfileLines
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
        podMethodLines.add("    pod '$podspecDebugName', :configuration => ['Debug'], :path => '$pathDebug'".toString())
        podMethodLines.add("    pod '$podspecReleaseName', :configuration => ['Release'], :path => '$pathRelease'".toString())
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
                regexStripLines(podfileLines, true, targetStartRegex, endRegex, stripRegex)

        List<String> insertLines = new ArrayList<>()
        insertLines.add('    platform :INVALID')
        podspecDetailsList.each { PodspecDetails podspecDetails ->
            insertLines.add("    ${podspecDetails.getPodMethodName()}".toString())
        }

        xcodeTargetDetails.xcodeTargetsIos.each { String iosTarget ->
            insertLines.set(0, "    platform :ios, '${xcodeTargetDetails.minVersionIos}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', iosTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, endRegex, insertLines)
        }
        xcodeTargetDetails.xcodeTargetsOsx.each { String osxTarget ->
            insertLines.set(0, "    platform :osx, '${xcodeTargetDetails.minVersionOsx}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', osxTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, endRegex, insertLines)
        }
        xcodeTargetDetails.xcodeTargetsWatchos.each { String watchosTarget ->
            insertLines.set(0, "    platform :watchos, '${xcodeTargetDetails.minVersionWatchos}'".toString())
            String startTargetNamed = targetNamedRegex.replace('TARGET', watchosTarget)
            newPodfileLines = regexInsertLines(newPodfileLines, true, startTargetNamed, endRegex, insertLines)
        }
        return newPodfileLines
    }
}
