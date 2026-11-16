feature/smb-max-minute-automation-toggle

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
- Extended: ActionSMBChangeTest (toggle on/off, description, JSON).