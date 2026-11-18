## UI

- Overview: show the profile glucose target and the current sensitivity-adjusted target, with the active target highlighted. Use a direction arrow for the adjusted target.
- Overview: show the effective algorithm ISF and Wizard ISF in the sensitivity section. Mark Wizard ISF with the bolus wizard icon.
- Overview: show SMB on/off status and the configured SMB maximum basal minutes beside the loop indicator.## UI

- Overview graphs: add a selectable Time in Range bar with Very Low, Low, In Range, High, and Very High ranges.
- TIR graphs: support the current day from midnight or a rolling 24-hour view.
- TIR graphs: keep TIR exclusive from other graph series and make it available in the history browser.
- TIR graphs: move the Today and 24h choice into the graph edit screen.
## UI

- Bolus wizard: add the live BG, IOB, and COB overview bar used by the carb-entry screen.
- Bolus wizard: restore colors for the BG, temp target, trend, IOB, and COB buttons.
- Bolus wizard: order the extra settings as Use percentage, Carb time, BG, Correction, and Profile.## UI

- Overview graphs: allow more than two data fields on one graph.
- Overview graphs: use the first field on the left axis and later fields on the right axis.
- Overview graphs: show `%sens` and VSENS with different colors.
- Overview graphs: remove the 100 dp minimum and 250 dp maximum height limits.
- Overview screen: hide the top controls until the content is scrolled back to the top; keep bottom controls visible.
- Overview screen: hide the top controls after 3 seconds of inactivity and keep them hidden when returning from another screen.
- Overview screen: show the top search controls when pulling down while already at the top.
- Overview screen: show and hide the top search controls with a slower, smoother slide-and-fade animation.
- Overview graphs: allow data series selection on the fixed IOB/basal graph.
- Overview screen: center the disabled SMB cross on the SMB icon.
- Quick Launch: remove the count badges from the Treatments and Scenes bottom-bar buttons.
- Quick Launch: make the settings action optional and configurable instead of always adding it.
feature/profile-percent-permanent

Added
- "Start profile N% for M min" automation action: a duration of 0 now makes it a permanent switch of
  the current profile at N% (e.g. 100% / 0 min = back to 100% with no end time). Shown as "Start profile N%".
  The profile is whichever one is currently active, so no profile name is needed.

Changed
- Duration range in the action editor is now 0-1440 min (was 5-1440). Validation accepts 0.
- The "profile must be at 100%" precondition only applies to temporary switches. A permanent switch replaces
  whatever percentage is running.
- User entry / automation log note for this action uses the action description, so a permanent switch is
  not logged as "for 0 min". Temporary switches are logged as before.

Notes
- Same pump, hard-limit and insulin checks as before. Only the switch duration differs.
- Does not skip when the profile is already at that percentage, so use a one-shot trigger, not a condition that can re-fire.

Tests
- Added: permanent switch (valid, description, createProfileSwitch with duration 0), precondition only for temporary switches.feature/smb-max-minute-automation-toggle

Added
- Automation action "Change SMB max minutes": sets "Max minutes of basal to limit SMB" (15-120 min).
- Automation action "Change UAM SMB max minutes": sets "UAM max minutes of basal to limit SMB" (15-120 min).
  Both are ported unchanged from upstream nightscout/AndroidAPS PR #5076 (author: olorinmaia).
  Combine with a recurring time trigger and the SMB action to lower or stop SMB during chosen hours.
- "Toggle" option for the "Enable/disable SMB" automation action. It inverts the current SMB setting
  (on -> off, off -> on) instead of setting a fixed value.
  New On / Off / Toggle selector in the action editor, new string "Toggle SMB".

Changed
- Enable/disable SMB action JSON now stores a "toggle" field. Existing automations without it keep
  their behavior (toggle = false).

Notes
- Toggle is not idempotent. Use it with one-shot triggers (time of day), not level conditions that can re-fire.
- Max-minutes values are clamped to the same limits as the manual preference.
- No change to the SMB algorithm or safety constraints.

Tests
- Added: ActionSMBMaxMinutesChangeTest, ActionUAMSMBMaxMinutesChangeTest (from PR #5076).
- Extended: ActionSMBChangeTest (toggle on/off, description, JSON).## 2026-11-16 — Scene automation (branch: feature/scene-automation)
Base: upstream master f77abd9 (v4.0.0 merge). Not an upstream PR.

Changes
- Run Scene action: per-action guard with three modes: do not run if any scene is
  running (default, unchanged behaviour), replace another scene but skip if the chosen
  scene is already running, always run and replace any running scene.
- Scene trigger: optional scene picker ("Any scene" by default). Now selectable in the
  trigger list (category Other); before it was only a hidden precondition.
- New Stop scene action with an option to run the follow-up (chain) scene or not.
- SceneAutomationApi: new activeSceneId().
- Tests: ActionRunSceneTest, TriggerSceneActiveTest, SceneAutomationApiImplTest updated;
  ActionStopSceneTest added.

Safety notes
- Default guard unchanged. A missing or unknown guard value falls back to the default.
- Replace modes can end a scene that includes a loop-mode change (its revert runs).
- "Always run" restarts the scene every 5 minutes while the rule's conditions hold.
- Stop scene restores SMB to the value it had when the scene started.
- A scene-based trigger on a deleted scene never matches; "not exists" is then always true.
- On a build without this change: guard modes fall back to the default, scene triggers
  widen to "any scene", Stop scene actions are dropped from their rules.

Upstream compatibility
- Conflict-prone files: ActionFactory, ChooseActionSheet, ActionEditors, TriggerEditors,
  AutomationRuntime, SceneAutomationApi.

Verification: unit tests [result], emulator [result].
Revert: git revert -m 1 <merge commit>. Check rules that use scene triggers afterwards.- Quick Launch: draw the divider before Scenes across the full menu width.
