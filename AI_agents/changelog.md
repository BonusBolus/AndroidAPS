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
- Like upstream, the switch uses timeshift 0. A temporary switch ends and the old permanent profile
  (with its timeshift) comes back; a permanent switch replaces it, so a timeshift is lost.

Tests
- Permanent switch (valid, description, createProfileSwitch with duration 0), precondition for temporary and
  permanent switches, minimum duration of 1 min, missing duration is invalid, JSON with the permanent flag.
