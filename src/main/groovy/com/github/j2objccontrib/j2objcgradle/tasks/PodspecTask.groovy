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
import org.gradle.api.tasks.Input
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
class PodspecTask extends DefaultTask {

    // Generated ObjC source files and main resources
    // Not @InputDirectory as the podspec doesn't depend on the directory contents, only the path
    @Input
    File getDestSrcMainObjDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'objc')
    }
    // Not @InputDirectory as the podspec doesn't depend on the directory contents, only the path
    @Input
    File getDestSrcMainResourcesDirFile() {
        return J2objcConfig.from(project).getDestSrcDirFile('main', 'resources')
    }

    @Input
    String getJ2objcHome() { return Utils.j2objcHome(project) }

    @Input
    String getLibName() { return "${project.name}-j2objc" }

    // Default: build/j2objcOutputs
    // Podspec Requirements require this location:
    //     Podspecs should be located at the root of the repository, and paths to files should be specified relative
    //     to the root of the repository as well. File patterns do not support traversing the parent directory ( .. ).
    //     https://guides.cocoapods.org/syntax/podspec.html#group_file_patterns
    @Input
    File getDestPodspecDirFile() { return J2objcConfig.from(project).getDestPodspecDirFile() }
    @Input
    File getDestLibDirFile() { return J2objcConfig.from(project).getDestLibDirFile() }

    @Input
    String getPodNameDebug() { "j2objc-${project.name}-debug" }
    @Input
    String getPodNameRelease() { "j2objc-${project.name}-release" }

    @Input
    String getMinVersionIos() { return J2objcConfig.from(project).getMinVersionIos() }
    @Input
    String getMinVersionOsx() { return J2objcConfig.from(project).getMinVersionOsx() }
    @Input
    String getMinVersionWatchos() { return J2objcConfig.from(project).getMinVersionWatchos() }

    @OutputFile
    File getPodspecDebug() {
        return new File(getDestPodspecDirFile(), "${getPodNameDebug()}.podspec")
    }
    @OutputFile
    File getPodspecRelease() {
        return new File(getDestPodspecDirFile(), "${getPodNameRelease()}.podspec")
    }


    @TaskAction
    void podspecWrite() {

        // Absolute path for header include, relative path for resource include
        String headerIncludePath = getDestSrcMainObjDirFile().getAbsolutePath()

        // TODO: allow custom list of libraries
        // podspec paths must be relative to podspec file, which is in buildDir
        String resourceIncludePath = Utils.relativizeNonParent(getDestPodspecDirFile(), getDestSrcMainResourcesDirFile())
        // iOS packed libraries are shared with watchOS
        String libDirIosDebug = Utils.relativizeNonParent(getDestPodspecDirFile(), new File(getDestLibDirFile(), 'iosDebug'))
        String libDirIosRelease = Utils.relativizeNonParent(getDestPodspecDirFile(), new File(getDestLibDirFile(), 'iosRelease'))
        String libDirOsxDebug = Utils.relativizeNonParent(getDestPodspecDirFile(), new File(getDestLibDirFile(), 'x86_64Debug'))
        String libDirOsxRelease = Utils.relativizeNonParent(getDestPodspecDirFile(), new File(getDestLibDirFile(), 'x86_64Release'))

        validateNumericVersion(getMinVersionIos(), 'minVersionIos')
        validateNumericVersion(getMinVersionOsx(), 'minVersionOsx')
        validateNumericVersion(getMinVersionWatchos(), 'minVersionWatchos')

        String podspecContentsDebug =
                genPodspec(getPodNameDebug(), headerIncludePath, resourceIncludePath,
                        libDirIosDebug, libDirOsxDebug, libDirIosDebug,
                        getMinVersionIos(), getMinVersionOsx(), getMinVersionWatchos(),
                        getLibName(), getJ2objcHome())
        String podspecContentsRelease =
                genPodspec(getPodNameRelease(), headerIncludePath, resourceIncludePath,
                        libDirIosRelease, libDirOsxRelease, libDirIosRelease,
                        getMinVersionIos(), getMinVersionOsx(), getMinVersionWatchos(),
                        getLibName(), getJ2objcHome())

        Utils.projectMkDir(project, getDestPodspecDirFile())
        logger.debug("Writing debug podspec... ${getPodspecDebug()}")
        getPodspecDebug().write(podspecContentsDebug)
        logger.debug("Writing release podspec... ${getPodspecRelease()}")
        getPodspecRelease().write(podspecContentsRelease)
    }

    // Podspec references are relative to project.buildDir
    @VisibleForTesting
    static String genPodspec(String podname, String publicHeadersDir, String resourceDir,
                             String libDirIos, String libDirOsx, String libDirWatchos,
                             String minIosVersion, String minOsxVersion, String minWatchosVersion,
                             String libName, String j2objcHome) {

        // Relative paths for content referenced by CocoaPods
        validatePodspecPath(libDirIos, true)
        validatePodspecPath(libDirOsx, true)
        validatePodspecPath(libDirWatchos, true)
        validatePodspecPath(resourceDir, true)

        // Absolute paths for Xcode command line
        validatePodspecPath(j2objcHome, false)
        validatePodspecPath(publicHeadersDir, false)

        // TODO: CocoaPods strongly recommends switching from 'resources' to 'resource_bundles'
        // http://guides.cocoapods.org/syntax/podspec.html#resource_bundles

        // TODO: replace xcconfig with {pod|user}_target_xcconfig
        // See 'Split of xcconfig' from: http://blog.cocoapods.org/CocoaPods-0.38/

        // File and line separators assumed to be '/' and '\n' as podspec can only be used on OS X
        return "Pod::Spec.new do |spec|\n" +
               "  spec.name = '$podname'\n" +
               "  spec.version = '1.0'\n" +
               "  spec.summary = 'Generated by the J2ObjC Gradle Plugin.'\n" +
               "  spec.resources = '$resourceDir/**/*'\n" +
               "  spec.requires_arc = true\n" +
               "  spec.libraries = " +  // continuation of same line
               "'ObjC', 'guava', 'javax_inject', 'jre_emul', 'jsr305', 'z', 'icucore'\n" +
               "  spec.ios.vendored_libraries = '$libDirIos/lib${libName}.a'\n" +
               "  spec.osx.vendored_libraries = '$libDirOsx/lib${libName}.a'\n" +
               "  spec.watchos.vendored_libraries = '$libDirWatchos/lib${libName}.a'\n" +
               "  spec.xcconfig = {\n" +
               "    'HEADER_SEARCH_PATHS' => '$j2objcHome/include $publicHeadersDir'\n" +
               "  }\n" +
               "  spec.ios.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib'\n" +
               "  }\n" +
               "  spec.osx.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib/macosx'\n" +
               "  }\n" +
               "  spec.watchos.xcconfig = {\n" +
               "    'LIBRARY_SEARCH_PATHS' => '$j2objcHome/lib'\n" +
               "  }\n" +
                // http://guides.cocoapods.org/syntax/podspec.html#deployment_target
               "  spec.ios.deployment_target = '$minIosVersion'\n" +
               "  spec.osx.deployment_target = '$minOsxVersion'\n" +
               "  spec.watchos.deployment_target = '$minWatchosVersion'\n" +
               "  spec.osx.frameworks = 'ExceptionHandling'\n" +
               "end\n"
    }

    @VisibleForTesting
    void validateNumericVersion(String version, String type) {
        // Requires at least a major and minor version number
        Matcher versionMatcher = (version =~ /^[0-9]*(\.[0-9]+)+$/)
        if (!versionMatcher.find()) {
            logger.warn("Non-numeric version for $type: $version")
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
        if (relativeRequired && path.startsWith('../')) {
            // Pod references must be relative to podspec and not traverse parent, i.e. '../'
            // https://guides.cocoapods.org/syntax/podspec.html#group_file_patterns
            throw new InvalidUserDataException("Path can't traverse parent: $path")
        }
    }
}
