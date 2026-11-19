---
name: safety-review
description: Do a dedicated, adversarial second-pass review of a diff or change touching insulin dosing, safety constraints, algorithms (OpenAPS/AutoISF/determine-basal), pump communication, or CGM/glucose processing in AndroidAPS - looking specifically for bypassed safety checks, silently widened bounds, weakened fail-fast behavior, and unit/off-by-one errors. Always run this after writing or modifying code in those areas, even without being asked, before considering the change ready to hand off. Also trigger when the user asks to "review this for safety", "sanity check this change", "is this safe to build", or similar.
---

# Safety Review

The risk with self-review: the same context that wrote the code is the one reviewing it,
and it's easy to re-confirm your own reasoning instead of actually hunting for what's wrong.

This skill exists to force a deliberately different mode of reading than the one used while
writing the code - adversarial, not confirmatory.

## When to run this

Run it automatically, without being asked, any time you've just written or modified code in:

- Insulin dosing and delivery
- Safety constraints (`plugins/constraints/**`)
- Algorithms - OpenAPS/AutoISF/determine-basal logic (`plugins/aps/**`, `plugins/insulin/**`, `plugins/sensitivity/**`)
- Pump communication (`pump/**`)
- CGM/glucose processing
- Treatment handling
- Any other safety check

This mirrors the same category list `upstream-diff-check` uses - if a change was flagged as
safety-relevant there, it needs this review after the code is written.

## How to review

Get the real diff first (`git diff master...HEAD`). Set aside the reasoning you used while
writing the change. Re-read the diff as if someone else wrote it and you're trying to find
what they got wrong, not confirm what they got right. Go through this checklist against the
actual diff, not a summary of it:

1. **Bypassed or weakened safety mechanisms.** Was any existing constraint check removed,
   disabled, or made conditional/optional where it wasn't before? Does every code path that
   used to go through a safety check still go through it?

2. **Widened bounds.** Did the change increase a max dose, expand a time window, or relax a
   limit? If so, is that widening the actual, deliberate point of the requested change - or
   a side effect of how it was implemented?

3. **Weakened fail-fast behavior.** Does the change silently swallow an exception, fall back
   to a default value, or continue in an ambiguous state where the prior code would have
   raised an error or reported an invalid precondition? Per the project's guidelines, invalid
   states should be reported clearly, not silently recovered from.

4. **New alternate paths.** Does the change introduce a second way to reach an outcome
   (e.g. a new code path to trigger a bolus/basal change) that doesn't route through the
   same safety checks as the existing path? Two paths to the same effect is itself a risk -
   check whether one was actually necessary.

5. **Unit and off-by-one errors.** In safety-relevant numeric code, check units explicitly:
   mg/dL vs. mmol/L, minutes vs. seconds, U vs. mU, insulin duration vs. DIA. A one-decimal
   or unit-scale error here is a dosing error, not a cosmetic bug.

6. **Pump/CGM error handling.** If the change touches how pump communication or CGM data
   errors are handled, confirm errors still surface (retry logic, alerts, or fail-safe
   behavior) rather than being treated as equivalent to a successful read.

## Reporting

For each finding, cite the specific function or line and explain concretely why it's a
concern - not just "this looks risky." Suggest a fix or, if it's a judgment call the user
should make, phrase it as a question rather than silently picking an answer.

If the checklist turns up nothing, say so plainly and move on - don't manufacture a finding
to look thorough. A clean review with a real checklist behind it is a genuinely useful
signal; a fabricated one erodes trust in the next one.

**This review is not a substitute for actually building and testing the change.** Per the
project's workflow, that step happens on the user's machine - say so if there's any risk the
user might read a clean review as "verified."

## Example

```
[Just wrote a change to plugins/aps/openAPSAMA/DetermineBasalAMA.kt widening the
SMB cap under a new condition]

Reviewing plugins/aps/openAPSAMA/DetermineBasalAMA.kt against the checklist:

- Widened bounds: yes - the new `highBgOverride` branch allows maxSMB up to 1.5x the
  configured cap when BG > 250 mg/dL. That's the intended behavior per the request, but
  it bypasses the existing `constraintChecker.applyMaxIOBConstraints()` call that every
  other SMB path goes through. That looks unintentional - the new branch should route
  through the same constraint check before returning, not skip it because it's an
  "override" path.
- Fail-fast: no change to error handling in this diff - clean.
- Units: cap is compared in the same mg/dL scale as elsewhere - clean.

One finding, one question: should `highBgOverride` really bypass
`applyMaxIOBConstraints()`, or was that accidental? I'd fix it to still call the
constraint check unless there's a specific reason not to.
```
