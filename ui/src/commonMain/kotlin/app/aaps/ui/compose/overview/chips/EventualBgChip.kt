package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * The eventual BG of the last loop run: where the algorithm expects the glucose to end when all insulin and
 * carbs on board have acted. Not shown before the first loop run.
 *
 * @param text the eventual BG in the user's units, empty when there is no loop run
 * @param time about when it is reached, empty when unknown
 * @see EventualBgChipPreview
 */
@Composable
fun EventualBgChip(text: String, time: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(AapsSpacing.chipHeight * LocalAapsScale.current)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = AapsSpacing.medium)) {
            Text(
                text = if (time.isEmpty()) stringResource(UiStrings.eventual_bg_chip, text)
                else stringResource(UiStrings.eventual_bg_chip_with_time, text, time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun EventualBgChipPreview() {
    MaterialTheme {
        EventualBgChip(text = "9.0", time = "22:00")
    }
}
