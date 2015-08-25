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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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

    // Generated ObjC source files and main resources
    // TODO: is this more than needed? Task only cares about directory location, not contents.
    @InputDirectory
    File getDestSrcMainObjDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'objc')
    }
    @InputDirectory
    File getDestSrcMainResourcesDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'resources')
    }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    File getDestLibDirFile() { return J2objcConfig.from(project).getDestLibDirFile() }

    @Input @Optional
    String getXcodeProjectDir() { return J2objcConfig.from(project).xcodeProjectDir }
    @Input @Optional
    String getXcodeTarget() { return J2objcConfig.from(project).xcodeTarget }

    @Input
    String getPodNameDebug() { "j2objc-${project.name}-debug" }
    @Input
    String getPodNameRelease() { "j2objc-${project.name}-release" }


    // CocoaPods podspec files that are referenced by the Podfile
    @OutputFile
    File getPodspecDebug() { new File(project.buildDir, "${getPodNameDebug()}.podspec") }
    @OutputFile
    File getPodspecRelease() { new File(project.buildDir, "${getPodNameRelease()}.podspec") }


    @OutputFile
    File getPodfileFile() {
        verifyXcodeArgs()
        return project.file(new File(getXcodeProjectDir(), '/Podfile'))
    }


    @TaskAction
    void xcodeConfig() {
        Utils.requireMacOSX('j2objcXcode task')

        verifyXcodeArgs()

        // podspec paths must be relative to podspec file, which is in buildDir
        // NOTE: toURI() adds trailing slash in production but not in unit tests
        URI buildDir = project.buildDir.toURI()
        String mainObjcRelativeToBuildDir = Utils.trimTrailingForwardSlash(
                buildDir.relativize(getDestSrcMainObjDirFile().toURI()).toString())
        String mainResourcesRelativeToBuildDir = Utils.trimTrailingForwardSlash(
                buildDir.relativize(getDestSrcMainResourcesDirFile().toURI()).toString())

        // TODO: make this an explicit @Input
        // Same for both debug and release builds
        String libName = "${project.name}-j2objc"

        // podspec creation
        // TODO: allow custom list of libraries
        String libDirDebug = new File(getDestLibDirFile(), '/iosDebug').absolutePath
        String libDirRelease = new File(getDestLibDirFile(), '/iosRelease').absolutePath

        String podspecContentsDebug =
                genPodspec(getPodNameDebug(), libDirDebug, libName, getJ2objcHome(),
                        mainObjcRelativeToBuildDir, mainResourcesRelativeToBuildDir)
        String podspecContentsRelease =
                genPodspec(getPodNameRelease(), libDirRelease, libName, getJ2objcHome(),
                        mainObjcRelativeToBuildDir, mainResourcesRelativeToBuildDir)

        logger.debug("Writing debug podspec... ${getPodspecDebug()}\n$podspecContentsDebug")
        getPodspecDebug().write(podspecContentsDebug)
        logger.debug("Writing release podspec... ${getPodspecDebug()}\n$podspecContentsDebug")
        getPodspecRelease().write(podspecContentsRelease)

        // link the podspec in pod file
        File podFile = getPodfileFile()
        if (!podFile.exists()) {
            // TODO: offer to run the setup commands
            String xcodeAbsPath = project.file(getXcodeProjectDir()).absolutePath
            String message =
                    "No podfile exists in the xcodeProjectDir directory:\n" +
                    "    ${podFile.path}\n" +
                    "\n" +
                    "The Podfile must be created with this command:\n" +
                    "    (cd $xcodeAbsPath && pod init)\n" +
                    "\n" +
                    "If the pod command isn't found, then install CocoaPods:\n" +
                    "    sudo gem install cocoapods"
            throw new InvalidUserDataException(message)
        } else {
            logger.debug("Pod exists at path: ${getXcodeProjectDir()}")
            // TODO: should use relative path, see if that's possible
            writeUpdatedPodFileIfNeeded(
                    podFile, getXcodeTarget(),
                    getPodNameDebug(), getPodNameRelease(),
                    project.buildDir.path)

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
    }

    @VisibleForTesting
    void verifyXcodeArgs() {
        if (getXcodeProjectDir() == null ||
            getXcodeTarget() == null) {
            String message =
                    "Xcode settings need to be configured in this project's build.gradle.\n" +
                    "The directory should point to the location containing your Xcode project,\n" +
                    "including the .xccodeproj and .xcworkspace files. The target is the name,\n" +
                    "of the iOS app within Xcode (not the tests or watch app target).\n" +
                    "\n" +
                    "j2objcConfig {\n" +
                    "    xcodeProjectDir '../ios'\n" +
                    "    xcodeTarget 'IOS-APP'\n" +
                    "}\n" +
                    "\n" +
                    "Also see the guidelines for the folder structure:\n" +
                    "https://github.com/j2objc-contrib/j2objc-gradle#folder-structure"
            throw new InvalidUserDataException(message)
        }
    }

    @VisibleForTesting
    static void validatePodspecPath(String path, boolean relativeRequired) {
        if (path.contains('//')) {
            throw new InvalidUserDataException("Path shouldn't have '//': $path")
        }
        if (path.endsWith('/')) {
            throw new InvalidUserDataException("Path shouldn't end with '/': $path")
        }
        if (path.endsWith('*')) {
            throw new InvalidUserDataException("Only genPodspec(...) should add '*': $path")
        }
        // Hack to recognize absolute path on Windows, only relevant in unit tests run on Windows
        boolean absolutePath = path.startsWith('/') ||
                               (path.startsWith('C:\\') && Utils.isWindowsNoFake())
        if (relativeRequired && absolutePath) {
            throw new InvalidUserDataException("Path shouldn't be absolute: $path")
        }
        if (!relativeRequired && !absolutePath) {
            throw new InvalidUserDataException("Path shouldn't be relative: $path")
        }
    }

    // Podspec references are relative to project.buildDir
    @VisibleForTesting
    static String genPodspec(String podname, String libDir, String libName, String j2objcHome,
                             String publicHeadersDir, String resourceDir) {

        // Absolute paths for Xcode command line
        validatePodspecPath(libDir, false)
        validatePodspecPath(j2objcHome, false)

        // Relative paths for content referenced by CocoaPods
        validatePodspecPath(publicHeadersDir, true)
        validatePodspecPath(resourceDir, true)

        // Line separator assumed to be "\n" as this task can only be run on a Mac
        // TODO: CocoaPods strongly recommends switching from 'resources' to 'resource_bundles'
        // http://guides.cocoapods.org/syntax/podspec.html#resource_bundles
        return "Pod::Spec.new do |spec|\n" +
               "  spec.name = '$podname'\n" +
               "  spec.version = '1.0'\n" +
               "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'\n" +
               "  spec.public_header_files = '$publicHeadersDir/**/*.h'\n" +
               "  spec.resources = '$resourceDir/**/*'\n" +
               "  spec.requires_arc = true\n" +
               // Avoid CocoaPods deleting files that don't match "*.h" in public_header_files
               "  spec.preserve_paths = '$publicHeadersDir/**/*'\n" +
               "  spec.libraries = " +  // continuation of same line
               "'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore', '$libName'\n" +
               "  spec.xcconfig = {\n" +
               "    'HEADER_SEARCH_PATHS' => '$j2objcHome/include',\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib $libDir'\n" +
               "  }\n" +
               "end\n"
    }

    /**
     * Extracts all target names that start with xcodeTarget.
     *
     * For xcodeTarget == 'IOS-APP', likely extracted names are:
     *   IOS-APP
     *   IOS-APPTests
     *   IOS-APP WatchKit App
     *   IOS-APP WatchKit Extension
     */
    @VisibleForTesting
    static List<String> extractXcodeTargets(String xcodeTarget, List<String> podFileLines) {
        List<String> xcodeTargets = new ArrayList<>()
        for (line in podFileLines) {
            Matcher matcher = (line =~ /^target '($xcodeTarget[^']*)' do$/)
            if (matcher.find()) {
                xcodeTargets.add(matcher.group(1))
            }
        }
        return xcodeTargets
    }

    /**
     * Modify in place the existing podFile.
     */
    @VisibleForTesting
    static void writeUpdatedPodFileIfNeeded(
            File podFile, String xcodeTarget,
            String podNameDebug, String podNameRelease, String podPath) {

        List<String> oldPodFileLines = podFile.readLines()
        List<String> newPodFileLines = new ArrayList<String>(oldPodFileLines)
        List<String> xcodeTargets = extractXcodeTargets(xcodeTarget, oldPodFileLines)

        if (! xcodeTargets.contains(xcodeTarget)) {
            throw new InvalidUserDataException(
                    "Can't find iOS App Target '$xcodeTarget' in Podfile: ${podFile.absolutePath}")
        }

        // Iterate over all the xcodeTargets for Debug and Release
        for (xcodeTargetName in xcodeTargets) {
            newPodFileLines = updatePodFileLines(
                    newPodFileLines, xcodeTargetName,
                    podNameDebug, ['Debug'], podPath)
            newPodFileLines = updatePodFileLines(
                    newPodFileLines, xcodeTargetName,
                    podNameRelease, ['Release'], podPath)
        }

        // Write file only if it's changed
        if (!oldPodFileLines.equals(newPodFileLines)) {
            podFile.write(newPodFileLines.join("\n"))
        }
    }

    /**
     * Add a podspec to a podfile. Update in place if it already exists.
     *
     * @return updated copy of podFile (may be identical to input)
     */
    @VisibleForTesting
    static List<String> updatePodFileLines(
            List<String> oldPodFileLines, String xcodeTarget,
            String podName, List<String> podConfigurations, String podPath) {

        List<String> newPodFileLines = new ArrayList<>()

        // Search for pod within the xcodeTarget, until "end" is found for that target
        // Either update pod line in place or add line if pod doesn't exist
        String podConfigStr = podConfigurations.collect { String podConfiguration ->
            assert podConfiguration in ['Debug', 'Release']
            return "'$podConfiguration'"
        }.join(", ")
        podConfigStr = "[$podConfigStr]"

        String podMatchedLine = "pod '$podName'"
        String newPodPathLine =
                "pod '$podName', :configuration => $podConfigStr, :path => '$podPath'"
        boolean withinXcodeTarget = false
        boolean podNameWritten = false

        oldPodFileLines.each { String line ->

            // Copies each line to newPodFileLines, unless skipped
            boolean skipWritingLine = false

            // Find xcodeTarget within single quote marks
            if (line.contains("'$xcodeTarget'")) {
                withinXcodeTarget = true

            } else if (withinXcodeTarget) {
                if (line.contains(podMatchedLine)) {
                    // skip copying this line
                    skipWritingLine = true
                    if (podNameWritten) {
                        // repeated podName lines, drop them as they should not be here
                    } else {
                        // write updated line the first time pod is seen
                        newPodFileLines += newPodPathLine
                        podNameWritten = true
                    }
                } else if (line.contains('end')) {
                    if (!podNameWritten) {
                        // no existing podName, so write that additional line
                        newPodFileLines += newPodPathLine
                        podNameWritten = true
                    }
                }
            }

            if (!skipWritingLine) {
                newPodFileLines += line
            }
        }

        if (!podNameWritten) {
            throw new InvalidUserDataException(
                    "Unable to modify PodFile, likely unable to find target $xcodeTarget.")
        }

        return newPodFileLines
    }
}
