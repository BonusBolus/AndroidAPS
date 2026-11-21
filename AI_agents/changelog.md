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
