Please keep in mind that your "Contribution(s)" are submitted under
the [Apache 2.0 License](LICENSE).

### Quick start

1. Fork it clicking on the top right "Fork" button
2. Create your feature branch<br>
`git checkout -b my-new-feature`
3. Commit your changes<br>
`git commit -am 'Add some feature'`
4. Push to the branch<br>
`git push origin my-new-feature`
5. Create a new Pull Request and send it for review
6. After the PR has been approved and is ready to submit see
[preparing-your-pull-request-for-submission](#preparing-your-pull-request-for-submission).


### Conditions for accepting pull requests
Please confirm that you can certify the following, 
then add the certification at the end of every commit message (with two newlines between
the main content of your commit message and the notice), entering your own information
and removing the &lt;angled brackets&gt;. If you can't certify the following, please
do not submit a pull request.

```
I, <full name> (<email@example.com>, https://github.com/<github username>), certify that
a) this Contribution is my original work, and
b) I have the right to submit this Contribution under the Apache License,
   Version 2.0 (the "License") available at
   http://www.apache.org/licenses/LICENSE-2.0, and
c) I am submitting this Contribution under the License.
```

Note: This is not legal advice.  Contributors, users, etc. must ensure their own level of comfort with
contributions certified as described here, and should seek their own legal counsel as needed.

Your Contribution (including the certification notice and other commit metadata)
will be stored publicly within the history of the repository,
and may be redistributed per the License.

_If this is your first commit_, and you are not already mentioned in the NOTICE file,
please add your name, GitHub username, and email, to the end of the
'Thanks:' list, formatted just like the entries already there:

```
  First Last @gitHubUserName <email@example.com>
```

This addition to the NOTICE file should be a part of that first commit.


### Local Development

For plugin contributors, you should build the plugin from this repository's root:

```sh
$ ./gradlew build
```

This will create a .jar containing the plugin at projectDir/build/libs/j2objc-gradle-X.Y.Z-alpha.jar

#### System tests

On OS X we have system tests under the `systemTests` directory.  Each test directory
has one root Gradle project (and zero or more subprojects), some or all of which apply the
`j2objc-gradle` plugin.  Locally you can run them as follows:

```sh
# Once per git repository and/or new release of j2objc,
# run install.sh to download j2objc and prepares the environment.
systemTests/install.sh

# Every time you want to run the tests:
# Normal Gradle build results will be displayed for each test project.
./gradlew build && systemTests/run-all.sh
```

These system tests are also run as part of OS X continuous integration builds on Travis.
You are not required to run the system tests locally before creating a PR (they can take
time and processing power), however if the tests fail on Travis you will need to update
the PR until they pass.

#### Testing on your own Gradle project

In order to test your modification to the plugin using your own project, you need to modify the
project that uses the plugin:

1. Comment out the `plugins {...}` section
1. Add the `buildscript` and `apply plugin` lines then update X.Y.Z based on "version = 'X.Y.Z-alpha'"
in j2objc-gradle/build.gradle

```gradle
// File: shared/build.gradle

//plugins {
//    id 'java'
//    id 'com.github.j2objccontrib.j2objcgradle' version 'X.Y.Z-alpha'
//}

buildscript {
    dependencies {
        classpath files('/LOCAL_J2OBJC_GRADLE/j2objc-gradle/build/libs/j2objc-gradle-X.Y.Z-alpha.jar')
    }
}
apply plugin: 'java'
apply plugin: 'com.github.j2objccontrib.j2objcgradle'
```

Note that when rapidly developing and testing changes to the plugin by building your own project,
avoid using the Gradle daemon as issues sometimes arise with the daemon using an old version
of the plugin jar.  You can stop an existing daemon with `./gradlew --stop` and avoid the daemon
on a particular build with the `--no-daemon` argument to gradlew.


### Team workflow

1.  An issue is created describing the change required.
Feature requests should be labeled 'enhancement' while bugs should be labeled 'bug'.
Issues are optional for trivial fixes like typos.
2.  An issue is assigned to the developer working on it.  If the developer is
not assignable due to GitHub.com permissions, add the label 'public-dev-assigned'.
3.  The issue assignee creates a pull request (PR) with their commits.
PRs are never optional, even for trivial fixes.
4.  The PR is assigned to one of the project committers and code review/fixes ensue.
The PR assignee's LGTM is sufficient and neccessary to merge the PR, however additional
code review from anyone is welcome.  The PR author should keep the PR in a state fit for submission
(see [preparing-your-pull-request-for-submission](#preparing-your-pull-request-for-submission))
through out this process.  The PR assignee will also need you to follow the instructions in
[Conditions for accepting pull requests](#conditions-for-accepting-pull-requests).
5.  After LGTM from the PR assignee, a project committer merges the PR into master.
If the PR author is a committer, they can merge the PR themselves as long as they
have an LGTM from the PR assignee (which should be a different committer).


### Style Guidelines
The style guidelines use the following external style guides in priority order, along with
a customizations that aren't explicit in those guides.

1. Groovy Style Guidlines (highest priority):<br>
   http://www.groovy-lang.org/style-guide.html
2. Java Style Guidelines (when not in conflict with above):<br>
   https://google-styleguide.googlecode.com/svn/trunk/javaguide.html

Further Customizations:

* Function calls should use parentheses:
```groovy
logging.captureStandardOutput(LogLevel.INFO)  // CORRECT
logging.captureStandardOutput LogLevel.INFO   // WRONG
```

* Configure calls do not use parentheses:
```groovy
project.exec {
    executable 'j2objc'              // CORRECT
    args '-sourcepath', sourcepath   // CORRECT

    executable('j2objc')             // WRONG
    args('-sourcepath', sourcepath)  // WRONG
}
```

* Explicit Types instead of 'def':
```groovy
String message = 'a message'  // CORRECT
def message = 'a message'     // WRONG

// This also applies for '->' iterators:
translateArgs.each { String arg ->  // CORRECT
translateArgs.each { arg ->         // WRONG
```

* GString curly braces only for methods or object members:
```groovy
String message = "the count is $count"           // CORRECT
String message = "the count is ${object.count}"  // CORRECT
String message = "the count is ${method()}"      // CORRECT

String message = "the count is ${count}"         // WRONG
String message = "the count is $method()"        // WRONG
String message = "the count is $object.count"    // incorrect as it only prints "$object"
```

* Single quotes for non-GString, i.e. no string interpolation:
```groovy
String message = 'the count is negative'  // CORRECT
String message = "the count is negative"  // WRONG - only needed for $var interpolation
```

* Regexes should be written and displayed as
[Slashy Strings](http://docs.groovy-lang.org/latest/html/documentation/#_slashy_string):
```groovy
String regex = /dot-star:.*, forward-slash:\/, newline:\n/
logger.debug('Regex is: ' + Utils.escapedSlashyString(regex))

// Debug log: '/dot-star:.*, forward-slash:\/, newline:\n/'
```


### Testing your code
Unit tests must be written for all new code and major changes. Please follow the examples
of existing tests.

Running `build` also runs all the unit tests:
```sh
./gradlew build
```

### Cross Platform Testing
The plugin is designed to work on Mac OS X but also has limited support on Windows and Linux.
The unit tests are designed to run across all platforms. This minimizes the chance that a
developer working on one platform can break the build on another platform.

#### Separators
All the tests should be written using forward slashes for paths and `:` as the path separator
(the standard for Linux and Mac OS X). MockProjectExec and other methods will automatically
convert `\` to `/` and ':' to ';' on **on Windows only**.

Separators are the main issue working across platforms. For the "file separator",
`java.io.File.separator` separates path components in a directory hierarchy. This is `/` on
Linux and Mac OS X and `\` on Windows. For the "path separator", `java.io.File.pathSeparator`
separates paths on the command line. This is `:` on Linux and Mac OS X and `;` on Windows.


#### Absolute Paths
Absolute path detection is usually done by checking if it `startswith('/')`. For Windows,
an absolute path has to start with the volume, e.g. `C:\`. When passing absolute paths
to `proj.file(...)`, it must be prefixed with `C:` (the only volume used in testing).
That should be done by using `TestingUtils.windowsNoFakeAbsolutePath(...)`. The following
is a simple example that tests the OS specific behaviour of `proj.file(...)`:

```groovy
@Test
public void testProjFile() {
    String absolutePath = TestingUtils.windowsNoFakeAbsolutePath('/ABSOLUTE_PATH')
    String actual = proj.file(absolutePath).absolutePath
    // proj.file on Windows will convert '/' to '\', so convert back again
    assert absolutePath == TestingUtils.windowsToForwardSlash(actual)
}
```

#### Command Lines
Windows doesn't support `echo hello` directly, instead you must run `cmd /C echo hello`.
The `demandExecAndReturn(...)` methods allow you to supply a substitute set of arguments
to replace the initial executable. For example the following will typically test for the
execution of the command: `echo hello`. On Windows however, it will replace the first
element with the second array, testing for the command `cmd /C echo hello`.

```groovy
mockProjectExec.demandExecAndReturn(
        ['echo', 'hello'],
        // windows substitute args
        ['cmd', '/C', 'echo'])
```

#### Fake OS
To get better coverage for a particular OS, without needing to run the unit-tests
on that OS, it is possible to use the `setFakeOSXXXX()` methods. This has limited
functionality and won't replace the native separators as used by java.io.File.
When this is used, the `@Before` test method should always use the reset `setFakeOSNone()`
to make the unit tests more hermetic. Within the particular test, use
`Utils.setFakeOSWindows()` or similar for Linux or Mac OS X.

In order for the test to pass, the main code under test must use `Utils.fileSeparator()`
and `Utils.pathSeparator()`. These methods will respect the setFakeOSXXXX methods
and return the separators for the faked OS. In the example below, the `testMethod_native`
test uses the native platform on which the test is run, so it will act differently across
multiple platforms. The `testMethod_windows` fakes the Windows OS. As long as the method
under test uses `Utils.fileSeparator()`, the test will run the Windows code across all
platforms.

```groovy
// File: Mine.groovy

// Method can be used on both platforms
public static void method() {
    String executableIn = 'echo'
    List<String> argsIn = ['hello']
    // This conditional check if the fake OS has been set
    if (Utils.isWindows()) {
        executableIn = 'cmd'
        argsIn = ['/C', 'echo', 'hello']
    }
    Utils.projectExec(project, null, null, null, {
                executable executableIn
                args argsIn
                setStandardOutput null
                setErrorOutput null
            })
}


// File: MineTest.groovy

// Default to native OS except for most tests
@Before
void setUp() {
    Utils.setFakeOSNone()
}

// Uses native platform (most tests should be like this)
@Test
public void testMethod_native() {
    mockProjectExec.demandExecAndReturn(
            // Linux / Mac OS X uses only these arguments
            ['echo', 'hello'],
            // Windows will substitute these arguments
            ['cmd', '/C', 'echo'])
    Mine.method()
}

// Forces Windows platform test
@Test
public void testMethod_windows() {
    Utils.setFakeOSWindows()
    mockProjectExec.demandExecAndReturn(
            // Making the test invalid on Linux / Mac OS X ensures that setFakeOSWindows works
            ['INVALID-MUST-BE-SUBSTITUTED', 'hello'],
            // All platforms will substitute these arguments due to the fake OS
            ['cmd', '/C', 'echo'])
    Mine.method()
}
```

### Preparing your pull request for submission
Say you have a pull request ready for submission into the main repository - it has
been code reviewed and tested.
It's convenient for project committers if you:

1.  Condense branch history to 1 or a few commits that describe what you did.  This
eliminates internal code review fixes from being separate in history,
like refactors, typos, etc.
2.  Resolve merge conflicts with master.  As the author of the PR, you're in the
best position to resolve these correctly.  Even if the merge is a fast-forward,
doing the merge yourself allows you to test your code after incorporating others'
changes.

If you are new to github, here's an example workflow.
We'll assume you are working from your local repository
which is a clone of a fork of j2objccontrib/j2objc-gradle, that your feature
branch is called 'patch-1' and that your pull request is number 46.


### Preparation
```sh
# have a clean working directory and index
# from the tip of your patch-1 branch: first save away your work
git branch backup46
```

Don't checkout or touch branch backup46 from here on out.  If things
go terribly wrong below, you can return to a state of LKG sanity with:

1.  `git rebase --abort`
2.  `git merge --abort`
3.  `git checkout patch-1`
4.  `git reset --hard backup46`

This will return your patch-1 branch to the state before merging & rebasing

First ensure you've setup your upstream remote as per 
https://help.github.com/articles/configuring-a-remote-for-a-fork/,
then do https://help.github.com/articles/syncing-a-fork/.
Your local master should now be equal to upstream/master.
Push your local master to your origin remote:
```sh
# While in your local master branch
git push
```


### Rebasing and merging
Now you can work on merging in master to your branch.  We'll assume a simple
branch history here, where patch-1 diverged from master and has never been
merged back in yet.  If you are unfamiliar
with rebasing and merging, first read:
https://robots.thoughtbot.com/git-interactive-rebase-squash-amend-rewriting-history 

The following steps will:

1. Update your repository with changes upstream.
2. Allow you to merge those change in to yours.
3. Allow you to squash all your commits into a single well-described commit.

```sh
git checkout patch-1
# condense this down to one commit to preserve proper project history
git rebase -i master
# within the rebase editor, replace the word 'pick' with 'fixup' on
# every line except the very first one.  On the first line, replace
# 'pick' with 'reword'.
# When you exit that editor, you should be given a chance to give
# your entire PR a single detailed commit message.
# resolve and finish the merge as usual.
# The -f forces a push, since you will have rewritten part of your branch's
# history.
git push -f
```

For guidance on doing the merge itself, see
https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging


### Publishing and versioning

These instructions are only for plugin maintainers; if you are just working
on issues or pull requests, you can ignore this section.

We'd like to maintain proper versioning history for the plugin.  This requires
a little bit of coordination between in-code versions, GitHub tags, and
published versions on plugins.gradle.org.  All branches and tags are on the
`j2objc-contrib/j2objc-gradle` repository, not any forks.  The steps are:

1.  Determine the version number.  Use https://semver.org to guide which
slot in the version number should be bumped.  We'll call this `vX.Y.Z`.  Note that
this does not have to be the `-SNAPSHOT` version in build.gradle - that number
was auto-incremented after the last release and may not reflect the extent
of API changes which, per semantic versioning, whether to increment major, minor,
or patch numbers.
2.  As a separate commit, update the version number in `build.gradle`, removing the
`-SNAPSHOT` suffix if any, and add a brief section at the top of
[CHANGELOG.md](CHANGELOG.md) indicating key functionality and quality improvements.
File a PR, review and merge as normal.
3. Get the commit SHA after the PR is merged from the
[commit history](https://github.com/j2objc-contrib/j2objc-gradle/commits/master)
(`cfdc1aa` used in example below).
4. Verify that the specific commit SHA build Successful on all platforms. You may need
to look in to the build history to confirm the specific SHA.
5. Tag the merge commit where that PR is merged into master as `vX.Y.Z` and push
that tag to the repository. Since you are working directly off master, you must manually
verify that no additional commits/PRs have been merged that you don't want in the release.
```sh
git tag -a v0.4.1-alpha cfdc1aa
git push upstream v0.4.1-alpha
```
6. Do a clean build and then publish the new version to https://plugins.gradle.org<br>
```sh
./gradlew clean build publishPlugins
```
7. Push a new PR that increments build.gradle to `vX.Y.(Z+1)-SNAPSHOT`.  `-SNAPSHOT`
is standard convention for marking an unofficial build (if users happen to get their
hands on one built directly from source).
8. Update j2objc-common-libs-e2e-test to use the latest plugin version,
[see instructions](https://github.com/j2objc-contrib/j2objc-common-libs-e2e-test/blob/master/CONTRIBUTING.md#updating-j2objc-gradle).
The commit SHA should match the SHA shown in the PR.


### Hotfixes

Currently the development is manageable, such that release branching can be isolated
to hot fixes. When that is necessary, do the following:

1.  Create a branch `release_vX.Y.Z` on the `master` repo from the last commit for the
published version (this was the sha used previously in step 3 from the last section).
2.  Merge in hotfix PRs to the release branch.
3.  Merge the release branch back into master.
4.  Delete the release branch from the repository.
5.  If further hotfixes are needed, start at step 1, using the last commit from step 2.

### Issue tracking
Labels are assigned as follows:

label | description
----- | -----------
**type** | Type of issue (use one)
`type:bug` | Defect in the codebase
`type:enhancement` | Feature request
`type:push-release` | Request/documentation for a release
`type:question` | Question from a user
**status** | Status of the issue
(none) | Issue has not yet been triaged by a committer
(assigned to a committer) | A committer is working on this
`status:duplicate` | Closed as duplicate of another issue (please link in closing comment)
`status:help-wanted` | Requesting PRs from the public
`status:icebox` | Nice to have, but indefinitely on hold
`status:no-repro` | Closed because we could not reproduce the error
`status:obsolete` | Closed because the issue no longer occurs
`status:public-dev-assigned` | Expecting a PR from a particular member of the public (GitHub prevents us from assigning the issue explicitly)
`status:wontfix` | Closed because we've chosen not to fix this
`status:works-as-intended` | Closed because the described behavior is intended
**categories** | Which component(s) of the project are affected
`cat:convenience-magic` | Increasing convenience for plugin users by ex. automatic configuration
`cat:dependencies` | Handling of dependencies among projects, libraries, etc.
`cat:docs` | Improving project documentation
`cat:testing` | Improving project test coverage
`cat:native-build` | Building the translated code into libraries
`cat:perf` | Improving performance
`cat:translation` | The core `j2objc` execution phase, converting Java to Objective-C
`cat:xcode` | Integration with Xcode
