# J2ObjC Gradle Plugin

The __J2ObjC Gradle plugin__ enables Java source to be part of an iOS application's build
so you can write an app's non-UI code (such as application logic and data models) in Java,
which is then shared by Android apps (natively Java) and iOS apps (using J2ObjC).

The plugin:
* Translates your Java source code to Objective-C for iOS (iPhone/iPad) using [__J2ObjC__](https://github.com/google/j2objc), an open-source tool from Google
* Builds Objective-C static libraries and headers ready-to-use in Xcode
* Runs translated versions of your JUnit tests to ensure your code works in Objective-C form
* Handles multiple Java projects, external Java libraries \[1\], and existing Objective-C code you'd like to link in
* Configures Xcode projects to use your translated libraries, using CocoaPods (optionally)
 
The plugin is not affiliated with Google but was developed by former Google Engineers and others.
Note that the plugin is currently in alpha; we may need to make breaking API changes
before the 1.0 release.

[![License](https://img.shields.io/badge/license-Apache%202.0%20License-blue.svg)](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/LICENSE)
[![OSX and Linux Build Status](https://img.shields.io/travis/j2objc-contrib/j2objc-gradle/master.svg?label=mac and linux build)](https://travis-ci.org/j2objc-contrib/j2objc-gradle)
[![Windows Build Status](https://img.shields.io/appveyor/ci/madvayApiAccess/j2objc-gradle/master.svg?label=windows build)](https://ci.appveyor.com/project/madvayApiAccess/j2objc-gradle/branch/master)

Home Page: https://github.com/j2objc-contrib/j2objc-gradle

### Quick Start Guide

You should start with a clean Java only project without any Android dependencies.
It is suggested that the project is named `shared`. It must be buildable using the standard
[Gradle Java plugin](https://docs.gradle.org/current/userguide/java_plugin.html).
Starting with an empty project allows you to gradually shift over code from an existing
Android application. This is beneficial for separation between the application model
and user interface. It also allows the shared project to be easily used server-side as well.

The Android app, shared Java project and Xcode project should be sibling directories, i.e children
of the same root level folder. Suggested folder names are `'android', 'shared' and 'ios'`
respectively. See the FAQ section on [recommended folder structure](FAQ.md#what-is-the-recommended-folder-structure-for-my-app).

Configure `shared/build.gradle` for your Java-only project:

```gradle
// File: shared/build.gradle
plugins {
    id 'java'
    id 'com.github.j2objccontrib.j2objcgradle' version '0.5.0-alpha'
}

dependencies {
    // Any libraries you depend on, like Guava or JUnit
    compile 'com.google.guava:guava:18.0'
    testCompile 'junit:junit:4.11'
}

// Plugin settings; put these at the bottom of the file.
j2objcConfig {
    // Sets up libraries you depend on
    autoConfigureDeps true
    
    // Omit these two lines if you don't configure your Xcode project with CocoaPods
    xcodeProjectDir '../ios'  //  suggested directory name
    xcodeTargetsIos 'IOS-APP', 'IOS-APPTests'  // replace with your iOS targets

    finalConfigure()          // Must be last call to configuration
}
```

Finally, make the Android application's `android/build.gradle` depend on the `shared` project:

```gradle
// File: android/build.gradle
dependencies {
    compile project(':shared')
}
```

For more complex situations like:
* building for OS X and ([soon](https://github.com/j2objc-contrib/j2objc-gradle/issues/525)) watchOS
* [multiple Java projects and third-party libraries](FAQ.md#how-do-i-setup-dependencies-with-j2objc)
* customizing the translation and compilation steps
* mixing Objective-C and Java implementations

, check the [FAQ table of contents](FAQ.md) or see all of the `j2objcConfig` settings in
[J2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy#L30).


### Minimum Requirements

The plugin requries modern versions of Gradle and J2ObjC, and assumes the
[J2ObjC Requirements](http://j2objc.org/#requirements):

* Gradle 2.4
* J2ObjC 1.0.2 or higher
* JDK 1.7 or higher
* Mac workstation or laptop
* Mac OS X 10.9 or higher
* Xcode 7 or higher

Applications built with the plugin may target

* iOS 6.0 or higher
* OS X 10.6 or higher


### J2ObjC Installation

If J2ObjC is not detected when the plugin is first run, on-screen instructons will guide
you through installation from your Terminal.

Alternatively:
Download the latest version from the [J2ObjC Releases](https://github.com/google/j2objc/releases).
Find (or add) the local.properties in your root folder and add the path to the unzipped folder:

```properties
# File: local.properties
j2objc.home=/J2OBJC_HOME
```


### Build Commands

The plugin will output the generated source and libaries to the `build/j2objcOutputs`
directory, run all unit tests, and configure your Xcode project (if you specified one).

It is integrated with Gradle's Java build plugin and may
be run as follows:

    ./gradlew shared:build


### Problems

Having issues with the plugin?
Please first check the [Frequently Asked Questions](FAQ.md).

Next, search the [Issues](https://github.com/j2objc-contrib/j2objc-gradle/issues) for a similar
problem.  If your issue is not addressed, please file a new Issue, including the following
details:
- build.gradle file(s)
- contents of Gradle build errors if any
- version of J2ObjC you have installed

If you are not comfortable sharing these, the community may not be able to help as much.
Please note your bug reports will be treated as "Contribution(s)" per the [LICENSE](LICENSE).

Mozilla's [Bug writing guidelines](https://developer.mozilla.org/en-US/docs/Mozilla/QA/Bug_writing_guidelines)
may be helpful. Having public, focused, and actionable Issues
helps the maximum number of users and also lets the maximum number of people help you.
Please do not email the authors directly.


### FAQ

Please see [FAQ.md](FAQ.md).


### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).


### License

This library is distributed under the Apache 2.0 license found in the [LICENSE](./LICENSE) file.
J2ObjC and libraries distributed with J2ObjC are under their own licenses.


### Footnotes

[1]  <a href='#footnote1'>where source is appropriately licensed and available</a>
