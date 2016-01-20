# FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))

This FAQ only illustrates the most common ways to customize the J2ObjC Gradle Plugin.  See
[J2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy)
for comprehensive documentation of all options.

__Table of Contents__
<!--
To update the TOC,
open a debugging console on the FAQ.md page on GitHub
and execute:
  console.log($('article>h3').map(function(i){
    var txt = $(this).text();
    var href= $(this).find('a').attr('href');
    return "- [" + txt + "](" + href + ")";
  }).get().join('\n'));

Paste the results below, replacing existing contents.
-->
- [Start here for debugging (aka it's not working; aka don't panic)](#start-here-for-debugging-aka-its-not-working-aka-dont-panic)
- [How do I develop with Xcode?](#how-do-i-develop-with-xcode)
- [How do I build against a specific version of OS X, iOS or watchOS?](#how-do-i-build-against-a-specific-version-of-os-x-ios-or-watchos)
- [How can I speed up my build?](#how-can-i-speed-up-my-build)
- [What libraries are linked by default?](#what-libraries-are-linked-by-default)
- [How do I setup dependencies with J2ObjC?](#how-do-i-setup-dependencies-with-j2objc)
- [What should I add to .gitignore?](#what-should-i-add-to-gitignore)
- [What is the recommended folder structure for my app?](#what-is-the-recommended-folder-structure-for-my-app)
- [What tasks does the plugin add to my project?](#what-tasks-does-the-plugin-add-to-my-project)
- [How do I customize translation with J2ObjC?](#how-do-i-customize-translation-with-j2objc)
- [How do I customize compilation and linkage?](#how-do-i-customize-compilation-and-linkage)
- [Why is my clean build failing?](#why-is-my-clean-build-failing)
- [How do I include Java files from additional source directories?](#how-do-i-include-java-files-from-additional-source-directories)
- [How do I develop with Swift?](#how-do-i-develop-with-swift)
- [How do I specify additional Xcode build configurations?](#how-do-i-specify-additional-xcode-build-configurations)
- [How do I manually configure the Cocoapods Podfile?](#how-do-i-manually-configure-the-cocoapods-podfile)
- [How do I manually configure my Xcode project to use the translated libraries?](#how-do-i-manually-configure-my-xcode-project-to-use-the-translated-libraries)
- [How do I update my J2ObjC translated code from Xcode?](#how-do-i-update-my-j2objc-translated-code-from-xcode)
- [How do I work with Package Prefixes?](#how-do-i-work-with-package-prefixes)
- [How do I enable ARC for my translated Objective-C classes?](#how-do-i-enable-arc-for-my-translated-objective-c-classes)
- [How do I call finalConfigure()?](#how-do-i-call-finalconfigure)
- [Why is my Android build so much slower after adding J2ObjC?](#why-is-my-android-build-so-much-slower-after-adding-j2objc)
- [Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)](#error-implicit-declaration-of-function-jrerelease-is-invalid-in-c99--werror-wimplicit-function-declaration-jrereleasethis0_)
- [How do I disable a plugin task?](#how-do-i-disable-a-plugin-task)
- [Cycle Finder Basic Setup](#cycle-finder-basic-setup)
- [Cycle Finder Advanced Setup](#cycle-finder-advanced-setup)
- [How do I develop on Windows or Linux?](#how-do-i-develop-on-windows-or-linux)
- [How do I fix missing required architecture linker warning?](#how-do-i-fix-missing-required-architecture-linker-warning)
- [How do I fix Undefined symbols for architecture linker warning?](#how-do-i-fix-undefined-symbols-for-architecture-linker-warning)
- [How do I solve the Eclipse error message Obtaining Gradle model...?](#how-do-i-solve-the-eclipse-error-message-obtaining-gradle-model)


### Start here for debugging (aka it's not working; aka don't panic)

Some common misconfigurations can cause numerous Gradle errors.

1. First make sure you have a supported version of j2objc and Xcode
(see [README.md](README.md) for our current supported versions) installed locally,
and that your J2OBJC_HOME property is set correctly to the distribution directory
of j2objc.  This _is not_ the source repository root for j2objc.  The distribution
directory should have at minimum lib/ and include/ subdirectories.  (If you download
j2objc release .zip files from https://github.com/google/j2objc, you get only the
distribution by default.)

2. Verify you are using the latest released version of the plugin.  Forks of the
plugin exist; if you are looking for help at https://github.com/j2objc-contrib/j2objc-contrib
(the original), please use the latest version distributed from 
https://plugins.gradle.org/plugin/com.github.j2objccontrib.j2objcgradle.

3. If you are still getting errors regarding classes, executables, Jars, or libraries that cannot be found,
and you've verified that your J2OBJC_HOME is set correctly, you may have stale Gradle
caches, which can be cleared as follows.  Note the following steps will cause you to
rebuild everything, so the next build may take a long time.

```shell
# (from your Gradle project's root directory)
# Stops any Gradle daemons if they are running.
./gradlew --stop
# Remove cached Gradle database.
rm -rf .gradle/
# Remove cached Gradle outputs.
rm -rf build/
```

Now try building again.


### How do I develop with Xcode?

The J2ObjC Gradle Plugin can configure your Xcode project with [CocoaPods](https://cocoapods.org/).
To take advantage of this, specify the directory that contains `PROJECT.xcodeproj` as
the `xcodeProjectDir` in your shared `j2objcConfig` per the [Quick Start Guide](README.md#quick-start-guide).
Gradle projects that `shared` depends on must not specify `xcodeProjectDir` so that only one project
controls the Xcode updates.

After running `j2objcXcode`, open the `.xcworkspace` file in Xcode. If the `.xcodeproj` file
is opened in Xcode then CocoaPods will fail. This will appear as an Xcode build time error:

    library not found for -lPods-IOS-APP-j2objc-shared

The above system uses CocoaPods and is the quickest way to build in Xcode with this plugin.
If you wish to avoid using CocoaPods, do not specify the `xcodeProjectDir` option and instead
[manually](#how-do-i-manually-configure-my-xcode-project-to-use-the-translated-libraries)
add the static libraries and translated header directories to your Xcode project.  The
`j2objcXcode` task will not do anything.

Also see the FAQ note on [developing with Swift](#how-do-i-develop-with-swift).


### How do I build against a specific version of OS X, iOS or watchOS?

For example, to use methods only available in iOS 9.2:

```gradle
// File: shared/build.gradle
j2objcConfig {
    minVersionIos '9.2'
    ...
}
```

The settings are `minVersionIos, minVersionOsx & minVersionWatchos`. You can see the
defaults in [J2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy#L636).


### How can I speed up my build?

You can reduce the build time by 50% by skipping the release binaries by adding the
following to your root level `local.properties` file:

```properties
# File: local.properties
j2objc.release.enabled=false
```

This is helpful for a tight modify-compile-test loop using only debug binaries.
You can also do this for `j2objc.debug.enabled`.

If you'd rather just disable release builds for a particular run of the command line:

```sh
J2OBJC_RELEASE_ENABLED=false ./gradlew build
```

The `local.properties` value overrides the environment variable, if present.


### What libraries are linked by default?

A number of standard libraries are included with the J2ObjC releases and linked
by default when using the plugin. To add other libraries, see the FAQs about
[dependencies](#how-do-i-setup-dependencies-with-j2objc).
The standard libraries are:

```
com.google.guava:guava
com.google.j2objc:j2objc-annotations
com.google.protobuf:protobuf-java
junit:junit (test only)
org.mockito:mockito-core (test only)
org.hamcrest:hamcrest-core (test only)
```

Note that this only covers the Objective-C libraries; if you want to use these
libraries in your Java code, you must still include the standard dependency directives like:

```gradle
// File: shared/build.gradle
dependencies {
    compile 'com.google.guava:guava:18.0'
    testCompile 'junit:junit:4.11'
}
```


### How do I setup dependencies with J2ObjC?

The plugin can handle many types of dependencies: multiple Java projects, external libraries and
unit-tests (with source), existing Objective-C code, etc.

See [dependencies.md](dependencies.md).


### What should I add to .gitignore?

The .gitignore file should follow existing conventions for Android Studio and Xcode.
Once that's configured, check that it contains the following:

```properties
# File: .gitignore

# Includes j2objc.home setting (Android Studio should have added this)
local.properties

# CocoaPods temporary files used for Xcode
Pods/
```


### What is the recommended folder structure for my app?

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
    │   ├── IOS-APP.xcodeproj          // Xcode project, which is modified by j2objcXcode / CocoaPods
    │   ├── Podfile                    // j2objcXcode modifies this file for use by CocoaPods, committed
    │   ├── WORKSPACE.xcworkspace      // Xcode workspace
    │   ├── IOS-APP/...                // Xcode workspace
    │   ├── IOS-APPTests/...           // j2objcXcode configures as above but with "debug" buildType
    │   └── Pods/...                   // temp files generated by CocoaPods for Xcode, .gitignore
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


### What tasks does the plugin add to my project?

The main tasks for the plugin are:

    j2objcTranslate   - Translates Java source to Objective-C
    j2objcAssemble    - Outputs built libraries, source & resources to 'build/j2objcOutputs'
    j2objcTest        - Runs all JUnit tests in the translated code
    j2objcXcode       - Updates Xcode project with translated libraries, headers & resources (uses CocoaPods)
    j2objcBuild       - Runs j2objcTranslate, j2objcAssemble, j2objcTest, and j2objcXcode

Running the `build` task from the Gradle Java plugin will automatically run the `j2objcBuild` task
and all the previous tasks (which it depends on).  The `j2objcXcode` task will disable itself
automatically if no `xcodeProjectDir` is specified.

Note that you can use the Gradle shorthand of `$ gradlew jA` to do the `j2objcAssemble` task.


### How do I customize translation with J2ObjC?

Inside your `j2objcConfig` section, you can pass any of the [options J2ObjC takes](http://j2objc.org/docs/j2objc.html)
with `translateArgs`.

Make sure your arguments are separate strings, not a single space-concatenated string.

```gradle
// File: shared/build.gradle
j2objcConfig {
    // CORRECT
    translateArgs '-use-arc'
    translateArgs '-prefixes', 'file.prefixes'

    // CORRECT
    translateArgs '-use-arc', '-prefixes', 'file.prefixes'

    // WRONG
    translateArgs '-use-arc -prefixes file.prefixes'
    ...
}
```


### How do I customize compilation and linkage?

Many well-known compiler or linker args can be customized using an existing
`J2objcConfig` option.  The plugin already sets up a standard build for you,
so only configure these options if the defaults fail to work.

As a last resort, inside your `j2objcConfig` section, you can pass any of the
standard Clang compiler or linker args as `extraObjcCompilerArgs` and `extraLinkerArgs`,
respectively.

Make sure your arguments are separate strings, not a single space-concatenated strings
(see `translateArgs` example above).


### Why is my clean build failing?

This is a [known issue](https://github.com/j2objc-contrib/j2objc-gradle/issues/306)
if you don't have any JUnit tests. If you are doing `./gradlew clean build`, try instead
`./gradlew clean && ./gradlew build`.

When you don't have any test source files, the plugin creates a placeholder to force the
creation of a test binary; this is done during project configuration phase, but `clean` deletes
this file before `build` can use it.


### How do I include Java files from additional source directories?

In order to include source files from sources different than `src/main/java` you have to
[modify the Java plugin's sourceSet(s)](https://docs.gradle.org/current/userguide/java_plugin.html#N11FD1).
For example, if you want to include files from `src-gen/base` both into your JAR and (translated) into
your Objective C libraries, then add to your `shared/build.gradle`:

```gradle
// File: shared/build.gradle
sourceSets {
    main {
        java {
            srcDir 'src-gen/base'
        }
    }
}
```


### How do I develop with Swift?

To work with Swift in Xcode, you need to configure a
[bridging header](https://developer.apple.com/library/ios/documentation/Swift/Conceptual/BuildingCocoaApps/MixandMatch.html#//apple_ref/doc/uid/TP40014216-CH10-XID_81).
Within that bridging header, include the files needed for using the JRE and any classes that
you'd like to access from Swift code.

```objective-c
// File: ios/IOS-APP/IOS-APP-bridging-header.h

// J2ObjC requirement for Java Runtime Environment
// Included from /J2OBJC_HOME/include
#import "JreEmulation.h"

// Swift accessible J2ObjC translated classes
// Included from `shared/build/j2objcOutputs/src/main/objc`
#import "MyClassOne.h"
#import "MyClassTwo.h"
```


### How do I specify additional Xcode build configurations?

Set `xcodeDebugConfigurations` and `xcodeReleaseConfigurations` to specify which build
configurations should link the debug and release builds of the translated libs, respectively.
These default to `['Debug']` and `['Release']` which correspond to Xcode's default build
configuration names. Setting either of these to an empty array will omit the respective pod
line from the "pod method".

For example, if you have a build configuration called "Beta" for sending to internal testers
and one called "Preview" for doing ad-hoc distribution:

```gradle
// File: shared/build.gradle
j2objcConfig {
    xcodeDebugConfigurations += ['Beta']
    xcodeReleaseConfigurations += ['Preview']
    ...
}
```


### How do I manually configure the Cocoapods Podfile?

The plugin will try to automatically update the Cocoapods Podfile but that may fail if
the Podfile is too complex. In that situation, you can manually configure the pod method.

```gradle
// File: shared/build.gradle
j2objcConfig {
    xcodeTargetsManualConfig true
    ...
}
```

The "pod method" definition will still be added automatically (e.g.
`def j2objc_shared...`). However, the "pod method" will not be added to any
targets, so that needs to be done manually. See example of the Podfile below:

```
// File: ios/Podfile
...

# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY START - can be moved as a block
def j2objc_shared
    pod 'j2objc-shared-debug', :configuration => ['Debug'], :path => '../shared/build/j2objcOutputs'
    pod 'j2objc-shared-release', :configuration => ['Release'], :path => '../shared/build/j2objcOutputs'
end
# J2ObjC Gradle Plugin - PodMethods - DO NOT MODIFY END

<SOME COMPLEX RUBY>
    ...
    # NOTE: this line must be added manually for the relevant targets:
    j2objc_shared
    ...
end
```

To disable all modifications of the Podfile, disable the `j2objcXcode` task.
This will also skip the `pod install` step. The podspec files will still
be written (done by the `j2objcPodspec` task).

```gradle
// File: shared/build.gradle
j2objcConfig {
    ...
}
j2objcXcode {
    enabled = false
}
```


### How do I manually configure my Xcode project to use the translated libraries?

Using CocoaPods is the quickest way to use the plugin. To configure Xcode manually,
you will need to modify your Xcode project's build settings.

In each case, you need to make sure the specification of the path is relative to the location
of the Xcode project.  We'll assume your J2ObjC distribution is at `/J2OBJC_HOME`.

1.  Add `/J2OBJC_HOME/include` and `shared/build/j2objcOutputs/src/main/objc` to
[`USER_HEADER_SEARCH_PATHS`](https://developer.apple.com/library/mac/documentation/DeveloperTools/Reference/XcodeBuildSettingRef/1-Build_Setting_Reference/build_setting_ref.html#//apple_ref/doc/uid/TP40003931-CH3-SW21).

2.  Add `-lshared-j2objc` and `-ljre_emul` to `OTHER_LD_FLAGS`.  If you also use Guava, JSR 305,
etc., you will need to add appropriate OTHER_LD_FLAGS for them as well.

2.  For each build configuration and platform applicable to your project, add the following `LIBRARY_SEARCH_PATHS`:
  * iOS Debug: /J2OBJC_HOME/lib, shared/build/j2objcOutputs/lib/iosDebug
  * iOS Release: /J2OBJC_HOME/lib, shared/build/j2objcOutputs/lib/iosRelease
  * OS X Debug: /J2OBJC_HOME/lib/macosx, shared/build/j2objcOutputs/lib/x86_64Debug
  * OS X Release: /J2OBJC_HOME/lib/macosx, shared/build/j2objcOutputs/lib/x86_64Release

3.  Follow the instructions on "Build Phases" (only) [here](http://j2objc.org/docs/Xcode-Build-Rules.html#update-the-build-settings).

In each case, if the setting has existing values, append the ones above.
Wherever `shared` appears above, duplicate that value for every J2ObjC project used,
including all transitive dependencies.


### How do I update my J2ObjC translated code from Xcode?

It is suggested to first develop and debug in Android, then after that is working to
build in iOS. If you want to update your generated Objective-C code while working in
Xcode, then you can add the J2ObjC build as an External Build System target:

1.  Select the main iOS project.  File -> New Target -> Other -> External Build System.
    Set the build tool as `/bin/sh`, and name the target j2objcGradle.
2.  In the 'External Build Tool Configuration' screen that appears, set the following values:
  * Build Tool: `/bin/sh`
  * Arguments: `./gradleJ2objcBuild.sh`
  * Directory: (the root Gradle project directory, where your settings.gradle file lives)
  * __Uncheck__ 'Pass build settings in environment'
3.  Add the following script named `gradleJ2objcBuild.sh` in your root Gradle project directory.

  ```sh
  #!/bin/sh
  # Don't contaminate the build with Xcode env vars.
  unset DEVELOPER_DIR
  # CUSTOMIZE: Add any needed paths here.
  export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
  if [ "$ACTION" != "clean" ]; then
      # CUSTOMIZE: List the j2objcBuild task for each J2ObjC project.
      ./gradlew shared:j2objcBuild -q
  else
      echo "Skipping Gradle Build for Clean"
  fi
  ```
4.  Click on your your project in the left pane, select your main app target,
    and in 'Build Phases' select 'Target Dependencies'.  Click the plus sign and find the j2objcGradle
    target added earlier.

From now on, building your iOS project will first update any J2ObjC code (if needed).


### How do I work with Package Prefixes?

For the class `com.example.dir.MyClass`, J2ObjC will by default translate the name to
`ComExampleDirMyClass`, which is a long name to use. With package prefixes, you can shorten it
to something much more manageable, like `CedMyClass`. See the example below on how to do this.
Also see the reference docs on [package name prefixes](http://j2objc.org/docs/Package-Prefixes.html).

```gradle
// File: shared/build.gradle
j2objcConfig {
    translateArgs '--prefixes', 'src/main/resources/prefixes.properties'
    ...
}
```

Storing `prefixes.properties` in the resource folder will mean that it's copied in to the
Xcode build. Without having this file in the Xcode, calls to `Class.forName("MyClass")`
will fail for the mapped classes.

```properties
# File: shared/src/main/resources/prefixes.properties
com.example.dir: Ced
com.example.dir.subdir: Ced
```

You can also use wildcards in your prefixes file. 
This will map packages such as com.example.dir and com.example.dir.subdir both to Ced.

```
com.example.dir.*: Ced
```


### How do I enable ARC for my translated Objective-C classes?

__Note__: The use of ARC for the translated code is not recommended and is not required
in order to use ARC for the hand-written Objective-C iOS app that links to the translated code.

Add the following to your configuration block. See 
[here](https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15).

```gradle
// File: shared/build.gradle
j2objcConfig {
   translateArgs '-use-arc'
   extraObjcCompilerArgs '-fobjc-arc'
   ...
}
```


### How do I call finalConfigure()?

You must always call `finalConfigure()` at the end of `j2objcConfig {...}` within your project's
`build.gradle` file. You need to include an otherwise empty `j2objcConfig {...}` block with this
call even if you do not need to customize any other `j2objConfig` option.

```gradle
// File: shared/build.gradle
j2objcConfig {
    ...
    finalConfigure()
}
```

In addition, put your `j2objcConfig` at the very end of your build.gradle.  This allows
`finalConfigure()` to correctly access other project settings, like dependencies.


### Why is my Android build so much slower after adding J2ObjC?

Depending on your Android Studio 'run configuration', you may be building more of your Gradle
project than is neccessary.  For example if your run configuration includes a general 'Make'
task before running, it will build all your gradle projects, including the j2objc parts.
Instead make sure your run configuration only performs the `:android:assembleDebug` task:

<img width="664" alt="Run Configuration" src="https://cloud.githubusercontent.com/assets/11729521/9024382/95a511a4-3881-11e5-8525-9e4256990614.png">

At the command line, if you want to use the debug version of your Android app,
make sure you are running `./gradlew :android:assembleDebug` and not for example
`./gradlew build`.

### Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)

See: [How do I enable ARC for my translated Objective-C classes?](#how-do-i-enable-arc-for-my-translated-objective-c-classes?)


### How do I disable a plugin task?

You can disable tasks performed by the plugin using the following configuration block in your
`build.gradle`. This is separate and alongside the `j2objcConfig` settings. For example, to
disable the `j2objcTest` task, do the following:

```gradle
// File: shared/build.gradle
j2objcConfig {
    ...
}

j2objcTest {
    enabled = false
}
```


### Cycle Finder Basic Setup

This task is disabled by default as it requires greater sophistication to use. It runs the
`cycle_finder` tool in the J2ObjC release to detect memory cycles in your application.
Objects that are part of a memory cycle on iOS won't be deleted as it doesn't support
garbage collection.

The basic setup will implicitly check for 40 memory cycles - this is the expected number
of erroneous matches with `jre_emul` library for J2ObjC version 0.9.6.1. This may cause
issues if this number changes with future versions of J2ObjC libraries.

```gradle
// File: shared/build.gradle
j2objcCycleFinder {
    enabled = true
}
```

Also see FAQ's [Advanced Cycle Finder Setup](FAQ.md#Cycle-Finder-Advanced-Setup).


### Cycle Finder Advanced Setup

This uses a specially annotated version of the `jre_emul` source that marks all the
erroneously matched cycles such that they can be ignored. It requires downloading
and building the J2ObjC source:

1. Download the J2ObjC source to a directory (hereafter J2OJBC_REPO):

    https://github.com/google/j2objc

2. Within the J2OJBC_REPO, run:<br>

    `(cd jre_emul && make java_sources_manifest)`

3. Configure j2objcConfig in `shared/build.gradle` so CycleFinder uses the annotated J2ObjC
source and whitelist. Note how this specifies an expected cycle count of zero, as the JRE cycles
are already accounted for in the generated whitelist.  If however, you have additional cycles
you expect, you should use that number instead of zero.

```gradle
// File: shared/build.gradle
j2objcConfig {
    cycleFinderArgs '--whitelist', 'J2OBJC_REPO/jre_emul/cycle_whitelist.txt'
    cycleFinderArgs '--sourcefilelist', 'J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
    cycleFinderExpectedCycles 0
    ...
}
```

For more details:
- http://j2objc.org/docs/Cycle-Finder-Tool.html
- https://groups.google.com/forum/#!msg/j2objc-discuss/2fozbf6-liM/R83v7ffX5NMJ


### How do I develop on Windows or Linux?

Windows and Linux support is limited to the `j2objcCycleFinder` and `j2objcTranslate` tasks.
Mac OS X is required for the j2objcAssemble, j2objcTest and j2objcXcode tasks
(see [task descriptions](README.md#tasks)) This matches the
[J2ObjC Requirements](http://j2objc.org/#requirements).

It is recommended that Windows and Linux users use `translateOnlyMode` to reduce the chances
of breaking the iOS / OS X build. This can be done by those developers configuring their
local.properties (the Mac OS X developers should not use this):

```properties
# File: local.properties
j2objc.translateOnlyMode=true
```


### How do I fix `missing required architecture` linker warning?
If you see a message similar to:
```
ld: warning: ignoring file /PATH/j2objcOutputs/lib/iosDebug/libPROJECT-j2objc.a,
missing required architecture i386 in file /PATH/j2objcOutputs/lib/iosDebug/libPROJECT-j2objc.a
(3 slices)


Undefined symbols for architecture i386:
  "_OBJC_CLASS_$_ComExampleShared", referenced from:
      type metadata accessor for ObjectiveC.ComExampleShared in ViewController.o
ld: symbol(s) not found for architecture i386
```
You are not building all the necessary architectures.

By default (for performance), we build only modern iOS device and simulator
architectures. If you need i386 for older simulators (iPhone 5, 5c and earlier
devices), add the following to your build.gradle file:

```gradle
// File: shared/build.gradle
j2objcConfig {
    supportedArchs += ['ios_i386']
    ...
}
```

### How do I fix `Undefined symbols for architecture` linker warning?

This usually occurs with `architecture x86_64` when using the simulator and
`architecture arm64` when running on a device. The error should look
similar to this:

```
Undefined symbols for architecture x86_64:
  "_OBJC_METACLASS_$_MyClassMethodName", referenced from:
     _OBJC_METACLASS_$_MethodName in MyClass.o
```

It can usually be fixed by manually running the Cocoapods install command
(adjust based on your Xcode directory):

```shell
(cd Xcode && pod install)
```

Note that re-running the build from Gradle as it will skip re-running the
`j2objcXcode` task unless one of the dependencies has changed.

The problem occurs due to flakiness in Cocoapods. You can see this in the
linker output, it fails to include the `-lshared-j2objc` library in the linker
flags. When working correctly, the linker flags should include the following:

```
-ObjC -lObjC -lshared-j2objc -lguava -licucore -ljavax_inject -ljre_emul -ljsr305 -lz
```

If you still have further issues, use the following commands to investigate
the built libraries:

```shell
# list the architectures in the library. This should include "x86_64":
lipo -info shared/build/j2objcOutputs/lib/iosDebug/libshared-j2objc.a

# list out the methods in the file and look for the missing methods:
otool -SV base/build/j2objcOutputs/lib/iosDebug/libbase-j2objc.a
```


### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to first create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.
