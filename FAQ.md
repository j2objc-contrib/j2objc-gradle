# FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))


### What version of Gradle do I need?

You need at least [Gradle version 2.4](https://discuss.gradle.org/t/gradle-2-4-released/9471),
due to support for native compilation features.


### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to first create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.


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

You must always call finalConfigure at the end of `j2objcConfig {...}` within your project's
`build.gradle` file. You need to include an otherwise empty j2objcConfig { } block with this
call even if you do not need to customize any other `j2objConfig` option.

    j2objcConfig {
        ...
        finalConfigure()
    }


### Error: implicit declaration of function 'JreRelease' is invalid in C99 [-Werror,-Wimplicit-function-declaration] JreRelease(this$0_)

See: [How do I enable ARC for my Objective-C classes?](#how-do-i-enable-arc-for-my-objective-c-classes?)
