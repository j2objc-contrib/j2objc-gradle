# j2objc-gradle
Gradle plugin for [J2ObjC](https://github.com/google/j2objc) (Java to Objective-C transpiler)

### Usage
At HEAD, this plugin is in a state of significant flux as we refactor it into a first-class Gradle plugin for our beta.
You may wish to wait for the beta release as we will not support the alpha release in any form going forward.

You should start with a clean java only project without any android dependencies, preferably named `'shared'`. This must be buildable using Gradle's standard `'java'` plugin. It may start as an empty project and allows you to gradually shift over code from an existing Android application.

Within the `'shared'` project's `build.gradle`, apply the j2objc plugin before the java plugin:

    // 'shared' build.gradle
    plugins {
        id "com.github.j2objccontrib.j2objcgradle" version "0.2.1-alpha"
    }
    apply plugin: 'java'

Within the Android application's project `build.gradle`, make it dependent on the `shared` project:

    // 'android-app' build.gradle
    dependencies {
        compile project(':shared')
    }

The preferred folder structure is:

    workspace
    │   .gitignore
    │   build.gradle
    ├───android
    |   │   build.gradle          // dependency on ':shared'
    |   └   src                   // src/main/java/...
    ├───shared
    |   │   build.gradle          // apply 'j2objc' then 'java' plugins
    |   │   build/j2objcOutputs   // j2objc generated headers and libraries 
    |   └   src                   // src/main/java/...
    └───Xcode
        │   Project
        └   ProjectTests

### Interim development
For plugin contributors, you should build the plugin from this repository's root:
```
./gradlew build
```

This will create a .jar containing the plugin at projectDir/build/libs/j2objc-gradle-*.jar (depending on the version).

In order to test your modification to the plugin using your own project, use the following build script in your
java project's build.gradle:
```
buildscript {
    dependencies {
        classpath files('/PATH_TO_J2OBJC_PLUGIN/j2objc-gradle/build/libs/j2objc-gradle-0.1.0-alpha.jar')
    }
}

apply plugin: 'j2objc'

// (regular j2objcConfig here...)
```


### Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).

### License

This library is distributed under the Apache 2.0 license found in the
[LICENSE](./LICENSE) file.
