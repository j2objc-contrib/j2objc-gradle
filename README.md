# j2objc-gradle
Gradle plugin for [J2ObjC](https://github.com/google/j2objc) (Java to Objective-C transpiler)

### Usage
At HEAD, this plugin is in a state of significant flux as we refactor it into a first-class Gradle plugin for our beta.
You may wish to wait for the beta release as we may make backwards incompatible changes before that point.

You should start with a clean java only project without any android dependencies, preferably named `'shared'`. This must be buildable using Gradle's standard `'java'` plugin. It may start as an empty project and allows you to gradually shift over code from an existing Android application.

Within the `'shared'` project's `build.gradle`, apply the j2objc plugin before the java plugin.

Note: the `plugins {...}` syntax does not work for the j2objc plugin, so you must use the old buildscript style. For more info see: https://github.com/j2objc-contrib/j2objc-gradle/issues/130

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

    apply plugin: 'java'
    apply plugin: 'com.github.j2objccontrib.j2objcgradle'

Within the Android application's project `build.gradle`, make it dependent on the `shared` project:

    // File: android/build.gradle
    dependencies {
        compile project(':shared')
    }

The preferred folder structure is:

    workspace
    │   .gitignore
    │   build.gradle
    ├───android
    │   ├───build.gradle          // dependency on ':shared'
    │   └───src                   // src/main/java/...
    ├───shared
    │   │   build.gradle          // apply 'j2objc' then 'java' plugins
    │   ├───build                 // build directory
    │   │   │ ...                 // build output
    │   │   └ j2objcOutputs       // j2objc generated headers and libraries
    │   └───src                   // src/main/java/...
    └───xcode
        ├───Project
        └───ProjectTests

## Disable tasks

You can disable tasks performed by the plugin using the following configuration block in your ``build.gradle``

```
j2objcTest {
    enabled = false
}
```
Where ``j2objcTest`` can be any task defined [J2objcPlugin.groovy#L70](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcPlugin.groovy#L70).

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
        // Update using "version = X.Y.Z-alpha" defined in build.gradle
        classpath files('/PATH_TO_J2OBJC_PLUGIN/j2objc-gradle/build/libs/j2objc-gradle-X.Y.Z-alpha.jar')
    }
}

apply plugin: 'java'
apply plugin: 'com.github.j2objccontrib.j2objcgradle'

// (regular j2objcConfig here...)
```


### Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).

### License

This library is distributed under the Apache 2.0 license found in the
[LICENSE](./LICENSE) file.
