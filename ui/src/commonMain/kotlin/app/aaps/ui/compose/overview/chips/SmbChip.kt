package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * SMB state and the max minutes of basal that limit one SMB. Blue when an SMB can be given now, red and
 * crossed out when not. A tap shows what limits SMB. Not shown when [maxMinutes] is 0.
 *
 * @see SmbChipAllowedPreview
 * @see SmbChipBlockedPreview
 * @param allowed whether an SMB can be given now, see [app.aaps.ui.compose.main.SmbStatus]
 */
@Composable
fun SmbChip(
    allowed: Boolean,
    maxMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (maxMinutes <= 0) return
    val color = if (allowed) AapsTheme.elementColors.insulin else MaterialTheme.colorScheme.error
    val haptic = LocalHapticFeedback.current
    // Same guard as the running mode chip: no 48 dp minimum, so the chip is not padded and centred.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = Color.Transparent,
            modifier = modifier.height(AapsSpacing.chipHeight * LocalAapsScale.current)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AapsSpacing.small)
            ) {
                Box(modifier = Modifier.size(AapsSpacing.chipIconSize)) {
                    Icon(
                        imageVector = IcSmb,
                        contentDescription = stringResource(CoreUiStrings.smb_shortname),
                        tint = color,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!allowed) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.align(Alignment.Center).size(AapsSpacing.chipIconSize)
                        )
                    }
                }
                Text(
                    text = stringResource(UiStrings.smb_chip_minutes, maxMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    modifier = Modifier.padding(start = AapsSpacing.small)
                )
            }
        }
    }
}
