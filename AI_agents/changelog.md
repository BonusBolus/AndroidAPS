## 2026-11-16 — Scene automation (branch: feature/scene-automation)
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
Revert: git revert -m 1 <merge commit>. Check rules that use scene triggers afterwards.