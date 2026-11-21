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