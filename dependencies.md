This document explains how to configure dependencies between multiple projects, libraries, etc.

# Guide
This guide explains common use cases, but skips some of the corner-cases and technical detail.
See the [Reference](#reference) for that additional information.


## TL/DR
If simple enough, your dependencies may be configurable automatically.  First try adding:
```gradle
j2objcConfig {
   autoConfigureDeps true
   // ...
}
```
at the top of your Gradle project's `j2objcConfig` section.  If that fails, follow one of the
sections below.


## Using built-in libraries only
__Requirements__
* One or more j2objc Java projects
* Only built-in libraries

If you have one or more shared Java J2ObjC Gradle project,
and depend only on libraries provided in the J2ObjC distribution (in 0.9.8.2.1 this is:
* com.google.guava:guava
* com.google.j2objc:j2objc-annotations
* com.google.protobuf:protobuf-java
* junit:junit (test only)
* org.mockito:mockito-core (test only)
* org.hamcrest:hamcrest-core (test only)

), no further dependency configuration is required beyond that described
in [README.md](README.md) and the `autoConfigureDeps true` line.


## One j2objc project, plus simple external libraries
__Requirements__
* Single j2objc Java project `shared`
* Simple third-party libraries with source

If your single Java project depends on one or more libraries in a standard Maven repository (such as
Maven Central or jCenter) and each of those libraries provide their Java source in a `-sources.jar` file
in that Maven repository, you can modify your `shared/build.gradle` as follows.  We'll use Gson as the
example external library.

```gradle
// Before: shared/build.gradle
dependencies {
    compile 'com.google.code.gson:gson'
    // (plus any other libraries provided by J2ObjC)
}

j2objcConfig {
    // ...
    finalConfigure()
}
```

```gradle
// After: shared/build.gradle
dependencies {
    // MODIFY: You must add a specific version number, if you did not have one before.
    compile 'com.google.code.gson:gson:2.3.1'
    // (plus any other libraries provided by J2ObjC)
}

j2objcConfig {
    // ADD: This causes the plugin to download the sources and transpile them into
    // your project using --build-closure.
    autoConfigureDeps true
    // ...
    finalConfigure()
}
```


## Build standalone third-party library
__Requirements__
* A library associated with a source Jar file
* (Optionally) A test source Jar file

Each standalone third-party library you'd like to build must be associated with a separate
Gradle project.  For example, to build the Gson third-party library, you could create
`third-party-gson/build.gradle`:
```gradle
// apply the J2ObjC plugin as usual.

dependencies {
    j2objcTranslation 'com.google.code.gson:gson:2.3.1:sources'
}

j2objcConfig {
    // No tests provided with the Gson library.
    testMinExpectedTests 0
    finalConfigure()
}
```

If the library also provides a test source Jar, also add for example:
```gradle
dependencies {
    // Unfortunately: Gson doesn't actually have a test-sources.jar.
    j2objcTestTranslation 'com.google.code.gson:gson:2.3.1:test-sources'
}
```

You can see many examples of configuring builds for third-party libraries
[here](https://github.com/madvay/j2objc-common-libs-e2e-test/tree/master/libraryBuilds).


## Multiple projects and external libraries
__Requirements__
* Multiple j2objc Java projects: 'shared' depends on 'base' 
* Simple third-party libraries

With multiple Java projects and multiple external libraries, break up the problem.
First add the usual `compile project(':base')` line to `shared/build.gradle`,
to configure dependencies between the Java projects. Next, for each external library, create a
[standalone J2ObjC project](#build-standalone-third-party-library).  Finally, adjust
your Java project build files as follows.  If your `shared` project depends on Gson, for example,
and you have already created a `third-party-gson/build.gradle`, then:

```gradle
// Before: shared/build.gradle
dependencies {
    compile project(':base')
    compile 'com.google.code.gson:gson'
    // (plus any other libraries provided by J2ObjC)
}

j2objcConfig {
    // ...
    finalConfigure()
}
```

```gradle
// After: shared/build.gradle
dependencies {
    compile project(':base')
    // MODIFY: Convert the external module dependency to a Gradle project dependency.
    compile project(':third-party-gson')
    // (plus any other libraries provided by J2ObjC)
}

j2objcConfig {
    // ADD: This causes the plugin to link in the J2ObjC projects third-party-gson and buase above.
    autoConfigureDeps true
    // ...
    finalConfigure()
}
```

You can see an example
[here](https://github.com/j2objc-contrib/j2objc-gradle/tree/master/systemTests/externalLibrary1).


## Using native libraries built with Gradle (advanced)
__Requirements__
* One or more Java J2ObjC Gradle projects
* Gradle native library project

The dependency must use the Gradle
[custom native library](https://docs.gradle.org/current/userguide/nativeBinaries.html#N15F82).
If project `shared` depends on a library called someLibrary from native project A,
add to `shared/build.gradle`:

```gradle
// File: shared/build.gradle
j2objcConfig {
    extraNativeLib project: ':A', library: 'someLibrary', linkage: 'static'
    finalConfigure()
}
```

Note that J2ObjC does not understand this native library in any way - your Java code must still
compile as always, ignoring this library.

## Using prebuilt native libraries (advanced)
__Requirements__
* Existing static library (.a file) and header files

For a Java and J2ObjC project `shared` that depends on library `libpreBuilt.a` pre-built outside
of Gradle in directory /lib/SOMEPATH, with corresponding headers in /include/SOMEPATH,
add to `shared/build.gradle`:

```gradle
// File: shared/build.gradle
j2objcConfig {
    extraObjcCompilerArgs '-I/include/SOMEPATH'
    extraLinkerArgs '-L/lib/SOMEPATH'
    extraLinkerArgs '-lpreBuilt'
}
```

The library will be linked in and the headers available for inclusion. All prebuilt libraries
must be fat binaries with the architectures defined by `supportedArchs` in
[j2objcConfig.groovy](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcConfig.groovy).

Note that J2ObjC does not understand this native library in any way - your Java code must still
compile as always, ignoring this library.


# Reference
This describes the behavior of each J2objcConfig setting and dependency configuration used by the plugin
for dependency management.
Behavior sometimes depends on whether `autoConfigureDeps` is set to false (the
default) or true.


## J2ObjC Settings


### autoConfigureDeps
Setting this value to true causes the plugin to automatically convert Java
dependencies (compile and testCompile) to J2ObjC dependencies.  Note that any
J2ObjC dependencies (j2objcTranslationClosure, j2objcTranslation[Test], j2objcLinkage[Test])
added manually always have effect.


### extraNativeLib
This creates a static library linkage between each of your main Objective C library and your test Objective C
binary, and the specified Gradle native library.
See [Gradle docs](https://docs.gradle.org/2.4/userguide/nativeBinaries.html#N160EF).


### extraObjcCompilerArgs, extraLinkerArgs
Arguments passed to these methods are added - without modification - to the Clang
Objective C compiler and linker, respectively.
See [Gradle docs](https://docs.gradle.org/2.4/userguide/nativeBinaries.html#N16030).


## Dependency configurations


### compile, testCompile
If `autoConfigureDeps` is false, has no effect related to J2ObjC.  When true,
* for a project, creates a `j2objcLinkage` (or `j2objcTestLinkage`) dependency on the project.
* for an external module already included in the J2ObjC distribution, has no additional effect
(this will work automatically).
* for any other external module, creates a `j2objcTranslationClosure` dependency on the associated
sources Jar for the module. For testCompile, this fails immediately as we cannot build tests using
the --build-closure due to the risk of duplicate symbols.


### j2objcTranslationClosure
Use this only with a Jar of Java source files.  These files will be provided to the j2objc
translator with `--build-closure`; where your main source set relies on classes defined
in this source Jar, j2objc will additionally translate that class (and all of its transitive
dependencies).  The plugin will include the result in your project's own library.

This is only safe to use when you are building a single J2ObjC Java Gradle project.
Consider project A depending on B and C, and both B and C depending on external library L.
With j2objcTranslationClosure, L's symbols will be part of both B and C, and A will fail to
build with duplicate symbol errors.


### j2objcTranslation, j2objcTestTranslation
Use this only with a Jar of Java source files or Java JUnit test source files, and
these files will be added to the main or test Java source set, respectively.  These files will
be fully translated by j2objc.
For j2objcTranslation, they will be both compiled into your project's compiled Jar file,
and the Objective C will be included in your project's static library.
For j2objcTestTranslation, the Java and Objective C versions of the tests will get executed
in the `test` and `j2objcTest` tasks, respectively, but the tests will not be included in the
compiled Jar or static library.

### j2objcLinkage, j2objcTestLinkage.
Use this only with another Gradle Java J2ObjC project.  The other project must have the J2ObjC Gradle
plugin applied to it as well.  The other project will be linked with this project's main or
test source set, respectively.
