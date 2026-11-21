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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResourceOrNull


/**
 * The ISF half of the overview sensitivity block: the profile ISF and the ISF in use, with the
 * Wizard ISF beside them. The autosens value lives in [AutosensChip], which sits to the left of it.
 *
 * @see SensitivityChipAbovePreview
 * @see SensitivityChipIsfDownPreview
 */
@Composable
internal fun SensitivityChip(
    state: SensitivityUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The Wizard ISF earns its room only when it reads differently from the ISF in use beside it, the same
    // rule the autosens chip uses for the algorithm's autosens.
    val wizardIsf = state.wizardIsf.takeIf { it.isNotEmpty() && it != state.isfTo }
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = ElementType.SENSITIVITY.color().copy(alpha = 0.2f),
        modifier = modifier
            .heightIn(min = AapsSpacing.chipHeight)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
        ) {
            if (state.isfFrom.isNotEmpty()) {
                Text(
                    text = state.isfFrom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = selectIsfArrow(state.isfStep),
                    contentDescription = stringResourceOrNull(selectIsfArrowLabel(state.isfStep)),
                    tint = ElementType.SENSITIVITY.color(),
                    modifier = Modifier.size(16.dp)
                )
                // The last value in the chip is bold: the Wizard ISF when it is shown, else the ISF in use.
                Text(
                    text = state.isfTo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (wizardIsf == null) FontWeight.Bold else null,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (wizardIsf != null) {
                Spacer(Modifier.width(AapsSpacing.extraSmall))
                Icon(
                    imageVector = ElementType.BOLUS_WIZARD.icon(),
                    contentDescription = stringResourceOrNull(ElementType.BOLUS_WIZARD.label()),
                    tint = ElementType.SENSITIVITY.color(),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = wizardIsf,
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

private fun selectIsfArrow(step: IsfStep): ImageVector =
    when (step) {
        IsfStep.UP   -> IcArrowFortyfiveUp
        IsfStep.DOWN -> IcArrowFortyfiveDown
        IsfStep.SAME -> IcArrowFlat
    }

/**
 * What the arrow between the two ISF values MEANS, which is that the value rises or falls - not
 * which way the glyph happens to point. Naming the glyph gave "5.5, Up Right, 6.8", and for two
 * equal values "5.5, Right, 5.5", which says nothing at all.
 *
 * Equal returns null on purpose: two identical numbers already say the value did not change, and
 * every word available here would either be wrong ("Right") or borrowed from the glucose trend
 * vocabulary ("stable"), which is a different thing.
 */
private fun selectIsfArrowLabel(step: IsfStep): TextRef? =
    when (step) {
        IsfStep.UP   -> CoreUiStrings.arrow_up
        IsfStep.DOWN -> CoreUiStrings.arrow_down
        IsfStep.SAME -> null
    }
