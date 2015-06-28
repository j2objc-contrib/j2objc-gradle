# j2objc-gradle
Gradle Plugin for [J2ObjC](https://github.com/google/j2objc), which is an open-source tool
from Google that translates Java source code to Objective-C for the iOS (iPhone/iPad) platform.
The plugin is not affiliated with Google but was developed by former Google Engineers and others.
J2ObjC enables Java source to be part of an iOS application's build, as no editing of the
generated files is necessary. The goal is to write an app's non-UI code (such as application
logic and data models) in Java, which is then shared by web apps (using GWT), Android apps,
and iOS apps.


### Usage
At HEAD, this plugin is in a state of significant flux as we refactor it into a first-class
Gradle plugin for our beta. You may wish to wait for the beta release as we may make backwards
incompatible changes before that point.

You should start with a clean java only project without any android dependencies. It suggested that
this project is named `'shared'`. It must be buildable using Gradle's standard `'java'` plugin.
It may start as an empty project and allows you to gradually shift over code from an existing
Android application. See the section below on [Folder Structure](#folder-structure).

**Note: the `plugins { id 'com.github.j2objccontrib.j2objcgradle' }` syntax does
not yet work for the j2objc plugin. You must use the old buildscript style shown below.
Tracked issue: https://github.com/j2objc-contrib/j2objc-gradle/issues/130**

    // File: shared/build.gradle
    buildscript {
        repositories {
            maven {
                url "https://plugins.gradle.org/m2/"
            }
        }
        dependencies {
            // Current Version: https://plugins.gradle.org/plugin/com.github.j2objccontrib.j2objcgradle
            classpath "gradle.plugin.com.github.j2objccontrib.j2objcgradle:j2objc-gradle:X.Y.Z-alpha"
        }
    }

    // The 'java' plugin must be applied before the 'j2objc' plugin
    apply plugin: 'java'
    apply plugin: 'com.github.j2objccontrib.j2objcgradle'

    // Plugin settings:
    j2objcConfig {
        xcodeProjectDir "${projectDir}/../ios"   // Xcode workspace directory
        xcodeTarget "IosApp"                     // Xcode application target name
    
        // Help information on other settings:
        // https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy#L25
        
        // You must include this call (at the end of j2objcConfig) regardless of settings
        finalConfigure()
    }

Within the Android application's project `build.gradle`, make it dependent on the `shared` project:

    // File: android/build.gradle
    dependencies {
        compile project(':shared')
    }


### Folder Structure

This is the suggested folder structure. It also shows a number of generated files and
folders that aren't committed to your repository. Files are shown before folder, so it
is not in strict alphabetical order.

    workspace
    ├── .gitignore                     // Should exclude: local.properties, settings.gradle, build/, ...
    ├── build.gradle
    ├── local.properties               // sdk.dir=<Android SDK> and j2objc.home=<J2ObjC>, .gitignore exclude
    ├── settings.gradle                // include ':android', ':shared'
    ├── android
    │   ├── build.gradle               // dependencies { compile project(':shared') }
    │   └── src/...                    // src/main/java/... and more, only Android specific code
    ├── ios
    │   ├── iosApp.xcworkspace         // Xcode workspace
    │   ├── iosApp.xcodeproj           // Xcode project, which is modified by j2objcXcode / CocoaPods
    │   ├── Podfile                    // j2objcXcode modifies this file for use by CocoaPods, committed
    │   ├── iosApp/...                 // j2objcXcode configures dependency on j2objcOutputs/{libs|src}
    │   ├── iosAppTests/...            // j2objcXcode configures as above but with "debug" buildType
    │   └── Pods/...                   // generated by CocoaPods for Xcode, .gitignore exclude
    └── shared
        ├── build.gradle               // apply 'java' then 'j2objc' plugins
        ├── build                      // generated build directory, .gitignore exclude
        │   ├── ...                    // other build output
        │   ├── binaries/...           // Contains test binary: testJ2objcExecutable/debug/testJ2objc
        │   ├── j2objc-shared.podspec  // j2objcXcode generates these settings to modify Xcode
        │   └── j2objcOutputs/...      // j2objcAssemble copies libraries and headers here
        ├── lib                        // library dependencies, must have source code for translation
        │   └── lib-with-src.jar       // library with source can be translated
        └── src/...                    // src/main/java/... shared code for translation


### Tasks

These are the main tasks for the plugin:

    j2objcCycleFinder       - Find cycles that can cause memory leaks, see notes below
    j2objcTranslate         - Translates to Objective-C
    j2objcAssemble          - Builds debug/release libraries, packs targets in to fat libraries
    j2objcTest              - Runs all JUnit tests, as translated into Objective-C
    j2objcXcode             - Configure Xcode to link to static library and header files

Note that you can use the Gradle shorthand of "$ gradlew jA" to do the j2objcAssemble task.
The other shorthand expressions are `jCF`, `jTr`, `jA`, `jTe` and `jX`.


#### Task Enable and Disable

You can disable tasks performed by the plugin using the following configuration block in your
`build.gradle`. This is separate and alongside the j2objcConfig settings. For example, to
disable the `j2objcTest` task, do the following:

    j2objcTest {
        enabled = false
    }

    j2objcConfig {
        ...
    }


#### j2objcCycleFinder

This task is disabled by default as it requires greater sophistication to use. It runs the
`cycle_finder` tool in the J2ObjC release to detect memory cycles in your application.
Objects that are part of a memory cycle on iOS won't be deleted as it doesn't support
garbage collection.

The basic setup will implicitly check for 40 memory cycles - this is the expected number
of erroneous matches with `jre_emul` library for j2objc version 0.9.6.1. This may cause
issues if this number changes with future versions of j2objc libraries.

    j2objcCycleFinder {
        enabled = true
    }

Also see FAQ's [Advanced Cycle Finder Setup][FAQ.md#Advanced-Cycle-Finder-Setup].


### Plugin Development

For plugin contributors, you should build the plugin from this repository's root:

    ./gradlew build

This will create a .jar containing the plugin at projectDir/build/libs/j2objc-gradle-X.Y.Z-alpha.jar

In order to test your modification to the plugin using your own project, use the following build script in your
java project's build.gradle:

    buildscript {
        dependencies {
            // Update using "version = X.Y.Z-alpha" defined in build.gradle
            classpath files('/PATH_TO_J2OBJC_PLUGIN/j2objc-gradle/build/libs/j2objc-gradle-X.Y.Z-alpha.jar')
        }
    }

    apply plugin: 'java'
    apply plugin: 'com.github.j2objccontrib.j2objcgradle'

    // j2objcConfig...

Note that when rapidly developing and testing changes to the plugin by building your own project,
avoid using the Gradle daemon as issues sometimes arise with the daemon using an old version
of the plugin jar.  You can stop an existing daemon with `./gradlew --stop` and avoid the daemon
on a particular build with the `--no-daemon` argument to gradlew.


### Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).


### FAQ

See [FAQ.md](FAQ.md).


### License

This library is distributed under the Apache 2.0 license found in the
[LICENSE](./LICENSE) file.
