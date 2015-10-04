# J2ObjC Gradle Plugin

Gradle Plugin for [J2ObjC](https://github.com/google/j2objc),
which is an open-source tool from Google that translates
Java source code to Objective-C for the iOS (iPhone/iPad) platform. The plugin is
not affiliated with Google but was developed by former Google Engineers and others.
J2ObjC enables Java source to be part of an iOS application's build, no editing
of the generated files is necessary. The goal is to write an app's non-UI code
(such as application logic and data models) in Java, which is then shared by
Android apps (natively Java), web apps (using GWT), and iOS apps (using J2ObjC).

__Note:__ Work is in progress for compatibility with Xcode 7 and j2objc 0.9.8.2.1.
We currently support Xcode 6 and j2objc 0.9.8.1.

[![License](https://img.shields.io/badge/license-Apache%202.0%20License-blue.svg)](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/LICENSE)
[![OSX and Linux Build Status](https://img.shields.io/travis/j2objc-contrib/j2objc-gradle/master.svg?label=mac and linux build)](https://travis-ci.org/j2objc-contrib/j2objc-gradle)
[![Windows Build Status](https://img.shields.io/appveyor/ci/madvayApiAccess/j2objc-gradle/master.svg?label=windows build)](https://ci.appveyor.com/project/madvayApiAccess/j2objc-gradle/branch/master)

Home Page: https://github.com/j2objc-contrib/j2objc-gradle

### Usage

You should start with a clean java only project without any Android dependencies.
It is suggested that the project is named `shared`. It must be buildable using the standard
[Gradle Java plugin](https://docs.gradle.org/current/userguide/java_plugin.html).
Starting as an empty project allows you to gradually shift over code from an existing
Android application. This is beneficial for separation between the application model
and user interface. It also allows the shared project to be easily used server side as well.

The Android app, shared Java project and Xcode should be sibling directories, i.e children
of the same root level folder. Suggested folder names are `'android', 'shared' and 'ios'`
respectively. See the FAQ section on [recommended folder structure](FAQ.md#what-is-the-recommended-folder-structure-for-my-app).

Configure `shared/build.gradle` in your Java only project:

```gradle
// File: shared/build.gradle
plugins {
    id 'java'
    id 'com.github.j2objccontrib.j2objcgradle' version '0.4.3-alpha'
}

// Plugin settings:
j2objcConfig {
    xcodeProjectDir '../ios'  // Xcode workspace directory (suggested directory name)
    xcodeTarget 'IOS-APP'     // iOS app target name (replace with existing app name)

    finalConfigure()          // Must be last call to configuration
}
```

Info on additional `j2objcConfig` settings are in
[J2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy#L30).
If your `shared` project depends on any other projects or third-party libraries, you may
need to [add them manually](FAQ.md#how-do-i-setup-dependencies-with-j2objc) if they aren't
[linked by default](FAQ.md#what-libraries-are-linked-by-default).

Within the Android application's `android/build.gradle`, make it dependent on the `shared` project:

```gradle
// File: android/build.gradle
dependencies {
    compile project(':shared')
}
```

Note that the plugin is currently in alpha; we may need to make breaking API changes
before the 1.0 release.

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

### Problems

Having issues with the plugin?
Please first check the [Frequently Asked Questions](FAQ.md).
Next, search the [Issues](https://github.com/j2objc-contrib/j2objc-gradle/issues) for a similar
problem.  If your issue is not addressed, please file a new Issue, including the following
details (if you are comfortable sharing publicly as "Contribution(s)" per
the [LICENSE](LICENSE)):
- build.gradle file(s)
- contents of Gradle build errors if any
- version of J2ObjC you have installed

If you are not comfortable sharing these, the community may not be able to help as much.

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
