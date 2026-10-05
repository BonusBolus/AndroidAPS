# AI_agents/AGENTS.md - BonusBolus/AndroidAPS

Shared instructions for all AI coding agents (Codex, Claude Code, Gemini/Antigravity).
Fork of `nightscout/AndroidAPS` (`master`). Target pump: Medtrum Nano 200U (`pump/medtrum`).

## AndroidAPS Development
 
### Goal
 
Develop custom functionality for AndroidAPS while keeping the code **safe, clean, simple, and maintainable**.
 
Upstream repository:
 
**https://github.com/nightscout/AndroidAPS**
 
Use the upstream `master` branch as the reference implementation.
 
### Core Principles
 
* **Safety above all else.** Never bypass or weaken existing safety mechanisms.
* **Simple beats complex.** Use the simplest solution that correctly solves the problem.
* **One correct path.** Avoid unnecessary alternatives, fallbacks, abstractions, and duplicate mechanisms.
* **Surgical changes only.** Make the smallest change necessary; don't refactor unrelated code.
* **Fix root causes.** Diagnose the underlying problem rather than patching symptoms.
* **Separation of concerns.** Keep functions and components focused on a single responsibility.
* **Fail fast.** Clearly report invalid states or unmet preconditions rather than silently recovering.
* **Clarity over compatibility.** Don't maintain obsolete behavior without a concrete requirement.
* **Evidence-based debugging.** Use targeted investigation and minimal logging rather than guessing.
* **Preserve existing structure.** Follow AndroidAPS conventions and architecture unless there is a good reason not to.
 
### Upstream Awareness
 
This repository is a fork of:
 
**https://github.com/nightscout/AndroidAPS**
 
Before making significant changes:
 
1. Check whether the local fork is behind upstream `master`.
2. Check whether upstream has changed the code relevant to the proposed change.
3. **Warn the user before modifying an area that has relevant upstream changes.**
4. Explain the relevant upstream differences and potential conflicts.
5. Do not automatically merge, rebase, reset, or overwrite local changes.
 
Pay particular attention to upstream changes involving:
 
* Insulin dosing and delivery
* Safety constraints
* Algorithms
* Pump communication
* CGM/glucose processing
* Treatment handling
* Safety checks
 
Being behind upstream alone is **not** a reason to warn. Warn when upstream changes are relevant to the area being modified.
 
### Integrating External Code
 
When using code written by others:
 
* Understand the code and its assumptions before integrating it.
* Check whether AndroidAPS or upstream already provides similar functionality.
* Adapt it to the existing architecture rather than blindly copying it.
* Preserve AndroidAPS safety mechanisms.
* Keep the resulting implementation minimal and understandable.
* Clearly identify externally sourced code where appropriate.
 
### Development Workflow
 
Before implementing a change:
 
1. Understand the requested behavior.
2. Inspect the relevant existing code.
3. Check for relevant upstream changes.
4. Identify the simplest safe solution.
5. Make the smallest necessary change.
6. Test and review the resulting diff.
 
Do not expand the scope unless necessary for correctness or safety.
 
**When safety, functionality, convenience, or simplicity conflict, safety always wins.**


## Agent Behavior

- Never invent files, APIs, functions, results, or sources. Inspect the code before making claims about it.
- You review code; the user builds, deploys, and tests. Never call a change "tested" or "verified" unless it was actually built and run. A code review is not a build or test; say what was and was not checked.
- Ask only when a missing detail materially changes the solution; otherwise state a brief assumption and proceed. Ask before destructive, irreversible, or external actions.
- Be concise. Answer first. No filler.

## Git Workflow - Branch Per Feature

The user does all building, testing, merging, and pushing.

- Every feature gets its own branch: `feature/<short-name>` (e.g. `feature/smb-max-minutes-automation`). **Never work directly on `master`.** If no branch name has been established, ask; do not invent one.
- Allowed without asking: `git status`, `diff`, `log`, `fetch`; creating/switching to a `feature/*` branch; focused commits on the current `feature/*` branch (`WIP:` prefix for unfinished work).
- **Never do** (the user does these): `git push`; merging into `master` (or `master` into a feature branch without asking); `rebase`, `reset --hard`, `push --force`, `clean -fd`, discarding uncommitted changes; merging/rebasing/resetting onto upstream; deleting branches. If a task seems to require one, stop and ask.
- Pause: commit as `WIP: <state>` and switch. Abandon: the user deletes the branch; `master` stays untouched.

## Safety-Critical Areas

Paths: `plugins/aps/**`, `plugins/constraints/**`, `plugins/insulin/**`, `plugins/sensitivity/**`, `plugins/source/**` (CGM), `pump/**`, plus treatment handling and automation actions that change SMB/dosing parameters (e.g. `ActionSMBChange.kt`).

- Before editing: run the `upstream-diff-check` skill for the exact files. If upstream changed them, **stop and warn the user before writing code.** Read the existing implementation and tests; never rely on Graphify alone.
- While editing: do not remove, disable, or make optional any constraint check; every path to a bolus/basal/SMB outcome must go through the same checks (no second path); do not widen a max dose, time window, or limit unless that is the explicit point of the request; do not swallow exceptions or substitute defaults where the old code raised; pump/CGM errors must still surface; state units on every safety-relevant numeric (mg/dL vs mmol/L, minutes vs seconds, U vs mU, DIA vs insulin duration).
- After editing: run the `safety-review` skill before declaring the change ready. Mandatory, even if not asked.

## Skills (procedures)

Skills live in `AI_agents/skills/<name>/SKILL.md`. Claude Code and Antigravity discover them through small stubs in `.claude/skills/` and `.agent/skills/` that point here. If your tool does not load skills automatically (e.g. Codex), open the `SKILL.md` and follow it.

- `upstream-diff-check`: before non-trivial changes, porting upstream PRs, and always before safety-critical areas.
- `safety-review`: after writing or modifying safety-critical code.
- `feature-handoff`: only when the user says a feature is ready, paused, or asks to package it.

## Reference Material

- `AI_agents/agents_graphify/` (`GRAPH_REPORT.md`, `graph.json`, `.graphify_analysis.json`): Graphify analysis, a snapshot for orientation only. Always verify against the actual source; for safety-critical code never rely on it alone. If it disagrees with the source, trust the source and tell the user it is stale and should be regenerated.
- `AI_agents/AndroidAPS_Feature_Backlog.md`: planned custom features (SMB max-minutes actions, BG notification icon, Garmin, AutoISF, FPU bolus). Read it when asked about the backlog or porting an upstream PR; re-verify against current upstream before starting an item.
