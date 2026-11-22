## UI

- Overview: show the profile glucose target and the current sensitivity-adjusted target, with the active target highlighted. Use a direction arrow for the adjusted target.
- Overview: show the effective algorithm ISF and Wizard ISF in the sensitivity section. Mark Wizard ISF with the bolus wizard icon.
- Overview: show SMB on/off status and the configured SMB maximum basal minutes beside the loop indicator. The minutes use the same colour as the SMB icon, blue when SMB is on and red when it is off.
- Overview: split the sensitivity chip into a purple autosens chip (autosens value, and the value the algorithm used when it differs, both without a percent sign) and the blueish ISF chip.
- Overview: reserve an empty grey block beside the temp basal chip, the same size as it. A value for it comes in a later change.
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
- Overview layout: the loop and SMB chips sit above the BG circle (phone circle 95 -> 103 dp, so the two chips just
  fit). Beside the circle: profile (whole width), temp basal + reserved block + temp target, IOB + COB,
  autosens + ISF. IOB and autosens are equally wide, so COB and ISF line up. The eventual BG chip is removed.
  The loop chip no longer shows the time since the last loop run: it did not fit.
