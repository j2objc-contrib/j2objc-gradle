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


### Local Development

For plugin contributors, you should build the plugin from this repository's root:

    $ ./gradlew build

This will create a .jar containing the plugin at projectDir/build/libs/j2objc-gradle-X.Y.Z-alpha.jar

In order to test your modification to the plugin using your own project, you need to modify the
project that uses the plugin:

1. Comment out the `plugins {...}` section
1. Add the `buildscript` and `apply plugin` lines then update X.Y.Z based on "version = 'X.Y.Z-alpha'"
in j2objc-gradle/build.gradle

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
through out this process.
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
```
logging.captureStandardOutput(LogLevel.INFO)  // CORRECT
logging.captureStandardOutput LogLevel.INFO   // WRONG
```
* Configure calls do not use parentheses:
```
project.exec {
    executable 'j2objc'              // CORRECT
    args '-sourcepath', sourcepath   // CORRECT

    executable('j2objc')             // WRONG
    args('-sourcepath', sourcepath)  // WRONG
}
```
* Explicit Types instead of 'def':
```
String message = 'a message'  // CORRECT
def message = 'a message'     // WRONG

// This also applies for '->' iterators:
translateArgs.each { String arg ->  // CORRECT
translateArgs.each { arg ->         // WRONG
```
* GString curly braces only for methods or object members:
```
String message = "the count is $count"           // CORRECT
String message = "the count is ${object.count}"  // CORRECT
String message = "the count is ${method()}"      // CORRECT

String message = "the count is ${count}"         // WRONG
String message = "the count is $method()"        // WRONG
String message = "the count is $object.count"    // incorrect as it only prints "$object"
```
* Single quotes for non-GString, i.e. no string interpolation:
```
String message = 'the count is negative'  // CORRECT
String message = "the count is negative"  // WRONG - only needed for $var interpolation
```


### Testing your code
Unit tests must be written for all new code and major changes. Please follow the example
of existing tests within the code.

To run the build and all the tests:
```
./gradlew build
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
```
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
```
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

```
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
slot in the version number should be bumped.  We'll call this `vX.Y.Z`.
2.  From `master`, create a branch `release_vX.Y.Z` and check it out.
3.  Optional: If any critical bugfixes are deemed neccessary, merge those
commits into this release branch.
4.  As a separate commit, bump the version number in `build.gradle`. 
File a PR, and merge that PR into the release branch.
5.  Tag the merge commit where that PR is merged into `release_vX.Y.Z` as `vX.Y.Z` and push that
tag to the repository.  It is important that the commit where the
merge occurred into the release branch is tagged - not the commit that bumped the version number, but
also not the commit that merges the release branch into master (see below).
6.  Do a clean build and then publish the new version to https://plugins.gradle.org<br>
`./gradlew clean build publishPlugins`
7.  Merge the release branch back into master.
8.  Delete the release branch from the repository.  If for some reason a hotfix
is later needed, start at step 1, but instead of creating a new branch from the tip
of `master`, instead create the release branch from the commit tagged in step 5.  Do
not reuse the old release branch.
