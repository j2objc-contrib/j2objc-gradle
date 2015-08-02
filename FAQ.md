# FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))


### What version of Gradle do I need?

You need at least [Gradle version 2.4](https://discuss.gradle.org/t/gradle-2-4-released/9471),
due to support for native compilation features.


### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to first create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.


### How do I properly pass multiple arguments to J2ObjC?

Make sure your arguments are separate strings, not a single space-concatenated string.

    j2objcConfig {
        // CORRECT
        translateArgs '-use-arc'
        translateArgs '-prefixes', 'file.prefixes'

        // CORRECT
        translateArgs '-use-arc', '-prefixes', 'file.prefixes'

        // WRONG
        translateArgs '-use-arc -prefixes file.prefixes'
    }


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

    sourceSets {
        main {
            java {
                srcDir 'src-gen/base'
            }
        }
    }


### How do I develop with Swift?

To work with Swift in Xcode, you need to configure a [bridging header](https://developer.apple.com/library/ios/documentation/Swift/Conceptual/BuildingCocoaApps/MixandMatch.html#//apple_ref/doc/uid/TP40014216-CH10-XID_81).
Within that bridging header, include the file needed for using the JRE and any classes that you'd like
to access from Swift code.

    // File: ios/IOS-APP/IOS-APP-bridging-header.h

    // J2ObjC requirement for Java Runtime Environment
    // Included from /J2OBJC_HOME/include
    #import "JreEmulation.h"

    // Swift accessible J2ObjC translated classes
    // Included from `shared/build/j2objcOutputs/src/main/objc`
    #import "MyClassOne.h"
    #import "MyClassTwo.h"


### How do I enable ARC for my Objective-C classes?

Add the following to your configuration block. [See](https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15).

    j2objcConfig {
       translateArgs '-use-arc'
       extraObjcCompilerArgs '-fobjc-arc'
    }


### How do I call finalConfigure()?

You must always call `finalConfigure()` at the end of `j2objcConfig {...}` within your project's
`build.gradle` file. You need to include an otherwise empty `j2objcConfig {...}` block with this
call even if you do not need to customize any other `j2objConfig` option.

    j2objcConfig {
        ...
        finalConfigure()
    }


### Why is my Android build so much slower after adding j2objc?

Depending on your Android Studio 'run configuration', you may be building more of your Gradle
project than is neccessary.  For example if your run configuration includes a general 'Make'
task before running, it will build all your gradle projects, including the j2objc parts.
Instead make sure your run configuration only performs the `:android:assembleDebug` task:

<img width="664" alt="Run Configuration" src="https://cloud.githubusercontent.com/assets/11729521/9024382/95a511a4-3881-11e5-8525-9e4256990614.png">

At the command line, if you want to use the debug version of your android app,
make sure you are running `./gradlew :android:assembleDebug` and not for example
`./gradlew build`.

### Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)

See: [How do I enable ARC for my Objective-C classes?](#how-do-i-enable-arc-for-my-objective-c-classes?)


### How do I disable a plugin task?

You can disable tasks performed by the plugin using the following configuration block in your
`build.gradle`. This is separate and alongside the `j2objcConfig` settings. For example, to
disable the `j2objcTest` task, do the following:

    // File: shared/build.gradle
    j2objcTest {
        enabled = false
    }
    j2objcConfig {
        ...
    }


### How do I setup dependencies with J2ObjC?

See the following FAQ answers...


### How do I setup a dependency on a Gradle Java project?

If project `shared` depends on Gradle Java Project A, and you want J2Objc generated Project
`shared` to depend on J2ObjC generated Project A. Add to `shared/build.gradle`:

    // File: shared/build.gradle
    j2objcConfig {
        dependsOnJ2objc project(':A')
    }

Project A needs to have the J2objc Gradle Plugin applied and the `j2objcConfig` with the
`finalConfigure()` call. This applies transitively, so in turn it may need `dependsOnJ2objc`
again. Alternatively you can try building using `--build-closure` (TODO: need item on this).
The library will be linked in and the headers available for inclusion. Project A will be
built first.

In the future, this kind of dependency should be inferred automatically from the corresponding
Java dependency - [issue 41](https://github.com/j2objc-contrib/j2objc-gradle/issues/41).


### How do I setup a dependency on a prebuilt native library?

For a Java and J2ObjC project `shared` that depends on library libpreBuilt pre-built outside
of Gradle in directory /lib/SOMEPATH, with corresponding headers in /include/SOMEPATH.
Add to `shared/build.gradle`:

    // File: shared/build.gradle
    j2objcConfig {
        extraObjcCompilerArgs '-I/include/SOMEPATH'
        extraLinkerArgs '-L/lib/SOMEPATH'
        extraLinkerArgs '-lpreBuilt'
    }

The library will be linked in and the headers available for inclusion. All prebuilt libraries
must be fat binaries with the architectures defined by `supportedArchs` in
[j2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy).


### How do I setup a dependency on a Gradle native library project?

If project `shared` depends on a
[custom native library](https://docs.gradle.org/current/userguide/nativeBinaries.html#N15F82)
called someLibrary from native project A. Add to `shared/build.gradle`:

    // File: shared/build.gradle
    j2objcConfig {
        extraNativeLib project: ':A', library: 'someLibrary', linkage: 'static'
    }


### Cycle Finder Basic Setup

This task is disabled by default as it requires greater sophistication to use. It runs the
`cycle_finder` tool in the J2ObjC release to detect memory cycles in your application.
Objects that are part of a memory cycle on iOS won't be deleted as it doesn't support
garbage collection.

The basic setup will implicitly check for 40 memory cycles - this is the expected number
of erroneous matches with `jre_emul` library for J2ObjC version 0.9.6.1. This may cause
issues if this number changes with future versions of J2ObjC libraries.

    // File: shared/build.gradle
    j2objcCycleFinder {
        enabled = true
    }

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
source and whitelist. Note how this gives and expected cycles of zero.

```
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
