---
trigger: glob
globs: plugins/aps/**, plugins/constraints/**, plugins/insulin/**, plugins/sensitivity/**, plugins/source/**, pump/**
description: Extra scrutiny for insulin dosing, algorithms, constraints, pump comms, CGM processing
---

# Safety-Critical Areas

These paths cover insulin dosing and delivery, safety constraints, algorithms (OpenAPS / AutoISF / determine-basal), pump communication, CGM/glucose processing, treatment handling, and safety checks. Automation actions that change SMB/dosing parameters (e.g. `ActionSMBChange.kt`) are safety-relevant too.

## Before editing

1. Use the `upstream-diff-check` skill for the exact files you will touch. If upstream changed them, **stop and warn the user before writing code.**
2. Read the existing implementation and its tests. Do not rely on Graphify alone.
3. State in one or two sentences what the change does to dosing behavior.

## While editing

- Do not remove, disable, or make optional any constraint check.
- Every path to a bolus/basal/SMB outcome must go through the same constraint checks. Do not add a second path around them.
- Do not widen a max dose, time window, or limit unless that is the explicit point of the request.
- Do not swallow exceptions or substitute defaults where the old code raised an error.
- Pump/CGM errors must still surface, never be treated as a successful read.
- State units on every safety-relevant numeric: mg/dL vs mmol/L, minutes vs seconds, U vs mU, DIA vs insulin duration.

## After editing

Use the `safety-review` skill before declaring the change ready. Mandatory, even if not asked.
