package app.aaps.persistentNotification

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.widget.glance.resolveWidgetBackground
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The 4 × 2 status home screen widgets: profile and scene, glucose, IOB, COB, SMB state, the graph and the
 * sensitivity values, drawn by [DetailedStatusRenderer] like the status notification.
 *
 * A classic RemoteViews widget, not Glance: a Glance text has one style, and these values need several colours
 * and bold parts in one line. `WidgetUpdaterImpl` sends the update broadcast every minute and after each
 * calculation while a screen is on, which also redraws the strike-through when a reading gets old.
 * The background opacity is the same per widget setting the other widgets use (`WidgetConfigureActivity`).
 */
@HasMemberInjections
abstract class StatusWidget(private val detailed: Boolean) : AppWidgetProvider() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var config: Config
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var detailedStatus: DetailedStatusRenderer

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                appWidgetIds.forEach { draw(context, appWidgetManager, it) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.WIDGET, "${javaClass.simpleName} update failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized: the graph is drawn for the new size.
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private suspend fun draw(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val background = resolveWidgetBackground(
            config,
            preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId),
            preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val (widthPx, heightPx) = graphSizePx(context, appWidgetManager.getAppWidgetOptions(appWidgetId))
        val status = detailedStatus.build(
            withGraph = true,
            detailed = detailed,
            labelled = true,
            night = true,
            graphWidthPx = widthPx,
            graphHeightPx = heightPx
        )
        val views =
            if (status == null) detailedStatus.emptyWidgetViews(background, openApp)
            else detailedStatus.widgetViews(status, background, openApp)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * The graph gets the widget size minus the padding and the text rows. The launcher reports a range: the
     * narrow, tall size is the portrait one, the wide, low size the landscape one.
     */
    // shortcut: the text rows are an estimate, so the graph can be stretched a little when the values wrap to two lines.
    private fun graphSizePx(context: Context, options: Bundle): Pair<Int, Int> {
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val widthDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            .takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val heightDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        val density = context.resources.displayMetrics.density
        val graphWidthDp = (widthDp - HORIZONTAL_PADDING_DP).coerceAtLeast(MIN_GRAPH_DP)
        val graphHeightDp = (heightDp - TEXT_ROWS_DP).coerceAtLeast(MIN_GRAPH_DP)
        return (graphWidthDp * density).toInt() to (graphHeightDp * density).toInt()
    }

    private companion object {

        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 110

        /** Left and right padding of widget_status.xml together. */
        const val HORIZONTAL_PADDING_DP = 28

        /** Padding, header, glucose row and one line of values in widget_status.xml together. */
        const val TEXT_ROWS_DP = 104
        const val MIN_GRAPH_DP = 24
    }
}

/** The values of the detailed notification, with the wizard values beside the current ones, and AS, ISF and target marks. */
class StatusDetailedWidget : StatusWidget(detailed = true)

/** Only the current values with a short label, like the simple notification. */
class StatusSimpleWidget : StatusWidget(detailed = false)
