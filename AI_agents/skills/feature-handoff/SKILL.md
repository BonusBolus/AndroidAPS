---
name: feature-handoff
description: Package a finished (or paused) AndroidAPS feature-branch change for handoff, since the user does all building, testing, merging, and pushing themselves and the agent never pushes. Use this when a feature is ready to hand off, when explicitly asked to "package this up", "give me the patch", "prep this for testing", or when pausing mid-feature to switch tasks. Produces a committed feature branch, a summary, and the exact git commands to run locally. Never invoke this automatically mid-task, only when the user is ready to receive the change.
---

# Feature Branch Handoff

The workflow for this repo: the agent edits code in the working repo on a feature branch;
the user builds, tests, merges, and pushes. The agent never pushes or merges. This skill
makes the handoff consistent so the user doesn't have to re-derive the commands each time.

## Before handing off

Confirm the feature branch name. It must follow the `feature/<short-name>` convention,
e.g. `feature/smb-max-minutes-automation`. Check with `git branch --show-current`.
If on `master`, or no branch name was established, stop and ask what to call it.

If the change touches a safety-critical area and `safety-review` has not run on the final
diff, run it first.

## Step 1: Review the final diff

```bash
git status
git diff master...HEAD --stat
```

Confirm no unrelated edits, debug logging, or stray files are included.

## Step 2: Commit on the feature branch

If work is uncommitted:

```bash
git add -A
git commit -m "<what the feature does>"
```

For a paused (incomplete) feature, use `git commit -m "WIP: <short description of state>"`.

## Step 3: Optional patch file

Only if the user asks for a patch, generate it named after the branch (not `changes.patch`):

```bash
git diff master...feature/<branch-name> > <branch-name>.patch
```

## Step 4: Summarize and give the exact local commands

Summarize briefly: what changed and why, files touched, safety review result (or "not
applicable"), and what was and was not checked.

Then give the commands the user runs, with the real branch name filled in:

- Test: build and deploy from Android Studio on `feature/<branch-name>`.
- Once satisfied:

```bash
git checkout master
git merge feature/<branch-name>
git push origin master
```

(Or push the feature branch and open a PR against the fork's `master`, if they prefer to
review the diff on GitHub first.)

## Handling a pause instead of a finish

There is no "merge back" step yet. Commit the WIP state and tell the user they can resume
with `git checkout feature/<branch-name>`. Don't fabricate a merge or push step for
unfinished work.

## What not to do

- Don't invent a branch name if one wasn't established - ask.
- Don't push, merge, rebase, or reset.
- Don't tell the user the feature is "tested" or "verified" - only a build and deploy on
  their machine can confirm that, per the project's workflow. Say what was actually
  reviewed (code review, not a build or test).
