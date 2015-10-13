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
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
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

    @Input @Optional
    String getXcodeProjectDir() { return J2objcConfig.from(project).xcodeProjectDir }
    @Input @Optional
    List<String> getXcodeTargets() { return J2objcConfig.from(project).xcodeTargets }

    @OutputFile
    File getPodfileFile() {
        verifyXcodeArgs()
        return project.file(new File(getXcodeProjectDir(), '/Podfile'))
    }

    static class PodspecDetails {
        PodspecDetails(String projectNameIn, File podspecDebugIn, File podspecReleaseIn) {
            projectName = projectNameIn
            podspecDebug = podspecDebugIn
            podspecRelease = podspecReleaseIn
        }

        String projectName
        File podspecDebug
        File podspecRelease

        String getPodMethodName() {
            return "j2objc_$projectName"
        }
    }


    @TaskAction
    void xcodeConfig() {
        Utils.requireMacOSX('j2objcXcode task')

        verifyXcodeArgs()

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
                    "    current value from j2objcConfig: ${getXcodeProjectDir()}\n" +
                    "    current value for absolute path: $xcodeAbsPath\n" +
                    "\n" +
                    "2) Within that directory, create the Podfile with:\n" +
                    "    (cd $xcodeAbsPath && pod init)\n" +
                    "\n" +
                    "If the pod command isn't found, then install CocoaPods:\n" +
                    "    sudo gem install cocoapods"
            throw new InvalidUserDataException(message)
        }
        logger.debug("Pod exists at path: ${getXcodeProjectDir()}")

        // Write Podfile based on all the podspecs from dependent projects
        List<PodspecDetails> podspecDetailsList =
                getPodspecsFromProject(getProject(), new HashSet<Project>())
        writeUpdatedPodfileIfNeeded(podspecDetailsList, getXcodeTargets(), podfile, logger)

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

        } catch (Exception exception) {
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

        // Warning to avoid breaking CocoaPods
        // Error: "library not found for -lPods-*-j2objc-shared"
        // See: https://github.com/j2objc-contrib/j2objc-gradle/issues/273
        logger.warn("NOTE: open the '.xcworkspace' file in Xcode. It will fail if you open the '.xcodeproj' file.")
        // Warning to aid setup when developing with Swift
        logger.warn("NOTE: when working with Swift, setup your bridging header:")
        logger.warn("https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-develop-with-swift")
    }


    /**
     * Retrieve a list of debug and release podspecs to update Xcode
     *
     * @param proj Root project from which to search transitive dependencies
     * @param visitedProjects Set of visited projects to avoid repeat visits
     * @return List of Files corresponding to debug / release pair of podspecs
     *         Even entries in the list are debug podspecs, odd for release podspecs
     */
    private List<PodspecDetails> getPodspecsFromProject(Project proj, Set<Project> visitedProjects) {

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
            podspecs.addAll(getPodspecsFromProject(beforeProject, visitedProjects))
        }

        return podspecs
    }

    @VisibleForTesting
    void verifyXcodeArgs() {
        if (getXcodeProjectDir() == null) {
            String message =
                    "xcodeProjectDir need to be configured in ${project.name}'s build.gradle.\n" +
                    "The directory should point to the location containing your Xcode project,\n" +
                    "including the IOS-APP.xccodeproj file.\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    xcodeProjectDir '../ios'\n" +
                    "}\n" +
                    "\n" +
                    "Also see the guidelines for the folder structure:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#what-is-the-recommended-folder-structure-for-my-app"
            throw new InvalidUserDataException(message)
        }
    }

    /**
     * Extracts all target names within the Podfile.
     *
     * For an app target named 'IOS-APP', likely target names are:
     *   IOS-APP
     *   IOS-APPTests
     *   IOS-APP WatchKit App
     *   IOS-APP WatchKit Extension
     */
    @VisibleForTesting
    static List<String> extractXcodeTargets(List<String> podfileLines) {
        List<String> xcodeTargets = new ArrayList<>()
        for (line in podfileLines) {
            Matcher matcher = (line =~ /^target '([^']*)' do$/)
            if (matcher.find()) {
                xcodeTargets.add(matcher.group(1))
            }
        }
        return xcodeTargets
    }

    /**
     * Modify in place the existing podfile.
     */
    @VisibleForTesting
    static void writeUpdatedPodfileIfNeeded(
            List<PodspecDetails> podspecDetailsList,
            List<String> xcodeTargets, File podfile, Logger logger) {

        List<String> oldPodfileLines = podfile.readLines()
        List<String> newPodfileLines = new ArrayList<String>(oldPodfileLines)

        podspecDetailsList.each { PodspecDetails podspecDetails ->
            newPodfileLines = updatePodfile(
                    newPodfileLines, podspecDetails,
                    xcodeTargets, podfile, logger)
        }

        // Write file only if it's changed
        if (!oldPodfileLines.equals(newPodfileLines)) {
            podfile.write(newPodfileLines.join('\n'))
        }
    }

    @VisibleForTesting
    static List<String> updatePodfile(
            List<String> oldPodfileLines, PodspecDetails podspecDetails,
            List<String> xcodeTargets, File podfile, Logger logger) {

        List<String> podfileTargets = extractXcodeTargets(oldPodfileLines)
        List<String> newPodfileLines = new ArrayList<String>(oldPodfileLines)

        // No targets set, then default to all
        if (xcodeTargets.size() == 0) {
            xcodeTargets = podfileTargets
            if (logger) {
                logger.debug("xcodeTargets default is all targets: '${podfileTargets.join(', ')}'")
            }
        } else {
            for (xcodeTarget in xcodeTargets) {
                if (! (xcodeTarget in podfileTargets)) {
                    // Error checking for zero or non-existent xcodeTargets
                    throw new InvalidUserDataException(
                            "Current xcodeTargets: xcodeTargets\n" +
                            "Valid xcodeTargets must be subset (likely all) of: $podfileTargets\n" +
                            "\n" +
                            "j2objcConfig {\n" +
                            "    xcodeTargets '${podfileTargets.join(', ')}'\n" +
                            "}\n")
                }
            }
        }

        // Install shared shared pod for multiple targets
        // http://natashatherobot.com/cocoapods-installing-same-pod-multiple-targets/
        newPodfileLines = updatePodfileMethod(
                newPodfileLines, podspecDetails, podfile)

        // Iterate over all podfileTargets as some may need to be cleared
        for (podfileTarget in podfileTargets) {
            boolean addPodMethod = podfileTarget in xcodeTargets
            newPodfileLines = updatePodfileTarget(
                    newPodfileLines, podfileTarget,
                    podspecDetails.getPodMethodName(), addPodMethod)
        }
        return newPodfileLines
    }

    private static List<String> updatePodfileMethod(
            List<String> oldPodfileLines, PodspecDetails podspecDetails, File podfile) {

            List<String> newPodfileLines = new ArrayList<>()
        boolean podMethodFound = false
        boolean podMethodProcessed = false

        // Extract podspec details. Example given for debug podspec
        //
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
        List<String> newPodMethodLines = new ArrayList<>()
        newPodMethodLines.add("def ${podspecDetails.getPodMethodName()}".toString())
        newPodMethodLines.add("    pod '$podspecDebugName', :configuration => ['Debug'], :path => '$pathDebug'".toString())
        newPodMethodLines.add("    pod '$podspecReleaseName', :configuration => ['Release'], :path => '$pathRelease'".toString())
        newPodMethodLines.add("end")

        oldPodfileLines.each { String line ->
            // Copies each line to newPodfileLines, unless skipped
            boolean skipWritingLine = false

            if (!podMethodProcessed) {
                // Look for pod method start: def j2objc_shared
                if (!podMethodFound) {
                    if (line.contains(newPodMethodLines.get(0))) {
                        podMethodFound = true
                    }
                }

                if (podMethodFound) {
                    // Generate new pod method contents each time
                    skipWritingLine = true

                    if (line.trim() == 'end') {
                        // End of old pod method
                        newPodfileLines.addAll(newPodMethodLines)
                        // Generate new pod method each time
                        podMethodProcessed = true
                    }
                }

                if (line.trim().startsWith("target '")) {
                    // pod method wasn't found, so needs to be written anyway
                    newPodfileLines.addAll(newPodMethodLines)
                    // Blank line
                    newPodfileLines.add('')
                    podMethodProcessed = true
                }
            }

            if (!skipWritingLine) {
                newPodfileLines.add(line)
            }
        }

        return newPodfileLines
    }

    /**
     * Add a podspec to a podfile. Update in place if it already exists.
     *
     * @param addPodline if false then remove existing line if any
     * @return updated copy of Podfile (may be identical to input)
     */
    @VisibleForTesting
    static List<String> updatePodfileTarget(
            List<String> oldPodfileLines, String xcodeTarget,
            String podNameMethod, boolean addPodMethod) {

        // Indent for aesthetic reasons in Podfile
        String podMethodLine = "    $podNameMethod"
        List<String> newPodfileLines = new ArrayList<>()
        boolean podMethodProcessed = false
        boolean withinXcodeTarget = false

        oldPodfileLines.each { String line ->

            // Copies each line to newPodfileLines, unless skipped
            boolean skipWritingLine = false

            // Find xcodeTarget within single quote marks
            if (line.contains("'$xcodeTarget'")) {
                withinXcodeTarget = true

            } else if (withinXcodeTarget) {

                // For upgrade from v0.4.3 to v0.5.0, basically for Xcode 7
                // TODO: remove code for plugin beta release as it's not necessary after upgrading
                if (line.contains("pod 'j2objc")) {
                    // Old pod lines that should be removed. Example:
                    // pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '/Users/USERNAME/dev/taptap/base/build'
                    skipWritingLine = true
                }

                if (line.contains(podNameMethod)) {
                    // skip copying this line
                    skipWritingLine = true
                    if (podMethodProcessed) {
                        // repeated podName lines, drop them as they should not be here
                    } else {
                        if (addPodMethod) {
                            // update existing pod method line
                            // finding existing line makes for stable in place ordering
                            newPodfileLines.add(podMethodLine)
                        }
                        // If addPodMethod = false, then this line is dropped
                        podMethodProcessed = true
                    }
                } else if (line.contains('end')) {
                    withinXcodeTarget = false
                    if (!podMethodProcessed) {
                        if (addPodMethod) {
                            // no existing pod method line, so add it
                            newPodfileLines.add(podMethodLine)
                        }
                        podMethodProcessed = true
                    }
                }
            }

            if (!skipWritingLine) {
                newPodfileLines.add(line)
            }
        }

        if (!podMethodProcessed) {
            throw new InvalidUserDataException(
                    "Unable to find Podfile target: $xcodeTarget")
        }

        return newPodfileLines
    }
}
