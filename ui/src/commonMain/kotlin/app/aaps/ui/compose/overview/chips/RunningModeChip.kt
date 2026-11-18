package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import app.aaps.core.data.model.RM
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopOpen
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopPausedDst
import app.aaps.core.ui.compose.icons.IcLoopPausedPump
import app.aaps.core.ui.compose.icons.IcLoopSuperbolus
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.loopColor
import app.aaps.core.ui.compose.stringResource

/**
 * @see RunningModeChipClosedLoopPreview
 * @see RunningModeChipSuspendedPreview
 * @see RunningModeChipClosedLoopSmbPreview
 * @see RunningModeChipOpenLoopSmbPreview
 */
/**
 * [mode] is null when no running mode is known for now - a client that has never synced a permanent
 * record, or a database that has just been reset. That is drawn as a question mark rather than as
 * [RM.Mode.DISABLED_LOOP], which would tell the user the loop is off when nothing of the sort is
 * known.
 */
@Composable
fun RunningModeChip(
    mode: RM.Mode?,
    text: String,
    progress: Float,
    modifier: Modifier = Modifier,
    remaining: String = "",
    sceneManaged: Boolean = false,
    smbEnabled: Boolean = false,
    smbMaxMinutes: Int = 0,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val isTemporary = mode?.mustBeTemporary() == true
    val iconColor = mode?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isTemporary) iconColor.copy(alpha = 0.2f) else Color.Transparent
    val haptic = LocalHapticFeedback.current

    // Disable the clickable Surface's 48dp minimum interactive size so this content-width chip
    // isn't padded to 48dp and centered, which would inset it ~6dp from the column's left edge
    // (same guard IobChip uses).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            enabled = enabled,
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier
                .height(AapsSpacing.chipHeight * LocalAapsScale.current)
        ) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .fillMaxHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = AapsSpacing.medium, end = AapsSpacing.small, top = AapsSpacing.small, bottom = AapsSpacing.small)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        Icon(
                            imageVector = mode?.toIcon() ?: IcQuestion,
                            contentDescription = text,
                            tint = iconColor,
                            modifier = Modifier.size(AapsSpacing.chipIconSize)
                        )
                        if (smbMaxMinutes > 0) {
                            Spacer(modifier = Modifier.width(AapsSpacing.small))
                            Box(modifier = Modifier.size(AapsSpacing.chipIconSize)) {
                                Icon(
                                    imageVector = IcSmb,
                                    contentDescription = stringResource(CoreUiStrings.smb_shortname),
                                    tint = if (smbEnabled) AapsTheme.elementColors.insulin else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (!smbEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(CoreUiStrings.smb_shortname),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(AapsSpacing.chipIconSize)
                                    )
                                }
                            }
                            Text(
                                text = "${smbMaxMinutes}m",
                                style = MaterialTheme.typography.bodyMedium,
                                // Same colour as the SMB icon beside it, so the minutes read as part
                                // of the SMB state: blue when SMB is on, red when it is off.
                                color = if (smbEnabled) AapsTheme.elementColors.insulin else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = AapsSpacing.small)
                            )
                        }
                    }
                    if (remaining.isNotEmpty()) {
                        Text(
                            text = remaining,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = AapsSpacing.small)
                        )
                    }
                    if (sceneManaged) {
                        SceneBadge(modifier = Modifier.padding(start = AapsSpacing.small))
                    }
                }
                // Progress bar overlaid at the bottom (out of flow) so the Row above stays vertically
                // centered — consistent with the other chips. Manual track/fill instead of
                // LinearProgressIndicator: the indicator's large (~240dp) intrinsic width would drive
                // this chip's IntrinsicSize.Max width to full width. Empty Boxes add no intrinsic width.
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(AapsSpacing.chipProgressHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(iconColor.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(AapsSpacing.chipProgressHeight)
                                .background(iconColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extension to get theme color for RM.Mode.
 * Thin composable wrapper around the shared [loopColor] mapping.
 */
@Composable
internal fun RM.Mode.toColor(): Color = loopColor(AapsTheme.generalColors)

/**
 * Extension to get Compose icon for RM.Mode
 */
internal fun RM.Mode.toIcon(): ImageVector = when (this) {
    RM.Mode.CLOSED_LOOP       -> IcLoopClosed
    RM.Mode.CLOSED_LOOP_LGS   -> IcLoopLgs
    RM.Mode.OPEN_LOOP         -> IcLoopOpen
    RM.Mode.DISABLED_LOOP     -> IcLoopDisabled
    RM.Mode.SUPER_BOLUS       -> IcLoopSuperbolus
    RM.Mode.DISCONNECTED_PUMP -> IcLoopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP -> IcLoopPausedPump
    RM.Mode.SUSPENDED_BY_DST  -> IcLoopPausedDst
    RM.Mode.SUSPENDED_BY_USER -> IcLoopPaused

    RM.Mode.RESUME            -> IcLoopClosed
}
