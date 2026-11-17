---
name: upstream-diff-check
description: Check the BonusBolus/AndroidAPS fork against upstream nightscout/AndroidAPS master before editing a given area of the codebase - reports how far behind the fork is and whether upstream has touched the same files recently. Always run this before starting a non-trivial change, and especially before touching insulin dosing, safety constraints, algorithms (OpenAPS/AutoISF/determine-basal), pump communication, or CGM/glucose processing, since unnoticed upstream changes there are the highest-risk kind to miss. Trigger this whenever the user asks to implement, port, or modify a feature in AndroidAPS, asks "is this area safe to touch", or asks to check upstream status.
---

# Upstream Diff Check

AndroidAPS development guidelines require checking upstream before making significant
changes, and warning the user if upstream has touched a safety-relevant area you're about
to edit. This skill makes that check concrete and repeatable instead of relying on memory.

## Why this matters

The fork (`BonusBolus/AndroidAPS`) is a moving target relative to upstream
(`nightscout/AndroidAPS`, `master`). Two failure modes this skill prevents:

1. **Silent divergence** - you build a feature against fork code that upstream has since
   changed in a way that conflicts, wasting the work.
2. **Missed safety-relevant upstream fixes** - upstream ships a dosing/algorithm/safety fix
   in exactly the file you're about to modify, and you don't know because you never checked.

Being behind upstream by itself is not a problem worth flagging every time - churn in
unrelated modules (UI, translations, unrelated pump drivers) doesn't need a warning. What
matters is whether upstream changed the *specific area* you're about to touch.

## Steps

### 1. Identify the target area

Work out which files or directories the upcoming change will touch. If this isn't obvious
from the user's request, ask - don't guess broadly, since an overly wide target path makes
step 4 noisy and unhelpful.

### 2. Make sure upstream is reachable locally

If the fork's local clone doesn't already have upstream as a remote:

```bash
git remote add upstream https://github.com/nightscout/AndroidAPS.git 2>/dev/null
git fetch upstream master
```

If it's already there, just fetch:

```bash
git fetch upstream master
```

### 3. Check how far behind the fork is (overall context, not the main signal)

```bash
git log HEAD..upstream/master --oneline | wc -l
```

Report this number for context, but don't treat it alone as a reason to warn.

### 4. Check whether upstream touched the target area

```bash
git log HEAD..upstream/master --oneline -- <target-path-1> <target-path-2> ...
```

If this returns commits, upstream has changed something in the exact area you're about to
edit. List the commit subjects - they usually tell you enough to judge relevance (a typo fix
in a comment is very different from a change to a dosing calculation).

### 5. Classify safety relevance

Regardless of whether step 4 found anything, check if the target area falls into one of the
categories the project guidelines call out for extra scrutiny:

- Insulin dosing and delivery
- Safety constraints
- Algorithms (`plugins/aps`, `plugins/insulin`, `plugins/sensitivity`, determine-basal logic)
- Pump communication (`pump/**`)
- CGM/glucose processing
- Treatment handling
- Safety checks / constraints (`plugins/constraints`)

### 6. Report and decide

Combine steps 3-5 into a short report:

- Commits behind upstream master (overall count, for context).
- Relevant upstream commits touching the target area, if any, with subject lines.
- Whether the target area is safety-relevant per the categories above.

**If upstream has relevant changes in the target area, or the area is safety-relevant and
you haven't reviewed upstream's current state of it recently: stop and tell the user before
writing code.** Explain what upstream changed and let them decide whether to incorporate it,
work around it, or proceed as planned. Don't silently merge, rebase, reset, or overwrite
local changes - that decision belongs to the user, not to this check.

If nothing relevant turned up, say so briefly and proceed with the original task - this
check should be quick when the answer is "all clear," not a ceremony.

## Example

```
User: Let's port the SMB max-minutes automation actions from PR #5076 into
      plugins/automation/.../actions/.

[fetch upstream, diff plugins/automation/src/main/kotlin/.../actions/]

Fork is 340 commits behind upstream/master overall.
No upstream commits touch plugins/automation/.../actions/ in that range - this area
hasn't moved since the fork point. Not classified as safety-relevant (automation
actions, not the dosing algorithm itself), though the action changes SMB timing
constraints, so worth a quick sanity check on ActionSMBChange.kt's current logic.
Clear to proceed.
```
