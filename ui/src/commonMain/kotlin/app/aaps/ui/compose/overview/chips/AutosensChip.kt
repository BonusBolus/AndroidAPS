package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.icons.IcAsAbove
import app.aaps.core.ui.compose.icons.IcAsAboveX
import app.aaps.core.ui.compose.icons.IcAsBelow
import app.aaps.core.ui.compose.icons.IcAsBelowX
import app.aaps.core.ui.compose.icons.IcAsX
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * The autosens half of the overview sensitivity block: the autosens icon and the current autosens
 * value, then the value the algorithm used when it reads differently. Always shown, so the user can
 * see the autosens state even when there is no ISF step to show beside it.
 *
 * @see SensitivityChip
 * @see AutosensChipPreview
 * @see AutosensChipDisabledPreview
 * @see AutosensChipWithAlgorithmPreview
 */
@Composable
internal fun AutosensChip(
    state: SensitivityUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = selectAutosensIcon(ratio = state.ratio, isEnabled = state.isEnabled)
    // Same purple the overview graphs use for variable sensitivity, so one colour means the same
    // thing in the chips and in the graphs.
    val color = AapsTheme.elementColors.scene
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Autosens being switched off is drawn as a small X across the icon. So a screen reader read out
    // a percentage that is not actually being applied, with no sign of it. Above or below profile
    // stays unspoken on purpose - the percentage itself already says which way it goes.
    val autosensOff = stringResource(CoreUiStrings.autosens_state_off)
    // "Always show the current autosens": the text is empty only at exactly 100%, and a ratio of 1.0
    // is what 100% means, so it is formatted here instead of leaving the chip without a number.
    val autosensText = state.asText.ifEmpty { stringResource(CoreUiStrings.autosens_chip_value, state.ratio * 100) }
    // The algorithm's own ratio earns its room in the row only when it reads differently from the
    // autosens above. Both show the same whole number, so most of the time this would show it twice
    // and take away from the ISF chip beside it. When the two really differ - autosens off, or a
    // temp target that took sensitivity over - both numbers stay visible.
    val algorithmAutosens = state.algorithmAutosens.takeIf { it.isNotEmpty() && it != autosensText }
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = color.copy(alpha = 0.2f),
        modifier = modifier
            .heightIn(min = AapsSpacing.chipHeight)
            .clickable(onClick = onClick)
            .then(
                if (!state.isEnabled) Modifier.semantics { stateDescription = autosensOff }
                else Modifier
            )
    ) {
        // No padding before the icon: its 24 dp box already keeps an empty margin around the glyph, and the
        // row beside the BG circle is narrow.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = AapsSpacing.medium, top = AapsSpacing.small, bottom = AapsSpacing.small)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResourceOrNull(ElementType.SENSITIVITY.label()),
                tint = color,
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
            // The last value in the chip is bold: the algorithm's value when it is shown, else the autosens.
            Text(
                text = autosensText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (algorithmAutosens == null) FontWeight.Bold else null,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (algorithmAutosens != null) {
                Spacer(Modifier.width(AapsSpacing.extraSmall))
                Icon(
                    imageVector = ElementType.BOLUS_WIZARD.icon(),
                    contentDescription = stringResourceOrNull(ElementType.BOLUS_WIZARD.label()),
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = algorithmAutosens,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun selectAutosensIcon(ratio: Double, isEnabled: Boolean): ImageVector =
    if (isEnabled) {
        when {
            ratio > 1.0 -> IcAsAbove
            ratio < 1.0 -> IcAsBelow
            else        -> IcAs
        }
    } else {
        when {
            ratio > 1.0 -> IcAsAboveX
            ratio < 1.0 -> IcAsBelowX
            else        -> IcAsX
        }
    }
