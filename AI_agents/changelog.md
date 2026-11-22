## UI

- Overview: show the profile glucose target and the current sensitivity-adjusted target, with the active target highlighted. Use a direction arrow for the adjusted target.
- Overview: show the effective algorithm ISF and Wizard ISF in the sensitivity section. Mark Wizard ISF with the bolus wizard icon.
- Overview: show SMB on/off status and the configured SMB maximum basal minutes beside the loop indicator. The minutes use the same colour as the SMB icon, blue when SMB is on and red when it is off.
- Overview: split the sensitivity chip into a purple autosens chip (autosens value, and the value the algorithm used when it differs, both without a percent sign) and the blueish ISF chip.
- Overview: reserve an empty grey block beside the temp basal chip, the same size as it. A value for it comes in a later change.
- Widgets (ui-feature/new-widgets): two 4 × 2 status widgets, "AAPS status, detailed" and "AAPS status, simple". They show profile | scene, glucose with arrow, delta and age, IOB • COB, the SMB state, the 3 h graph with bolus, carb and SMB marks and the (temp) target, and the autosens, ISF and target values of the detailed or simple notification. The detailed widget adds AS, ISF and 🎯 marks. Drawn by the same renderer as the notification, always in the dark colours, with the background opacity setting the other widgets use.

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