package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun AutosensChipPreview() {
    MaterialTheme {
        AutosensChip(
            state = SensitivityUiState(asText = "112%", ratio = 1.12, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun AutosensChipDisabledPreview() {
    MaterialTheme {
        AutosensChip(
            state = SensitivityUiState(asText = "88%", ratio = 0.88, isEnabled = false, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun AutosensChipWithAlgorithmPreview() {
    MaterialTheme {
        AutosensChip(
            state = SensitivityUiState(asText = "112%", algorithmAutosens = "108%", ratio = 1.12, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}
