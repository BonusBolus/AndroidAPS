package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.EventualBgChip
import app.aaps.ui.compose.overview.chips.IobCobChipsRow
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.ReservedChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SmbChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.TbrChip
import app.aaps.ui.compose.overview.chips.TempTargetChip

/**
 * The full-width row above the BG circle: loop mode with the time since the last loop run, SMB, profile and
 * temp target. Above the circle these chips get the whole screen width, so the profile name is not cut off.
 */
@Composable
fun OverviewTopChipsRow(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    loopAgeText: String = "",
    loopAgeStale: Boolean = false,
    smbEnabled: Boolean = false,
    smbMaxMinutes: Int = 0,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    profileTargetText: String = "",
    tempTargetStep: IsfStep = IsfStep.SAME,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    onNavigate: (NavigationRequest) -> Unit,
    onSmbChipClick: () -> Unit,
    // The command chips (running mode / profile / temp target) open mutating screens — their click is disabled on an
    // unpaired client (same MASTER_OR_PAIRED_CLIENT gate as nav/Manage), while the chip stays visible as status.
    commandsAllowed: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        if (runningModeText.isNotEmpty()) {
            RunningModeChip(
                mode = runningMode,
                text = runningModeText,
                progress = runningModeProgress,
                remaining = runningModeRemaining,
                sceneManaged = runningModeSceneManaged,
                loopAge = loopAgeText,
                loopAgeStale = loopAgeStale,
                enabled = commandsAllowed && LocalClientRole.current.allows(ElementType.RUNNING_MODE.role),
                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
            )
        }
        // Its own chip, not part of the loop mode: a tap shows what limits SMB.
        SmbChip(allowed = smbEnabled, maxMinutes = smbMaxMinutes, onClick = onSmbChipClick)
        ProfileChip(
            profileName = profileName.ifEmpty { stringResource(CoreUiStrings.no_profile_set) },
            isModified = isProfileModified,
            progress = profileProgress,
            onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
            sceneManaged = profileSceneManaged,
            isNoProfile = profileName.isEmpty(),
            // Stays clickable even on an unpaired client: profile management opens read-only there,
            // so this is a view affordance, not a command one (unlike the running-mode / TT chips).
            // As wide as its name, so the temp target gets the rest; it shrinks only when the row is full.
            modifier = Modifier.weight(1f, fill = false)
        )
        if (tempTargetText.isNotEmpty()) {
            TempTargetChip(
                targetText = tempTargetText,
                profileTargetText = profileTargetText,
                tempTargetStep = tempTargetStep,
                state = tempTargetState,
                progress = tempTargetProgress,
                reason = tempTargetReason,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) },
                sceneManaged = tempTargetSceneManaged,
                enabled = commandsAllowed && LocalClientRole.current.allows(ElementType.TEMP_TARGET_MANAGEMENT.role)
            )
        }
    }
}

/** The chips beside the BG circle: temp basal, eventual BG and the reserved block; IOB and COB; sensitivity. */
@Composable
fun OverviewChipsColumn(
    tbrState: TbrState,
    eventualBgText: String,
    eventualBgTime: String = "",
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            TbrChip(
                state = tbrState,
                onClick = onTbrChipClick
            )
            EventualBgChip(text = eventualBgText, time = eventualBgTime)
            // Grey placeholder for a value that comes in a later change.
            ReservedChip()
        }
        IobCobChipsRow(
            iobUiState = iobUiState,
            cobUiState = cobUiState,
            onIobChipClick = onIobChipClick
        )
        SensitivityChipBlock(
            state = sensitivityUiState,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
