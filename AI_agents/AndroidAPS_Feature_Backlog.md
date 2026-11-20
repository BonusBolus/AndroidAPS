# AndroidAPS Custom Feature Backlog
 
Fork: `BonusBolus/AndroidAPS` (based on `nightscout/AndroidAPS`, `master` branch)
Target pump: **Medtrum Nano 200U** — driver already present in-tree at `pump/medtrum`.
 
Status of each item verified against fork source (`/home/claude/AndroidAPS`) and upstream
GitHub as of 2026-09-08. Re-verify before starting each item — upstream moves fast and is
mid-migration to Kotlin Multiplatform (KMP), which is already causing PR conflicts (see #5097 notes).
 
---
 
## 1. SMB / UAM max-minutes automation actions
 
**Reference:** [nightscout/AndroidAPS#5076](https://github.com/nightscout/AndroidAPS/pull/5076) (open, unmerged)
 
- Adds two automation actions: **Change SMB max minutes** and **Change UAM SMB max minutes**,
  changing the existing "Max minutes of basal to limit SMB" / "UAM max minutes..." preferences
  at runtime (range 15–120 min, same as the manual preference).
- Combine with a recurring time trigger + the existing enable/disable-SMB action to
  automatically reduce/stop SMB during a chosen window (e.g. overnight).
- Author's rationale: reduces algorithm aggressiveness during higher-uncertainty periods
  (e.g. overnight, dynamic ISF, erratic BG).
- **Already in the fork:** `plugins/automation/.../actions/ActionSMBChange.kt` exists (the
  existing enable/disable-SMB action referenced by the PR). The *max-minutes* actions from
  #5076 are **not** present — this PR's diff would need to be ported in.
- Implementation note: PR author describes "both actions share a small base class and one
  editor," which fits the existing `Action` subclass pattern in that package — check
  `ActionSMBChange.kt` as the closest existing analog before porting.
## 2. Dynamic/automation-driven SMB sizing (bigger/smaller SMB based on BG rate-of-change, BG level, etc.)
 
- No single upstream PR found matching this exactly — likely maps to a **new automation
  action or algorithm-level change** rather than an existing PR. Needs scoping:
  - Does "bigger/smaller SMB" mean scaling `maxSMB` size, or influencing the AutoISF/SMB
    calculation inputs (sensitivity ratio) based on rate-of-change?
  - AutoISF (see item 5 below) already does BG-level/rate-of-change-based ISF adjustment —
    worth checking whether that covers this need before building something new.
- Treat as its own design task once scoped; likely touches the OpenAPS/AutoISF determine-basal
  logic (safety-critical — extra upstream-diff scrutiny required per project guidelines).
## 3. Notification update — BG in notification icon
 
**Reference:** [nightscout/AndroidAPS#5097](https://github.com/nightscout/AndroidAPS/pull/5097) (open, unmerged, 4 commits)
 
- New preference `NotificationShowBgOnIcon` (`notification_show_bg_on_icon`), under "Local Alerts".
- Replaces the persistent notification's small icon with a live BG value rendered via a new
  `BgIconRenderer` → 96×96 ARGB_8888 bitmap, cached, following the pattern of the existing
  `IconBitmapRenderer`.
- Author's memory-safety justification: tiny bitmap (~36KB), short lifetime (each overview
  update replaces the prior notification), cached and only regenerated on value change.
- **Caveat from PR thread:** maintainer (olorinmaia) flagged that AAPS is mid-migration to a
  KMP branch, and this and "many other open PRs" will likely need to wait / face merge
  conflicts until that concludes. Worth checking KMP migration status before porting.
## 4. Garmin — connection fix, bolus from watch, temp target from watch, step syncing
 
- **Garmin support already exists in this fork's codebase**, under
  `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/garmin/` — includes `GarminPlugin`,
  `GarminDevice`, `GarminDeviceClient`, `GarminMessenger`, `GarminReceiver`, `LoopHub`,
  `DeltaVarEncodedList`, plus a simulator client and test suite. This is the existing
  Garmin ConnectIQ sync integration (BG/data sync to Garmin devices).
- **Bolus from watch / temp target from watch:** referenced against
  [swissalpine/AndroidAPS](https://github.com/swissalpine/AndroidAPS) — but that fork's own
  README only describes "private changes concerning tbr [temp basal rate] management, iob and
  layout" and does not explicitly document watch-bolus or watch-temp-target features.
  **Not yet verified** — need to diff `swissalpine/AndroidAPS` against upstream (or inspect
  their `plugins/sync/garmin` / wear-companion code directly) to find the actual watch-bolus
  and watch-temp-target implementation before porting anything.
- "Garmin connection fix" — no specific issue/PR identified yet; likely refers to a live bug
  the user has hit personally. Needs a concrete repro/symptom to scope.
- Action item: clone/inspect `swissalpine/AndroidAPS` source to find the real diff for watch
  bolus + temp target, rather than assuming from the README.
## 5. Closed-loop APS variants — AutoISF
 
**Reference:** [T-o-b-i-a-s/AndroidAPS](https://github.com/T-o-b-i-a-s/AndroidAPS), branch `3.4.2.6+aisf3.2.1`
 
- This repo is **not** the origin of AutoISF — it's a convenience build combining official
  AndroidAPS 3.4.2.6 with **autoISF 3.2.1** already integrated, maintained by T-o-b-i-a-s to
  simplify the build process for users.
- Actual AutoISF design/development is by **ga-zelle** — canonical source:
  [ga-zelle/autoISF](https://github.com/ga-zelle/autoISF/tree/A3.4.2.6_ai3.2.1), with
  contributions from swissalpine, claudi, BerNie, mountrcg, Bjr, Gohtraw, Koelewij, T-o-b-i-a-s.
- What AutoISF does: adjusts insulin sensitivity dynamically based on scenarios like high BG,
  accelerating/decelerating BG, BG plateau — has many tunable settings. Requires well-tested
  basal rates / ISF / carb ratios as a baseline before enabling.
- Status upstream: AutoISF was introduced as a plugin in AAPS 3.3 but **only enabled in dev
  mode** in the official master/dev branches — T-o-b-i-a-s's branch exposes the latest 3.2.1
  version without needing dev mode.
- Given the relevance to item 2 (dynamic SMB sizing by rate-of-change/BG level), review
  ga-zelle's AutoISF source directly as the primary reference, not the T-o-b-i-a-s convenience
  build.
## 6. Fat/protein/carb-separated bolus options
 
- No specific upstream PR identified yet. Needs scoping: likely relates to extended/combo
  bolus calculation incorporating FPU (fat-protein units) — AAPS already has some FPU handling
  for extended boluses; check `core`/bolus-wizard code for current FPU support before assuming
  this is greenfield.
---
 
## Open verification items (do before starting corresponding feature)
 
- [ ] Diff `swissalpine/AndroidAPS` vs upstream to find actual watch-bolus/temp-target code (item 4)
- [ ] Check current KMP migration status on upstream `dev` (affects items 1, 3, and any UI-adjacent work)
- [ ] Check current FPU/extended-bolus handling in fork before scoping item 6
- [ ] Scope item 2 concretely (algorithm change vs. new automation action) before implementation
- [ ] Get a concrete repro for the "Garmin connection fix" (item 4)
 
