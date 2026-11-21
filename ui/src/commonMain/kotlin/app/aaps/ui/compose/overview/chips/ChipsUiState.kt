package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.overview.IsfStep

/**
 * What the overview chips show.
 *
 * Kept in their own file rather than beside `ChipsViewModel`: these are plain values with no Android
 * in them, and every composable and preview that reads them can then be shared code.
 */
@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

@Immutable
data class SensitivityUiState(
    val asText: String = "",
    val algorithmAutosens: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val wizardIsf: String = "",
    val isfStep: IsfStep = IsfStep.SAME,
    val dialogText: String = "",
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)

/**
 * The last loop run, for the loop chip and the eventual BG chip.
 *
 * @param ageText minutes since the last loop run, empty before the first run
 * @param ageStale true when the last run is so old that the loop may have stopped
 * @param eventualBgText the eventual BG of the last run in the user's units, empty when unknown
 * @param eventualBgTime about when it is reached (end of the prediction lines), empty when unknown
 */
@Immutable
data class LoopUiState(
    val ageText: String = "",
    val ageStale: Boolean = false,
    val eventualBgText: String = "",
    val eventualBgTime: String = ""
)
