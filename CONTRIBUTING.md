## Quick start

1. Fork it clicking on the top right "Fork" button
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request (once your PR has been reviewed and is ready to submit see 'Preparing your pull request for submission' below).

## Style guide
TODO

## Testing your code
TODO

## Preparing your pull request for submission
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
which is a clone of a fork of brunobowden/j2objc-gradle, that your feature
branch is called 'patch-1' and that your pull request is number 46.

### Preperation
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
http://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging
