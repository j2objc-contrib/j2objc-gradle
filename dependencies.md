This document explains how to configure dependencies between multiple projects, libraries, etc.

# Guide
This guide explains common use cases, but skips some of the corner-cases and technical detail.
See the [Reference](#reference) for that additional information.

## TL/DR
If simple enough, your dependencies may be configurable automatically.  First try adding:
```gradle
   autoConfigureDeps true
```
at the top of your Gradle project's `j2objcConfig` section.  If that fails, follow one of the
sections below.

## Using built-in libraries only
__Requirements__
* One or more j2objc Java projects
* Only built-in libraries

If you have one or more shared Java J2ObjC Gradle project,
and depend only on libraries provided in the J2ObjC distribution (see `J2objcConfig.translateJ2objcLibs`
in your version of the plugin), no further dependency configuration is required beyond that described
in [README.md](README.md) and the `autoConfigureDeps true` line.

## One j2objc project, plus simple external libraries
__Requirements__
* Single j2objc Java project `common`
* Simple third-party libraries with source

If your single Java project depends on one or more libraries in a standard Maven repository (such as
Maven Central or jCenter) and each of those libraries provide their Java source in a `-sources.jar` file
in that Maven repository, you can modify your `common/build.gradle` as follows.  We'll use Gson as the
example external library.

```gradle
// Before: common/build.gradle
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
// After: common/build.gradle
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
* Multiple j2objc Java projects: 'common' depends on 'base' 
* Simple third-party libraries

With multiple Java projects and multiple external libraries, break up the problem.
First follow the advice in [Multiple projects](#multiple-projects-built-in-libraries),
on setting.  Next, for each external library, build a
[standalone J2ObjC project](#build-standalone-third-party-library).  Finally, adjust
your Java project build files as follows.  Consider that your `common` project depends on Gson,
and you have already created a `third-party-gson/build.gradle`

```gradle
// Before: common/build.gradle
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
// After: common/build.gradle
dependencies {
    // MODIFY: You must add a specific version number, if you did not have one before.
    compile project(':third-party-gson')
    // (plus any other libraries provided by J2ObjC)
}

j2objcConfig {
    // ADD: This causes the plugin to link in the J2ObjC project third-party-gson above.
    autoConfigureDeps true
    // ...
    finalConfigure()
}
```

You can see an example
[here](https://github.com/j2objc-contrib/j2objc-gradle/tree/master/systemTests/externalLibrary1).

# Reference
This describes the behavior of each dependency configuration used by the plugin.
Behavior sometimes depends on whether `autoConfigureDeps` is set to false (the
default) or true.

## J2ObjC Settings

### autoConfigureDeps
Setting this value to true causes the plugin to automatically convert Java
dependencies (compile and testCompile) to J2ObjC dependencies.  Note that any
J2ObjC dependencies (j2objcTranslationClosure, j2objcTranslation[Test], j2objcLinkage[Test])
added manually always have effect.

## Dependency configurations

### compile
If `autoConfigureDeps` is false, has no effect.  When true,
* for a project, creates a `j2objcLinkage` dependency on the project.
* for an external module, creates a `j2objcTranslationClosure` dependency on the associated sources
Jar for the module.

### testCompile
If `autoConfigureDeps` is false, has no effect.  When true,
* for a project, creates a `j2objcLinkage` dependency on the project.
* for an external module, fails immediately.  We cannot build tests using --build-closure due to the
risk of duplicate symbols.

### j2objcTranslationClosure
Use this only with a Jar of Java source files.  These files will be provided to the j2objc
translator with `--build-closure`; where your main source set relies on classes defined
in this source Jar, j2objc will additionally translate that class (and all of its transitive
dependencies).  The plugin will include the result in your project's own library.

This is only safe to use when you are building a single J2ObjC Java Gradle project.
If you have multiple projects, when the libraries for those projects are linked together,
you will get duplicate symbole errors.

### j2objcTranslation
Use this only with a Jar of Java source files.  These files will be added to the main Java
source set.  They will be both compiled into you project's compiled Jar file, and also be
fully translated by j2objc, and the Objective C will be included in your project's static
library.

### j2objcTestTranslation
Use this only with a Jar of Java JUnit test source files.  These files will be added to the test Java
source set, and executed in Java.  These will also be fully translated by j2objc and then executed
in their Objective C form.  They will not be included in your project's static library.

### j2objcLinkage
Use this only with another Gradle Java J2ObjC project.  The other project must have the J2ObjC Gradle
plugin applied to it as well.  The other project will be linked with this project's main source set.

### j2objcTestLinkage
Use this only with another Gradle Java J2ObjC project.  The other project must have the J2ObjC Gradle
plugin applied to it as well.  The other project will be linked with this project's test source set.
