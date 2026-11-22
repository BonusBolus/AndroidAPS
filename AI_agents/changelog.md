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
- Overview graphs: the TIR merge brought the 2-field limit back when picking fields; removed again (TIR still stays alone on its graph).
- Overview graphs: use the first field on the left axis and later fields on the right axis. Fields with the same
  unit as the first (IOB with absolute IOB, BGI with deviations) share the left axis, as on master.
- Overview graphs: zoom the graphs below the glucose graph with a two-finger pinch, the same as on the glucose graph itself.
- Overview graphs: show `%sens` and VSENS with different colors. `%sens` uses the new autosens colour (rose)
  and VSENS the ISF chip colour, the same as the overview chips.
- Overview graphs: remove the 100 dp minimum and 250 dp maximum height limits.
- Overview screen: hide the top controls until the content is scrolled back to the top; keep bottom controls visible.
- Overview screen: hide the top controls after 3 seconds of inactivity and keep them hidden when returning from another screen.
- Overview screen: show the top search controls when pulling down while already at the top.
- Overview screen: show and hide the top search controls with a slower, smoother slide-and-fade animation.
- Overview screen: move the content with the search bar's slide instead of snapping it into place when the animation starts.
- Overview screen: center the disabled SMB cross on the SMB icon.
- Quick Launch: remove the count badges from the Treatments and Scenes bottom-bar buttons.
- Quick Launch: make the settings action optional and configurable instead of always adding it.
feature/profile-percent-permanent

Added
- "Start profile N%" automation action: a "Permanent (no end time)" switch in the editor makes it a permanent
  switch of the current profile at N% (e.g. 100% permanent = back to 100% with no end time). Shown as
  "Start profile N%". The profile is whichever one is currently active, so no profile name is needed.
  The duration field is hidden while Permanent is on.

Changed
- Duration range in the action editor is now 1-1440 min, step 1 (was 5-1440, step 10). Validation needs
  Permanent or a duration of at least 1 min.
- A missing or broken duration in the rule JSON makes the action invalid. It no longer becomes permanent.
- Preconditions:
  - temporary switch: the profile must be at 100% (as before);
  - permanent switch: the running percentage must be different from N%. So 90% -> 100% runs, but
    100% -> 100% does not, and a trigger that stays true does not add the same switch every loop run.
- User entry / automation log note for this action uses the action description, so a permanent switch is
  not logged as "for 0 min". Temporary switches are logged as before.

Notes
- Same pump, hard-limit and insulin checks as before. Only the switch duration differs.
- A permanent switch keeps the timeshift of the running profile (for example after travel).
  A temporary switch uses timeshift 0 like upstream: when it ends, the old profile with its timeshift comes back.

Tests
- Permanent switch (valid, description, createProfileSwitch with duration 0), precondition for temporary and
  permanent switches, minimum duration of 1 min, missing duration is invalid, JSON with the permanent flag, timeshift
  kept for permanent and 0 for temporary switches.

feature/smb-max-minute-automation-toggle

Added
- Automation action "Change SMB max minutes": sets "Max minutes of basal to limit SMB" (15-120 min).
- Automation action "Change UAM SMB max minutes": sets "UAM max minutes of basal to limit SMB" (15-120 min).
  Both are ported unchanged from upstream nightscout/AndroidAPS PR #5076 (author: olorinmaia).
  Combine with a recurring time trigger and the SMB action to lower or stop SMB during chosen hours.
- "Toggle" option for the "Enable/disable SMB" automation action. It inverts the current SMB setting
  (on -> off, off -> on) instead of setting a fixed value.
  New On / Off / Toggle selector in the action editor, new string "Toggle SMB".
  Toggle is meant for manual (user action) buttons, not for automatic triggers.
- All three SMB actions (On/Off/Toggle, SMB max minutes, UAM SMB max minutes) refuse to run in simple mode.
  In simple mode these settings are fixed to their default, so a written value would only take effect later,
  when simple mode is turned off. The action now fails and posts an automation notification with the reason.

