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
- Added: permanent switch (valid, description, createProfileSwitch with duration 0), precondition only for temporary switches.