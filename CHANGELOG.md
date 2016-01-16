The following link can be adapted to see the differences between two versions.
Example is given for differences between `v0.5.0-alpha` and `v0.6.0-alpha`:

https://github.com/j2objc-contrib/j2objc-gradle/compare/v0.5.0-alpha...v0.6.0-alpha


The list of releases with working links is most easily viewed here:

https://github.com/j2objc-contrib/j2objc-gradle/releases


Change numbers below are github.com pull requests; peruse #NNN at:

https://github.com/j2objc-contrib/j2objc-gradle/pull/NNN


# Prerelease Alphas

## vNext
New functionality:
* TBD

Breaking changes/functionality:
* TBD

Code quality:
* TBD


## v0.6.0-alpha
New functionality:
* Support for arbitrary count of files that otherwise exceeds command line max args #574
* Podfile manual configure of targets using xcodeTargetsManualConfig #561 #562
* Podspec output to build/j2objcOutputs #558
* Default minVersionIos => 8.3, minVersionWatchos => 2.0 #584

Fixes:
* Allow spaces in Xcode target names #564
* Gradle 2.8 compatibility #567
* Gradle 2.9 unsupported message #581
* Gradle unsupport version causes deadlock #585
* Several broken links from plugin #563

Code quality:
* Guava 19.0 system test (updated from Guava 18.0) #434


## v0.5.0-alpha
New functionality:
* Support for Xcode 7 and j2objc 0.9.8.2.1 #483
* Validate version of j2objc and provide install instructions #515
* Wilcard package prefix matching #481
* Dependencies
  * `J2objcConfig.minVersion{Ios,Osx,Watchos}` controls minimum versions of associated target #483 #512
  * Test-only dependencies on other libraries and projects #489
  * Translate and run standalone test source Jar files (such as unit tests associated with third-party libraries) #489
  * Source files generated during compilation (ex. by Dagger2) included in translation #527
* CocoaPods
  * iOS and OS X applications can be setup using CocoaPods #506
  * Automatically configure CocoaPods and Xcode projects, when present, as part of build #524
  * CocoaPods supports multi-project applications #504
  * `J2objcConfig.xcodeTargets{Ios,Osx,Watchos}` Xcode targets to link to generated libraries #522

Breaking changes/functionality:
* No longer supports Xcode 6 and lower or j2objc 0.9.8.2 and lower #483
* Minimum versions of platforms have been configured as iOS 6.0, OS X 10.6, and WatchOS 1.0 #512
* NOTE: watchOS is not yet supported due to lack of full bitcode support by J2ObjC 0.9.8.2.1.
* Default auto-generated source dir `build/source/apt` replaced by `build/classes/main` #527
* Extracted Jar source dir `build/translationExtraction` replaced by `build/mainTranslationExtraction` #489
* j2objcXcode now a dependency of j2objcAssemble{Debug|Release} and not a separate task #532

Code quality:
* Multi-project integration tests disabled temporarily (system tests are used instead) #483
* System tests include Xcode and Android Studio project examples #508 #523
* Podspecs distinguish project libraries versus J2ObjC libraries #512


## v0.4.3-alpha
New functionality:
* Automatic dependency resolution for Maven jars and Gradle projects #420
* Build external Java libraries (with source) into standalone native libraries #431
* Proper limitation of functionality on non-Mac platforms #396
* Embedded docs and versioning info for easier debugging #395
* Specify a subset of J2ObjC libraries to link to #433
* Map individual Java files to a separate version used only for J2ObjC translation #455
* Projects no longer need to guarantee the Java plugin is applied before the J2ObjC Gradle plugin #453

Breaking changes/functionality:
* Default supported architectures reduced to modern devices only (arm64, armv7, x86_64) #448
* Production (main sourceSet) and test code are now translated and built separately #474
* `J2objcConfig.filenameCollisionCheck` is now named `forceFilenameCollisionCheck`,
  and defaults true only when using `--no-package-directories` #470

Code quality:
* A small number of [common open-source Java libraries](https://github.com/j2objc-contrib/j2objc-common-libs-e2e-test) are being built end-to-end with the J2ObjC Gradle plugin
* Continuous integration on Mac #406 and on Windows #401
* Added end to end tests on OSX (running j2objc) #409 #411 etc.
* Unit tests pass on Windows #404
* Prevent publishing of bad releases #395 #398
* Docs updates (various)


## v0.4.2-alpha
Functionality:
* Translation-only mode (skips building Objective-C libraries) #349
* Support for Windows/Linux (in translation-only mode) #349
* Cycle finding moved from assembly phase to test phase #338
* Automatic linking with related Xcode projects, like tests and WatchKit apps #353
* Per-environment configuration of iOS architectures to build #358
* Environment-specific config can be provided via environment variables in addition to local.properties #361 

Code quality:
* Travis Continuous Integration #365
* Additional test coverage (various)
* Documentation fixes (various)
* Updating package prefixes will now correctly cause retranslation/recompile


## 0.4.1-alpha
(Ignore - use v0.4.2 instead).


## v0.4.0-alpha
Functionality:
- Resources copied for unit tests and Xcode build
- Xcode debug and release targets now load distinct generated libraries
- j2objcConfig syntax standardized for translateClasspaths and translateSourcepaths
- Helpful error message upon failure with full command line, stdout and sterr

Code Quality:
- @CompileStatic for plugin build type checking
- Expanded unit test coverage now comprises 81 tests
- Numerous bug fixes


## v0.3.0-alpha
Functionality:
- Args groovy style syntax for config closure
- Multiple projects with dependencies
- Incremental compile using Gradle's native clang compiler
- Fix Plugins {...} syntax

Code Quality:
- Added unit test framework


## v0.2.2-alpha
- Requires buildscript syntax as a workaround to the plugins {} syntax not working
- Lots of fixes and improvements through the system

Upgrading in-place from v0.1.0-alpha is not supported: please read README.md and
J2objcPluginExtension.groovy for instructions on using and configuring the new version.


## v0.1.0-alpha
Initial working version.
There will be significant reworking of this before a beta and 1.0 release