Changed
- Enable/disable SMB action JSON now stores a "toggle" field. Existing automations without it keep
  their behavior (toggle = false). Rules saved by old test builds, which stored "On", "Off" or "Toggle"
  as text, are read correctly ("Off" no longer becomes ON).
- On / Off / Toggle labels in the editor come from strings.xml (were hardcoded). Action name is now
  "Enable/Disable/Toggle SMB" (was "Flip").

Notes
- Toggle is not idempotent. Use it with one-shot triggers (time of day), not level conditions that can re-fire.
- Max-minutes values are clamped to the same limits as the manual preference.
- No change to the SMB algorithm or safety constraints.

Tests
- Added: ActionSMBMaxMinutesChangeTest, ActionUAMSMBMaxMinutesChangeTest (from PR #5076).
- Extended: ActionSMBChangeTest (toggle on/off, description, JSON, old text JSON, simple mode refusal).
- ActionSMBMaxMinutesChangeTest: simple mode refusal. Max minutes tests use the real string resolver.
- DropdownElementsTest registers the automation strings, as the app does at startup.

## 2026-11-16 — Scene automation (branch: feature/scene-automation)
Base: upstream master f77abd9 (v4.0.0 merge). Not an upstream PR.

Changes
- Run Scene action: per-action guard with three modes: do not run if any scene is
  running (default, unchanged behaviour), replace another scene but skip if the chosen
  scene is already running, always run and replace any running scene.
- Scene trigger: optional scene picker ("Any scene" by default). Now selectable in the
  trigger list (category Other); before it was only a hidden precondition.
- New Stop scene action with an option to run the follow-up (chain) scene or not.
- Stop scene can be limited to one chosen scene, or "Any scene". It only runs while that scene runs, so a
  rule for one scene does not end a scene the user started by hand. Old rules have no scene and stop any scene.
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
- Overview screen: the screens read the scroll position only to know if they are at the top, so they are no longer
  redrawn on every scroll frame. Removed an always-visible AnimatedVisibility around the bottom bar.
- Overview graphs: three new data fields with the values the wizard and the algorithm used, drawn dashed:
  - Wizard ISF: the ISF for carbs the bolus wizard uses (the bold wizard ISF), dashed in the ISF colour beside VSENS.
  - Autosens used by the algorithm: the ratio of each loop run, dashed in the autosens colour beside SENS%.
  - Target used by the algorithm: the target of each loop run, dashed in the "adjusted" target colour.
  Wizard ISF comes from the COB buckets. Autosens and target come from a light query that reads only these two
  fields of the stored loop results with json_extract, like the VSENS query (#5248). Same-unit pairs share an axis.
- Tests: ApsResultAlgorithmValuesTest (real SQLite).
- Overview: show SMB on/off status and the configured SMB maximum basal minutes beside the loop indicator. The minutes use the same colour as the SMB icon, blue when SMB is on and red when it is off.
- Overview: split the sensitivity chip into a purple autosens chip (autosens value, and the value the algorithm used when it differs, both without a percent sign) and the blueish ISF chip.
- Overview: reserve an empty grey block beside the temp basal chip, the same size as it. A value for it comes in a later change.

Ongoing status notification: selectable mode, detailed layout, no pop-up on updates

Added
- New setting Appearance > "Ongoing status notification" with three modes:
  - Live chip (default): glucose chip stays in the status bar. The drawer and
    lock screen show a coloured text card (no graph).
  - Status bar icon: no chip. The glucose value is drawn as the status bar
    icon (based on upstream PR #5553) and the notification uses the detailed
    layout with graph.
  - Live chip on lock screen only: chip and text card while locked, status bar
    icon and detailed layout when unlocked (based on upstream PR #5482).
- Detailed notification content:
  - Glucose, trend arrow, delta, time since last reading (self-updating) and
    SMB state with max minutes.
  - Glucose graph (3 h) with dashed high/low lines, target line, and a
    highlighted target line while a temp target is active.
  - IOB, COB, profile with percentage when modified, Autosens %, ISF from-to
    (purple for variable ISF, target ISF in bold) and the wizard ISF in bold.
  - Glucose, trend and delta are struck through when the reading is older than
    5 minutes, including when no new reading arrives.
- SensitivityOverviewData.isVariableIsf flag (variable ISF vs autosens ISF).
- BgGraphBitmapRenderer: optional limit lines, target line and temp target
  highlight (widget output unchanged).

Changed
- Ongoing notification channel is now "AndroidAPS-Ongoing-Status" with low
  importance (no banner or sound on each update). The old channel is removed
  once no notification uses it. Channel settings in Android start from default.
- In the non-chip states the Samsung Now Bar extras are not applied.
- An unknown stored mode (for example from an imported or older setting) gives the standard notification
  and a log warning. Before, it crashed the app from the screen on/off receiver.
- The time since the reading uses digits of equal width, so the "|" after it no longer moves every second.
- When the detailed status cannot be built or drawn, the error is logged and the standard notification is
  posted instead. The redraw when a reading turns stale can no longer crash the app.

Tests
- PersistentNotificationPluginTest: mapping of every stored mode (also the old "chip_locked" and unknown
  values), the look for each mode and lock state, and that every choice in the setting has a mode.

Known limitations
- The live chip cannot be combined with custom layouts or the graph (Android
  restriction for promoted notifications).
- On Android 17 with MetricStyle the text card is not shown.
- Collapsed height of the custom layout is limited by Android and may need
  tuning on device.
- Not built or tested yet.

Includes code adapted from nightscout/AndroidAPS PR #5482 (AkiLetschne) and
PR #5553 (Der-Schubi).

- Overview: autosens has its own colour (rose), used by the autosens chip and the autosens ratio line in the graphs.
  Before it was the scene purple, the same as a custom temp target.
- Overview: the algorithm autosens value is empty until the loop has run, instead of a made-up 100.
- Tests: SensitivityOverviewImplTest and MainViewModelTest mock what this branch added (loop sensitivities for the
  wizard ISF, the SMB max minutes flow). Known failing: SensitivityChipBlockFitTest, the wizard ISF is cut at font
  1.3 on a 210 dp wide screen. To be fixed with the chip layout change.
- Overview: SMB has its own chip beside the loop chip. It is blue when an SMB can be given now and red and crossed
  out when not. Both levels count: the constraints (SMB preference, open loop, objectives) and the algorithm's
  decision in the last loop run (for example "SMB disabled due to high temptarget"). A tap shows what limits SMB
  and the max minutes. Display only, the loop is not changed. On a client only the synced SMB preference is known.
- Tests: SmbStatusTest.

- TIR graphs: the legend shows only the name and the percentage of each range, no glucose numbers, so it is less cluttered.
- TIR graphs: the edit button sits at the top, level with the "Time in range" title, so it no longer overlaps the legend. The
  TIR graph shows no extra name row: the title already names it.
- TIR graphs: show the tight range (3.9–7.8 mmol/L, 70–140 mg/dL) as a darker green part of the In Range bar, with its own legend item.
- TIR graphs: autosens has its own colour (rose) in the theme; the autosens ratio line in the graphs uses it.
- TIR graphs: the Tight legend item is hidden when the low mark is above 7.8 mmol/L (140 mg/dL), so it never
  shows a reversed range. Spacing uses the theme values.
- Tests: TirCalculationTest (midnight and 24 h window, never past now, past days, invalid readings, tight part).
- Overview layout: the loop and SMB chips sit above the BG circle (phone circle 95 -> 103 dp, so the two chips just
  fit). Beside the circle: profile (whole width), temp basal + reserved block + temp target, IOB + COB,
  autosens + ISF. IOB and autosens are equally wide, so COB and ISF line up. The eventual BG chip is removed.
  The loop chip no longer shows the time since the last loop run: it did not fit.
