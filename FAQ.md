# FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))

__Table of Contents__
<!--
To update the TOC,
open a debugging console on the FAQ.md page on GitHub
and execute:
  $('article>h3').map(function(i){
    var txt = $(this).text();
    var href= $(this).find('a').attr('href');
    return "- [" + txt + "](" + href + ")";
  }).get().join('\n');

Paste the results below, replacing existing contents.
-->
- [How do I develop with Xcode?](#how-do-i-develop-with-xcode)
- [How can I speed up my build?](#how-can-i-speed-up-my-build)
- [What libraries are linked by default?](#what-libraries-are-linked-by-default)
- [What is the recommended folder structure for my app?](#what-is-the-recommended-folder-structure-for-my-app)
- [What tasks does the plugin add to my project?](#what-tasks-does-the-plugin-add-to-my-project)
- [What version of Gradle do I need?](#what-version-of-gradle-do-i-need)
- [How do I solve the Eclipse error message Obtaining Gradle model...?](#how-do-i-solve-the-eclipse-error-message-obtaining-gradle-model)
- [How do I properly pass multiple arguments to J2ObjC?](#how-do-i-properly-pass-multiple-arguments-to-j2objc)
- [Why is my clean build failing?](#why-is-my-clean-build-failing)
- [How do I include Java files from additional source directories?](#how-do-i-include-java-files-from-additional-source-directories)
- [How do I develop with Swift?](#how-do-i-develop-with-swift)
- [How do I solve 'File not found' import error in Xcode?](#how-do-i-solve-file-not-found-import-error-in-xcode)
- [How do I work with Package Prefixes?](#how-do-i-work-with-package-prefixes)
- [How do I enable ARC for my Objective-C classes?](#how-do-i-enable-arc-for-my-objective-c-classes)
- [How do I call finalConfigure()?](#how-do-i-call-finalconfigure)
- [Why is my Android build so much slower after adding j2objc?](#why-is-my-android-build-so-much-slower-after-adding-j2objc)
- [Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)](#error-implicit-declaration-of-function-jrerelease-is-invalid-in-c99--werror-wimplicit-function-declaration-jrereleasethis0_)
- [How do I disable a plugin task?](#how-do-i-disable-a-plugin-task)
- [How do I setup dependencies with J2ObjC?](#how-do-i-setup-dependencies-with-j2objc)
- [How do I setup a dependency to a third-party Java library?](#how-do-i-setup-a-dependency-to-a-third-party-java-library)
- [How do I setup a dependency on a Java project?](#how-do-i-setup-a-dependency-on-a-java-project)
- [How do I setup a dependency on a prebuilt native library?](#how-do-i-setup-a-dependency-on-a-prebuilt-native-library)
- [How do I setup a dependency on a native library project?](#how-do-i-setup-a-dependency-on-a-native-library-project)
- [Cycle Finder Basic Setup](#cycle-finder-basic-setup)
- [Cycle Finder Advanced Setup](#cycle-finder-advanced-setup)
- [How do I develop on Windows or Linux?](#how-do-i-develop-on-windows-or-linux)


### How do I develop with Xcode?

When using the `j2objcTask`, open the `.xcworkspace` file in Xcode. If the `.xcodeproj` file
is opened in Xcode then CocoaPods will fail. This will appear as an Xcode build time error:

    library not found for -lPods-IOS-APP-j2objc-shared

Also see the FAQ note on [developing with Swift](#how-do-i-develop-with-swift).


### How can I speed up my build?

You can reduce the build time by 75% by skipping the release binaries and only building
for a subset of the architectures. The following `local.properties` will skip the release
build and only target the modern architectures. This works for a typical developer using
a 64-bit iPhone (5S or later) and running the iOS simulator on a 64-bit Mac (most Intel
Macs from 2010 onwards). Developing on an iPhone 5 or earlier will require the `ios_armv7`
architecture.

```properties
# File: local.properties
j2objc.release.enabled=false

# Builds only for 64-bit iPhone and 64-bit Mac iOS Simulator
j2objc.enabledArchs=ios_arm64,ios_x86_64
```

This is helpful for a tight modify-compile-test loop using only debug binaries.
The final build (of your continuous build) should build both the debug and release
builds. You can also do this for `j2objc.debug.enabled`.

If you'd rather just disable release builds for a particular run of the command line
(the `local.properties` value overrides the environment variable, if present).

```sh
J2OBJC_RELEASE_ENABLED=false ./gradlew build
```

The `enabledArchs` options are as follows:

* 'ios_arm64' => iPhone 5S, 6, 6 Plus
* 'ios_armv7' => iPhone 4, 4S, 5
* 'ios_i386' => iOS Simulator on 32-bit OS X
* 'ios_x86_64' => iOS Simulator on 64-bit OS X



### What libraries are linked by default?

A number of standard libraries are included with the J2ObjC releases and linked
by default when using the plugin. To add other libraries, see the FAQs about
[dependencies](#how-do-i-setup-dependencies-with-j2objc).
The standard libraries are:

    guava
    javax_inject
    jsr305
    junit
    mockito
    protobuf_runtime - TODO: https://github.com/j2objc-contrib/j2objc-gradle/issues/327


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


### What tasks does the plugin add to my project?

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


### What version of Gradle do I need?

You need at least [Gradle version 2.4](https://discuss.gradle.org/t/gradle-2-4-released/9471),
due to support for native compilation features.


### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to first create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.


### How do I properly pass multiple arguments to J2ObjC?

Make sure your arguments are separate strings, not a single space-concatenated string.

```gradle
j2objcConfig {
    // CORRECT
    translateArgs '-use-arc'
    translateArgs '-prefixes', 'file.prefixes'

    // CORRECT
    translateArgs '-use-arc', '-prefixes', 'file.prefixes'

    // WRONG
    translateArgs '-use-arc -prefixes file.prefixes'
}
```


### Why is my clean build failing?

This is a [known issue](https://github.com/j2objc-contrib/j2objc-gradle/issues/306)
if you don't have any tests. If you are doing `./gradlew clean build`, try instead
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
you'd like to access from Swift code. Also see the FAQ item on [file not found](#how-do-i-solve-file-not-found-import-error-in-xcode) errors.

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

### How do I solve 'File not found' import error in Xcode?

This is typically caused by a limitation of Xcode that expects all the source to be in a
top-level directory. You need to use the `--no-package-directories` argument to flatten
the hierarchy. The plugin will warn if two files are mapped to a conflicting name.

```groovy
j2objcConfig {
    translateArgs '--no-package-directories'
    ...
}
```

Example error:

```
Bridging-Header.h:6:9: note: in file included from Bridging-Header.h:6:
#import "MyClass.h"
        ^
Template.h:10:10: error: 'com/test/AnotherClass.h' file not found
#include "com/test/AnotherClass.h"
```

### How do I work with Package Prefixes?

For the class `com.example.dir.MyClass`, J2ObjC will by default translate the name to
`ComExampleDirMyClass`, which is a long name to use. With package prefixes, you can shorten it
to something much more manageable, like `CedMyClass`. See the example below on how to do this.
Also see the reference docs on [package name prefixes](http://j2objc.org/docs/Package-Prefixes.html).

```groovy
// File: build.gradle
j2objcConfig {
    translateArgs '--prefixes', 'resources/prefixes.properties'
    ...
}
```

```
// File: resources/prefixes.properties
// Storing at this location allows Class.forName(javaName) to work for mapped class.
com.example.dir: Ced
com.example.dir.subdir: Ceds
```


### How do I enable ARC for my Objective-C classes?

Add the following to your configuration block. [See](https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15).

```gradle
j2objcConfig {
   translateArgs '-use-arc'
   extraObjcCompilerArgs '-fobjc-arc'
}
```


### How do I call finalConfigure()?

You must always call `finalConfigure()` at the end of `j2objcConfig {...}` within your project's
`build.gradle` file. You need to include an otherwise empty `j2objcConfig {...}` block with this
call even if you do not need to customize any other `j2objConfig` option.

```gradle
j2objcConfig {
    ...
    finalConfigure()
}
```


### Why is my Android build so much slower after adding j2objc?

Depending on your Android Studio 'run configuration', you may be building more of your Gradle
project than is neccessary.  For example if your run configuration includes a general 'Make'
task before running, it will build all your gradle projects, including the j2objc parts.
Instead make sure your run configuration only performs the `:android:assembleDebug` task:

<img width="664" alt="Run Configuration" src="https://cloud.githubusercontent.com/assets/11729521/9024382/95a511a4-3881-11e5-8525-9e4256990614.png">

At the command line, if you want to use the debug version of your Android app,
make sure you are running `./gradlew :android:assembleDebug` and not for example
`./gradlew build`.

### Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)

See: [How do I enable ARC for my Objective-C classes?](#how-do-i-enable-arc-for-my-objective-c-classes?)


### How do I disable a plugin task?

You can disable tasks performed by the plugin using the following configuration block in your
`build.gradle`. This is separate and alongside the `j2objcConfig` settings. For example, to
disable the `j2objcTest` task, do the following:

```gradle
// File: shared/build.gradle
j2objcTest {
    enabled = false
}
j2objcConfig {
    ...
}
```


### How do I setup dependencies with J2ObjC?

See the following FAQ answers...

### How do I setup a dependency to a third-party Java library?

These are the kinds of dependencies usually specified as follows in build `shared/build.gradle`.
We'll use `gson` as an example:
```gradle
dependencies {
    compile 'com.google.code.gson:gson:2.3.1'
}
```

Per J2ObjC's documentation: "Developers must have source code for their Android app, which
they either own or are licensed to use" - which includes libraries.
The dependencies directive above only associates your project with the .class files,
not the .java source files, of your third-party dependency.

In order to use such a dependency with J2ObjC, you'll need to find the associated source jars.
For many libraries and repositories, source jars are available easily from the library page.  For
example, if you are using `mavenCentral()`, you could find the `gson` source by downloading
the `gson-2.3.1-sources.jar` file from 
[search.maven.org](http://search.maven.org/#artifactdetails%7Ccom.google.code.gson%7Cgson%7C2.3.1%7Cjar),
and putting that in your `shared/srcLibs` directory.

Then add the following to your j2objcConfig:
```gradle
j2objcConfig {
    // This will automatically incorporate just the neccessary files from
    // the translateSourcepaths.
    translateArgs '--build-closure'
    
    // Repeat for every source jar dependency you have.
    translateSourcepaths 'srcLibs/gson-2.3.1-sources.jar'
    
    ...
}
```

In the future, this kind of dependency should be inferred automatically from the corresponding
Java dependency - [issue 41](https://github.com/j2objc-contrib/j2objc-gradle/issues/41).

Also note that this directly incorporates only the neccessary files from your dependencies into
your Objective C libraries.  It does not produce a separate `gson` Objective C library that you can
use standalone.  You would need to create a separate Gradle Java project manually to do that.

### How do I setup a dependency on a Java project?

The Java project must use the [Gradle Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html).
If project `shared` depends on Gradle Java Project A, and you want J2Objc generated Project
`shared` to depend on J2ObjC generated Project A, then add to `shared/build.gradle`:

```gradle
// File: shared/build.gradle
j2objcConfig {
    dependsOnJ2objc project(':A')
}
```

Project A needs to have the J2objc Gradle Plugin applied along with `j2objcConfig {...}`.
This applies transitively, so in turn it may need `dependsOnJ2objc` again, if say A
depends on Gradle Java Project B. *

The library will be linked in and the headers available for inclusion. Project A will be
built first.

In the future, this kind of dependency should be inferred automatically from the corresponding
Java dependency - [issue 41](https://github.com/j2objc-contrib/j2objc-gradle/issues/41).

* Alternatively you can try building using `--build-closure` (TODO: need item on this).

### How do I setup a dependency on a prebuilt native library?

For a Java and J2ObjC project `shared` that depends on library libpreBuilt pre-built outside
of Gradle in directory /lib/SOMEPATH, with corresponding headers in /include/SOMEPATH.
Add to `shared/build.gradle`:

```gradle
// File: shared/build.gradle
j2objcConfig {
    extraObjcCompilerArgs '-I/include/SOMEPATH'
    extraLinkerArgs '-L/lib/SOMEPATH'
    extraLinkerArgs '-lpreBuilt'
}
```

The library will be linked in and the headers available for inclusion. All prebuilt libraries
must be fat binaries with the architectures defined by `enabledArchs`, explained in the FAQ for
[How can I speed up my build?](#how-can-i-speed-up-my-build).


### How do I setup a dependency on a native library project?

The dependency must use the Gradle [custom native library](https://docs.gradle.org/current/userguide/nativeBinaries.html#N15F82)
If project `shared` depends on a called someLibrary from native project A.
Add to `shared/build.gradle`:

```gradle
// File: shared/build.gradle
j2objcConfig {
    extraNativeLib project: ':A', library: 'someLibrary', linkage: 'static'
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
}
```

For more details:
- http://j2objc.org/docs/Cycle-Finder-Tool.html
- https://groups.google.com/forum/#!msg/j2objc-discuss/2fozbf6-liM/R83v7ffX5NMJ


### How do I develop on Windows or Linux?

Windows and Linux support is limited to the j2objcCycleFinder and j2objcTranslate tasks.
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
