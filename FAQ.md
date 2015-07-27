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

This is a [known issue](https://github.com/j2objc-contrib/j2objc-gradle/issues/306) if you don't
have any tests.
If you are doing `./gradlew clean build`, try instead `./gradlew clean && ./gradlew build`.

When you don't have any test source files, The plugin creates a placeholder to force the
creation of a test binary; this is done during project configuration phase, but `clean` deletes
this file before `build` can use it.


### How do I include Java files from additional source directories?

In order to include source files from sources different than ``src/main/java`` you have to
[modify the Java plugin's sourceSet(s)](https://docs.gradle.org/current/userguide/java_plugin.html#N11FD1).
For example, if you want to include files from ``src-gen/base`` both into your JAR and (translated) into
your Objective C libraries, then add to your ``build.gradle``:

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

```
// File: IOS-APP-bridging-header.h

// Required for Swift initialization of Java objects (they all inherit from JavaObject)
#import "JreEmulation.h"

// Swift accessible j2objc translated classes, referenced from `shared/build/j2objcOutputs/src/main/objc`
#import "MyClassOne.h"
#import "MyClassTwo.h"
```


### How do I enable ARC for my Objective-C classes?

Add the following to your configuration block. [See](https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15).

    j2objcConfig {
       translateArgs '-use-arc'
       extraObjcCompilerArgs '-fobjc-arc'
    }


### How do I call finalConfigure()?

You must always call `finalConfigure()` at the end of `j2objcConfig {...}` within your project's
`build.gradle` file. You need to include an otherwise empty j2objcConfig { } block with this
call even if you do not need to customize any other `j2objConfig` option.

    j2objcConfig {
        ...
        finalConfigure()
    }


### Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)

See: [How do I enable ARC for my Objective-C classes?](#how-do-i-enable-arc-for-my-objective-c-classes?)


### How do I disable a plugin task?

You can disable tasks performed by the plugin using the following configuration block in your
`build.gradle`. This is separate and alongside the j2objcConfig settings. For example, to
disable the `j2objcTest` task, do the following:

    j2objcTest {
        enabled = false
    }

    j2objcConfig {
        ...
    }


### How do I setup multiple related J2ObjC or native projects?

You can express three kinds of dependencies within j2objcConfig:

1. The common case is that Java and j2objc Project B depends on Java and J2ObjC Project A,
and you need the Project B J2ObjC generated library to depend on the Project A J2ObjC
generated library. In this case add to B.gradle:

```
    j2objcConfig {
        dependsOnJ2objc project(':A')
    }
```

This kind of dependency should be inferred automatically from the corresponding Java
dependency in the future.

2. Java and j2objc project B depends on a
[custom native library](https://docs.gradle.org/current/userguide/nativeBinaries.html#N15F82)
called someLibrary in native project A.  Add to B.gradle:

```
    j2objcConfig {
        extraNativeLib project: ':A', library: 'someLibrary', linkage: 'static'
    }
```

3. Java and j2objc project B depends on library libpreBuilt pre-built outside of
Gradle in directory /lib/SOMEPATH, with corresponding headers in /include/SOMEPATH.
Add to B.gradle:

```
    j2objcConfig {
        extraObjcCompilerArgs '-I/include/SOMEPATH'
        extraLinkerArgs '-L/lib/SOMEPATH'
        extraLinkerArgs '-lpreBuilt'
    }
```

In (1) and (2), A's library will be linked in and A's headers will be available for inclusion, and
B will automatically build after A.  (3) is not supported by Gradle's dependency management
capabilities; you must ensure preBuilt's binary and headers are available before project B is built.


### Cycle Finder Basic Setup

This task is disabled by default as it requires greater sophistication to use. It runs the
`cycle_finder` tool in the J2ObjC release to detect memory cycles in your application.
Objects that are part of a memory cycle on iOS won't be deleted as it doesn't support
garbage collection.

The basic setup will implicitly check for 40 memory cycles - this is the expected number
of erroneous matches with `jre_emul` library for J2ObjC version 0.9.6.1. This may cause
issues if this number changes with future versions of J2ObjC libraries.

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

3. Configure j2objcConfig in build.gradle so CycleFinder uses the annotated J2ObjC source
and whitelist. Note how this gives and expected cycles of zero.

```
    j2objcConfig {
        cycleFinderArgs '--whitelist', 'J2OBJC_REPO/jre_emul/cycle_whitelist.txt'
        cycleFinderArgs '--sourcefilelist', 'J2OBJC_REPO/jre_emul/build_result/java_sources.mf'
        cycleFinderExpectedCycles 0
    }
```

For more details:
- http://j2objc.org/docs/Cycle-Finder-Tool.html
- https://groups.google.com/forum/#!msg/j2objc-discuss/2fozbf6-liM/R83v7ffX5NMJ
