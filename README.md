# j2objc-gradle
Gradle plugin for [J2ObjC](https://github.com/google/j2objc) (Java to Objective-C transpiler)

### Usage
At HEAD, this plugin is in a state of significant flux as we refactor it into a first-class
Gradle plugin for our beta. You may wish to wait for the beta release as we may make backwards
incompatible changes before that point.

You should start with a clean java only project without any android dependencies. It suggested that
this project is named `'shared'`. It must be buildable using Gradle's standard `'java'` plugin.
It may start as an empty project and allows you to gradually shift over code from an existing
Android application. See the section below on [Folder Structure](#folder-structure).

**Note: the `plugins { id 'com.github.j2objccontrib.j2objcgradle' }` syntax does
not work for the j2objc plugin. You must use the old buildscript style.
See: https://github.com/j2objc-contrib/j2objc-gradle/issues/130**

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

    // The 'java' plugin must be applied before the 'j2objc' plugin
    apply plugin: 'java'
    apply plugin: 'com.github.j2objccontrib.j2objcgradle'

    // Plugin settings:
    j2objcConfig {
        xcodeProjectDir "${projectDir}/../xcode"   // Xcode project directory
        xcodeTarget "MyTarget"                     // Xcode application target name
        // More: https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcPluginExtension.groovy#L25
    }

Within the Android application's project `build.gradle`, make it dependent on the `shared` project:

    // File: android/build.gradle
    dependencies {
        compile project(':shared')
    }

## Folder Structure

The suggested folder structure is:

    workspace
    │   .gitignore
    │   build.gradle
    ├───android
    │   │   build.gradle        // dependency on ':shared'
    │   └───src                 // src/main/java/...
    ├───shared
    │   │   build.gradle        // apply 'java' then 'j2objc' plugins
    │   ├───build               // build directory
    │   │   ├───...             // build output
    │   │   ├───binaries        // Contains test binary: testJ2objcExecutable/debug/testJ2objc
    │   │   └───j2objcOutputs   // j2objc generated headers and libraries
    │   ├───lib                 // if library jar has source, then j2objc can translate it
    │   └───src                 // src/main/java/... i.e. main code for translation
    └───xcode
        ├───Project             // j2objcXcode configures dependency on j2objcOutputs/{libs|src}
        └───ProjectTests        // j2objcXcode configures the same except "debug" libraries

## Disable tasks

You can disable tasks performed by the plugin using the following configuration block in your ``build.gradle``

    j2objcTest {
        enabled = false
    }

Of the tasks that can be disabled, the major tasks (like ``j2objcTest``), are listed in
[J2objcPlugin.groovy#L58](https://github.com/j2objc-contrib/j2objc-gradle/blob/master/src/main/groovy/com/github/j2objccontrib/j2objcgradle/J2objcPlugin.groovy#L58).

### Interim development
For plugin contributors, you should build the plugin from this repository's root:

    ./gradlew build

This will create a .jar containing the plugin at projectDir/build/libs/j2objc-gradle-*.jar (depending on the version).

In order to test your modification to the plugin using your own project, use the following build script in your
java project's build.gradle:

    buildscript {
        dependencies {
            // Update using "version = X.Y.Z-alpha" defined in build.gradle
            classpath files('/PATH_TO_J2OBJC_PLUGIN/j2objc-gradle/build/libs/j2objc-gradle-X.Y.Z-alpha.jar')
        }
    }

    apply plugin: 'java'
    apply plugin: 'com.github.j2objccontrib.j2objcgradle'

    // j2objcConfig...

Note that when rapidly developing and testing changes to the plugin by building your own project,
avoid using the Gradle daemon as issues sometimes arise with the daemon using an old version
of the plugin jar.  You can stop an existing daemon with `./gradlew --stop` and avoid the daemon
on a particular build with the `--no-daemon` flag to gradlew.

### Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).

### License

This library is distributed under the Apache 2.0 license found in the
[LICENSE](./LICENSE) file.
