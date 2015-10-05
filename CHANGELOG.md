To see everything that has changed between version vA.B.C and vX.Y.Z, visit:
https://github.com/j2objc-contrib/j2objc-gradle/compare/vA.B.C...vX.Y.Z

Change numbers below are github.com pull requests; peruse #NNN at:
https://github.com/j2objc-contrib/j2objc-gradle/pull/NNN

# Prerelease Alphas

## vNext (HEAD)
New functionality:
* Support for Xcode 7 and j2objc 0.9.8.2.1 #483
* `J2objcConfig.minIosVersion` controls minimum iOS version #483

Breaking changes/functionality:
* No longer supports Xcode 6 and lower or j2objc 0.9.8.2 and lower #483

Code quality:
* Multi-project integration tests disabled temporarily (system tests are used instead) #483

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
