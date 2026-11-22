package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.overview.chips.AutosensChip
import app.aaps.ui.compose.overview.chips.SensitivityChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState

/**
 * The overview sensitivity block, split in two: the always shown autosens chip, then the ISF chip
 * when there is an ISF to show. Both open the same dialog.
 */
@Composable
fun SensitivityChipBlock(
    state: SensitivityUiState,
    modifier: Modifier = Modifier,
    // The autosens chip is at least this wide, so it can line up with the IOB chip above it.
    autosensMinWidth: Dp = 0.dp
) {
    var showSensitivityDialog by remember { mutableStateOf(false) }
    val onClick = { if (state.dialogText.isNotEmpty()) showSensitivityDialog = true }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        AutosensChip(
            state = state,
            onClick = onClick,
            modifier = Modifier.widthIn(min = autosensMinWidth)
        )
        if (state.isfFrom.isNotEmpty() || state.wizardIsf.isNotEmpty()) {
            SensitivityChip(
                state = state,
                onClick = onClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (showSensitivityDialog) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.sensitivity),
            message = state.dialogText,
            onConfirm = { showSensitivityDialog = false },
            onDismiss = { showSensitivityDialog = false }
        )
    }
}
