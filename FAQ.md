# FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))


### What version of Gradle do I need?

You need at least [Gradle version 2.4](https://discuss.gradle.org/t/gradle-2-4-released/9471),
due to support for native compilation features.


### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to first create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.

### How do I setup multiple related j2objc or native projects?
You can express three kinds of dependencies within j2objcConfig:

1.  The common case is that Java and j2objc Project B depends on Java and j2objc Project A,
and you need the Project B J2ObjC generated library to depend on the Project A J2ObjC
generated library. In this case add to B.gradle:
    ```
    j2objcConfig {
        dependsOnJ2objc project(':A')
    }
    ```
This kind of dependency should be inferred automatically from the corresponding Java
dependency in the future.

2.  Java and j2objc project B depends on a
[custom native library](https://docs.gradle.org/current/userguide/nativeBinaries.html#N15F82)
called someLibrary in native project A.  Add to B.gradle:
    ```
    j2objcConfig {
        extraNativeLib project: ':A', library: 'someLibrary', linkage: 'static'
    }
    ```

3.  Java and j2objc project B depends on library libpreBuilt pre-built outside of
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

### How do I properly pass multiple arguments to j2objc?

Make sure your arguments are separate strings, not a single space-concatenated string.
```
j2objcConfig {
    // CORRECT
    translateArgs '-use-arc', '-prefixes', 'file.prefixes'

    // CORRECT
    translateArgs '-use-arc'
    translateArgs '-prefixes'
    translateArgs 'file.prefixes'

    // WRONG
    translateArgs '-use-arc -prefixes file.prefixes'
    
    // WRONG
    translateArgs '-use-arc'
    translateArgs '-prefixes file.prefixes'
}
```

### How do I include Java files from additional source directories?

In order to include source files from sources different than ``src/main/java`` you have to
[modify the Java plugin's sourceSet(s)](https://docs.gradle.org/current/userguide/java_plugin.html#N11FD1).
For example, if you want to include files from ``src-gen/base`` both into your JAR and (translated) into
your Objective C libraries, then add to your ``build.gradle``:

```
sourceSets {
  main {
    java {
      srcDir 'src-gen/base'
    }
  }
}
```


### How do I enable ARC for my Objective-C classes?

Add the following to your configuration block. [See](https://developer.apple.com/library/mac/releasenotes/ObjectiveC/RN-TransitioningToARC/Introduction/Introduction.html#//apple_ref/doc/uid/TP40011226-CH1-SW15).

```
j2objcConfig {
   translateArgs '-use-arc'
   extraObjcCompilerArgs '-fobjc-arc'
}
```

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


### Advanced Cycle Finder Setup

This uses a specially annotated version of the `jre_emul` source that marks all the
erroneously matched cycles such that they can be ignored. It requires downloading
and building the J2ObjC source:

1. Download the j2objc source to a directory (hereafter J2OJBC_REPO):<br>
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
