# j2objc-gradle
Gradle plugin for j2objc (Java to Objective-C transpiler)

### Usage

1. Download the latest j2objc release and unzip it to a directory: https://github.com/google/j2objc/releases
1. Donwload the latest ``j2objc.gradle`` file and place it alongside the ``build.gradle`` file of the project to be translated:  https://raw.githubusercontent.com/brunobowden/j2objc-gradle/master/j2objc.gradle
1. Copy and paste the following to your project's ``build.gradle`` file, modifying the indicated parts. For now, always run ``gradlew clean`` when changing this configuration:

    ```
    apply from: 'j2objc.gradle'
    
    j2objcConfig {
        // MODIFY to where your unzipped j2objc directory is located
        // NOTE download the latest version from: https://github.com/google/j2objc/releases
        j2objcHome null  // e.g. "${projectDir}/../../j2objc or absolute path

        // MODIFY to where generated objc files should be put for Xcode project
        // NOTE these files should be checked in to the repository and updated as needed
        // NOTE this should contain ONLY j2objc generated files, other contents will be deleted
        destDir null  // e.g. "${projectDir}/../Xcode/j2objc-generated"
    }
    ```
4. Now you can run the following task in you Gradle project: 

    ```
    j2objcCycleFinder - Find cycles that can cause memory leaks, see https://github.com/google/j2objc/wiki/Cycle-Finder-Tool
    j2objcTranslate   - Translates to Objective-C, depends on java or Android project if found
    j2objcCompile     - Compile Objective-C files and build Objective-C binary (named 'runner')
    j2objcTest        - Run all java tests against the Objective-C binary
    j2objcCopy        - Copy generated Objective-C files to Xcode project
    ```

#### Further Configuration

See the ```J2objcPluginExtension``` class: https://github.com/brunobowden/j2objc-gradle/blob/master/j2objc.gradle#L82

### Errors
If you encounter a problem, please file a detailed bug at https://github.com/brunobowden/j2objc-gradle/issues/new

Help is easier to give if you provide github git or gist links to your gradle source files, or (redacted) copies within the issue.

Run gradle with the '--debug' flag to get more information that may be useful for the bug report.

### Future Plans

The intent is to evolve this in to a full gradle plugin over time. Happy to accept contributions.

### Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md#quick-start).

### License

This library is distributed under the Apache 2.0 license found in the
[LICENSE](./LICENSE) file.
