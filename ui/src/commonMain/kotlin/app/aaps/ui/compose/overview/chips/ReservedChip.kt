package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalAapsScale

/**
 * Empty chip-shaped block, kept for a value that is added in a later change.
 *
 * Sized like [TbrChip] - the same padding around an icon-sized space - so it takes its own block in
 * the narrow chips row and the temp basal block fits beside it. The grey is the sensor age bar's
 * track grey, so it reads as "nothing here yet" instead of as a status of its own. It carries no
 * text and no semantics on purpose: there is nothing to show and nothing to read out.
 */
@Composable
fun ReservedChip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(AapsSpacing.chipHeight * LocalAapsScale.current)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = AapsSpacing.medium)
        ) {
            Spacer(Modifier.size(AapsSpacing.chipIconSize))
        }
    }
}
