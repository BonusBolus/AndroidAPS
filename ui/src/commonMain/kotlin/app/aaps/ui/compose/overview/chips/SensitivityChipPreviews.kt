package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.IsfStep

@Preview(showBackground = true)
@Composable
internal fun SensitivityChipAbovePreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(isfFrom = "5.5", isfTo = "6.8", wizardIsf = "6.4", isfStep = IsfStep.UP, ratio = 1.12, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SensitivityChipIsfDownPreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(isfFrom = "6.0", isfTo = "4.2", wizardIsf = "5.1", isfStep = IsfStep.DOWN, ratio = 1.15, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}
