package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.AutosensChip
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobChip
import app.aaps.ui.compose.overview.chips.IobCobChipsRow
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.ReservedChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.SmbChip
import app.aaps.ui.compose.overview.chips.TbrChip
import app.aaps.ui.compose.overview.chips.TempTargetChip

/** Width of [BgInfoSection] with a circle of [circleSize]: the circle and its padding on both sides. */
fun bgSectionWidth(circleSize: Dp): Dp = circleSize + AapsSpacing.small * 2

/**
 * The loop mode chip (with the time since the last loop run) and the SMB chip, above the BG circle. The SMB
 * chip keeps its own width and the loop chip takes the rest, so the row is exactly as wide as the circle and
 * ends where the chips beside the circle start.
 */
@Composable
fun OverviewLoopSmbRow(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    loopAgeText: String = "",
    loopAgeStale: Boolean = false,
    smbEnabled: Boolean = false,
    smbMaxMinutes: Int = 0,
    onNavigate: (NavigationRequest) -> Unit,
    onSmbChipClick: () -> Unit,
    // The running mode chip opens a mutating screen - its click is disabled on an unpaired client (same
    // MASTER_OR_PAIRED_CLIENT gate as nav/Manage), while the chip stays visible as status.
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
                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) },
                modifier = Modifier.weight(1f)
            )
        }
        // Its own chip, not part of the loop mode: a tap shows what limits SMB.
        SmbChip(allowed = smbEnabled, maxMinutes = smbMaxMinutes, onClick = onSmbChipClick)
    }
}

/**
 * The BG circle with the loop mode and SMB chips above it. The column is as wide as the circle, so the
 * chips beside it start right where the SMB chip ends.
 */
@Composable
fun OverviewLoopAndBgColumn(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    circleSize: Dp,
    loopSmbRow: @Composable () -> Unit,
    showTimeAgo: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(bgSectionWidth(circleSize)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        loopSmbRow()
        BgInfoSection(
            bgInfo = bgInfo,
            timeAgoText = timeAgoText,
            size = circleSize,
            showTimeAgo = showTimeAgo
        )
    }
}

/**
 * The chips beside the BG circle:
 * - reserved block, profile
 * - temp basal, temp target
 * - IOB, COB
 * - autosens, ISF
 *
 * The reserved block is built like the temp basal chip, so it has the same width and sits right above it.
 * IOB and autosens get the same width, so COB and ISF do too and the last two rows line up.
 */
@Composable
fun OverviewChipsColumn(
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
    tbrState: TbrState,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    // The temp target chip opens a mutating screen - its click is disabled on an unpaired client, while the
    // chip stays visible as status.
    commandsAllowed: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            // Grey placeholder for a value that comes in a later change.
            ReservedChip()
            ProfileChip(
                profileName = profileName.ifEmpty { stringResource(CoreUiStrings.no_profile_set) },
                isModified = isProfileModified,
                progress = profileProgress,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
                sceneManaged = profileSceneManaged,
                isNoProfile = profileName.isEmpty(),
                // Stays clickable even on an unpaired client: profile management opens read-only there,
                // so this is a view affordance, not a command one (unlike the running-mode / TT chips).
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            TbrChip(
                state = tbrState,
                onClick = onTbrChipClick
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
        IobCobAndSensitivityRows(
            iobUiState = iobUiState,
            cobUiState = cobUiState,
            sensitivityUiState = sensitivityUiState,
            onIobChipClick = onIobChipClick
        )
    }
}

/**
 * IOB/COB above autosens/ISF. IOB and autosens both get the width of the wider one, so COB and ISF, which
 * take the rest of their rows, are equally wide as well.
 */
@Composable
private fun IobCobAndSensitivityRows(
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    onIobChipClick: () -> Unit
) {
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        // Only measured, never placed: the width each of the two chips needs on one line.
        val leftWidth = subcompose("leftWidth") {
            IobChip(state = iobUiState, onClick = {}, showIcon = true)
            AutosensChip(state = sensitivityUiState, onClick = {})
        }.maxOf { it.maxIntrinsicWidth(constraints.maxHeight) }.toDp()
        val rows = subcompose("rows") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IobCobChipsRow(
                    iobUiState = iobUiState,
                    cobUiState = cobUiState,
                    onIobChipClick = onIobChipClick,
                    iobMinWidth = leftWidth
                )
                SensitivityChipBlock(
                    state = sensitivityUiState,
                    autosensMinWidth = leftWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }.single().measure(constraints)
        layout(rows.width, rows.height) { rows.place(0, 0) }
    }
}
