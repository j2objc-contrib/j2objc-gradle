# j2objc-gradle
Gradle plugin for [J2ObjC](https://github.com/google/j2objc) (Java to Objective-C transpiler)

### Usage
At HEAD, this plugin is in a state of significant flux as we refactor it into a first-class Gradle plugin for our beta.
You may wish to wait for the beta release as we will not support the alpha release in any form going forward.

If you still want to try our alpha release, please see instructions at tag
https://github.com/brunobowden/j2objc-gradle/tree/v0.1.0-alpha instead, and download the release
at the same tag: https://github.com/brunobowden/j2objc-gradle/releases/tag/v0.1.0-alpha

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
