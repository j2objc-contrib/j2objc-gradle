# J2ObjC Gradle Plugin

Gradle Plugin for [J2ObjC](https://github.com/google/j2objc),
which is an open-source tool from Google that translates
Java source code to Objective-C for the iOS (iPhone/iPad) platform. The plugin is
not affiliated with Google but was developed by former Google Engineers and others.
J2ObjC enables Java source to be part of an iOS application's build, no editing
of the generated files is necessary. The goal is to write an app's non-UI code
(such as application logic and data models) in Java, which is then shared by
Android apps (natively Java), web apps (using GWT), and iOS apps (using J2ObjC).

[![Build Status](https://travis-ci.org/j2objc-contrib/j2objc-gradle.svg)](https://travis-ci.org/j2objc-contrib/j2objc-gradle)

Home Page: https://github.com/j2objc-contrib/j2objc-gradle

### Usage

At HEAD, this plugin is in a state of flux as we refactor it into a first-class
Gradle plugin for our beta. You may wish to wait for the beta release as we may make
backwards incompatible changes before that point.

You should start with a clean java only project without any Android dependencies.
It is suggested that the project is named `shared`. It must be buildable using the standard
[Gradle Java plugin](https://docs.gradle.org/current/userguide/java_plugin.html).
Starting as an empty project allows you to gradually shift over code from an existing
Android application. This is beneficial for separation between the application model
and user interface. It also allows the shared project to be easily used server side as well.

The Android app, shared Java project and Xcode should be sibling directories, i.e children
of the same root level folder. Suggested folder names are `'android', 'shared' and 'ios'`
respectively. See the section below on [Folder Structure](#folder-structure).

Configure `shared/build.gradle` in your Java only project:

```gradle
// File: shared/build.gradle
plugins {
    id 'java'
    id 'com.github.j2objccontrib.j2objcgradle' version '0.4.2-alpha'
}

// Plugin settings:
j2objcConfig {
    xcodeProjectDir '../ios'  // Xcode workspace directory (suggested directory name)
    xcodeTarget 'IOS-APP'     // iOS app target name (replace with existing app name)

    finalConfigure()          // Must be last call to configuration
}
```

Info on additional `j2objcConfig` settings are in [J2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy#L30).

Within the Android application's `android/build.gradle`, make it dependent on the `shared` project:

```gradle
// File: android/build.gradle
dependencies {
    compile project(':shared')
}
```


### Minimum Requirements

Gradle 2.4 is required for the compilation support within Gradle. The other necessities
are the [J2objc Requirements](http://j2objc.org/#requirements).

    * Gradle 2.4
    * JDK 1.7 or higher
    * Mac workstation or laptop
    * Mac OS X 10.9 or higher
    * Xcode 6 or higher


### J2ObjC Installation

Download the latest version from the [J2ObjC Releases](https://github.com/google/j2objc/releases).
Find (or add) the local.properties in your root folder and add the path to the unzipped folder:

```properties
# File: local.properties
j2objc.home=/J2OBJC_HOME
```


### Build Commands

The plugin will output the generated source and libaries to the `build/j2objcOutputs`
directory and run all tests. It is integrated with Gradle's Java build plugin and may
be run as follows:

    ./gradlew shared:build

During development, to build the libraries and update Xcode (skipping the tests):

    ./gradlew shared:j2objcXcode

For a complete build, run both:

    ./gradlew shared:build shared:j2objcXcode


### NOTE: Open .xcworkspace in Xcode

When using the `j2objcTask`, open the `.xcworkspace` file in Xcode. If the `.xcodeproj` file
is opened in Xcode then CocoaPods will fail. This will appear as an Xcode build time error:

    library not found for -lPods-IOS-APP-j2objc-shared

Also see the FAQ note on [developing with Swift](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/FAQ.md#how-do-i-develop-with-swift).


### Build Speed

You can reduce the build time by 50% by skipping the release binaries by adding the
following to your root level `local.properties` file:

```properties
j2objc.release.enabled=false
```

This is helpful for a tight modify-compile-test loop using only debug binaries.
You can also do this for `j2objc.debug.enabled`.

If you'd rather just disable release builds for a particular run of the command line:

```sh
J2OBJC_RELEASE_ENABLED=false ./gradlew build
```

The `local.properties` value overrides the environment variable, if present.

### J2ObjC Standard Libraries

A number of standard libraries are included with the J2ObjC releases and linked
by default when using the plugin. To add other libraries, see the FAQ
[dependency on a Java project](FAQ.md#how-do-i-setup-a-dependency-on-a-java-project).
The standard libraries are:

    guava
    javax_inject
    jsr305
    junit
    mockito
    protobuf_runtime - TODO: https://github.com/j2objc-contrib/j2objc-gradle/issues/327


### Folder Structure

This is the suggested folder structure. It also shows a number of generated files and
folders that aren't committed to your repository (marked with .gitignore). Files are
shown before folders, so it is not in strict alphabetical order.

    workspace
    ├── .gitignore                     // Add Ignores: local.properties, build/, Pod/
    ├── build.gradle
    ├── local.properties               // sdk.dir=<Android SDK> and j2objc.home=<J2ObjC>, .gitignore
    ├── settings.gradle                // include ':android', ':shared'
    ├── android
    │   ├── build.gradle               // dependencies { compile project(':shared') }
    │   └── src/...                    // src/main/java/... and more, only Android specific code
    ├── ios
    │   ├── IOS-APP.xcworkspace        // Xcode workspace
    │   ├── IOS-APP.xcodeproj          // Xcode project, which is modified by j2objcXcode / CocoaPods
    │   ├── Podfile                    // j2objcXcode modifies this file for use by CocoaPods, committed
    │   ├── IOS-APP/...                // j2objcXcode configures dependency on j2objcOutputs/{libs|src}
    │   ├── IOS-APPTests/...           // j2objcXcode configures as above but with "debug" buildType
    │   └── Pods/...                   // generated by CocoaPods for Xcode, .gitignore
    └── shared
        ├── build.gradle               // apply 'java' then 'j2objc' plugins
        ├── build                      // generated build directory, .gitignore
        │   ├── ...                    // other build output
        │   ├── binaries/...           // Contains test binary: testJ2objcExecutable/debug/testJ2objc
        │   ├── j2objc-shared.podspec  // j2objcXcode generates these settings to modify Xcode
        │   └── j2objcOutputs/...      // j2objcAssemble copies libraries and headers here
        ├── lib                        // library dependencies, must have source code for translation
        │   ├── lib-with-src.jar       // library with source can be translated, see FAQ on how to use
        │   └── libpreBuilt.a          // library prebuilt for ios, see FAQ on how to use
        └── src/...                    // src/main/java/... shared code for translation


### Tasks

These are the main tasks for the plugin:

    j2objcCycleFinder - Find memory cycles that can cause leaks, see FAQ for more details
    j2objcTranslate   - Translates Java source to Objective-C
    j2objcAssemble    - Outputs packed libraries, source & resources to build/j2objcOutputs
    j2objcTest        - Runs all JUnit tests in the translated code
    j2objcBuild       - Runs j2objcAssemble and j2objcTest, doesn't run j2objcXcode
    j2objcXcode       - Xcode updated with libraries, headers & resources (uses CocoaPods)

Running the `build` task from the Gradle Java plugin will automatically run the j2objcBuild command
and all the previous tasks (which it depends on). Only the `j2objcXcode` task needs to be manually
run. Note that you can use the Gradle shorthand of `$ gradlew jA` to do the `j2objcAssemble` task.
The other shorthand expressions are `jCF, jTr, jA, jTe, jB and jX`.


### FAQ

See [FAQ.md](FAQ.md).


### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).


### License

This library is distributed under the Apache 2.0 license found in the [LICENSE](./LICENSE) file.
