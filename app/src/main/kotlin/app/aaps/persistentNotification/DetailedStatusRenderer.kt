package app.aaps.persistentNotification

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.aaps.R as AppR
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.SensitivityOverviewData
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.GeneralColors
import app.aaps.core.ui.compose.LightGeneralColors
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.ElementColors
import app.aaps.core.ui.compose.navigation.LightElementColors
import app.aaps.core.ui.compose.ttReasonColor
import app.aaps.core.ui.extensions.generateCOBString
import app.aaps.plugins.main.R as MainR
import app.aaps.ui.widget.glance.BgGraphBitmapRenderer
import app.aaps.ui.widget.glance.BgGraphColors
import app.aaps.ui.widget.glance.BgGraphInput
import dev.zacsweers.metro.Inject
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

/**
 * Everything the ongoing status notification shows besides the plain lines: glucose, trend, delta, age,
 * profile, scene, SMB state, IOB, COB, sensitivity, target and the glucose graph - as a coloured text card (for
 * the live chip, where Android allows no custom views) or as a custom [RemoteViews] layout with the graph.
 * The status home screen widgets ([StatusWidget]) show the same, with their own layout.
 *
 * Two levels of detail. Detailed: the values carry no labels, their colours say what they are, and beside the
 * current values it shows the profile, algorithm and Wizard values, with the overview chips' rules for which
 * value is shown and which one is bold. Simple: only the current values, with a short label, and only the
 * algorithm's target in bold.
 */
@Inject
class DetailedStatusRenderer(
    private val context: Context,
    private val cacheProvider: () -> OverviewDataCache,
    private val lastBgData: LastBgData,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val trendCalculator: TrendCalculator,
    private val iobCobCalculator: IobCobCalculator,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val sensitivityOverview: SensitivityOverview,
    private val activeSceneSync: ActiveSceneSync,
    private val preferences: Preferences,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) {

    class Status(
        val bgText: String,
        val bgColor: Int,
        val trendSymbol: String?,
        val deltaText: String?,
        val readingTimestamp: Long,
        val isStale: Boolean,
        /** Profile • scene, for the chip title */
        val profileLine: String,
        /** Profile | scene, for the header of the graph layout */
        val header: String,
        /** IOB • COB | SMB: the first line of the chip card and the right part of the graph layout's top row */
        val cardValues: CharSequence,
        /** IOB • COB alone, for the widget, which shows the SMB on its own line */
        val iobCob: CharSequence,
        /** The SMB state, null when there is no SMB limit to show */
        val smb: CharSequence?,
        /** autosens | ISF | target: the second line of the chip card and the line under the graph */
        val sensitivityLine: CharSequence,
        val graph: Bitmap?
    )

    /** Colours that read on the notification background, which follows the system light or dark mode. */
    private class Palette(night: Boolean) {

        val element: ElementColors = if (night) DarkElementColors else LightElementColors
        val general: GeneralColors = if (night) DarkGeneralColors else LightGeneralColors
        val low = 0xFFE53935.toInt()
        val inRange = if (night) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
        val high = if (night) 0xFFFFCA28.toInt() else 0xFFF57F17.toInt()

        // Low and high lines in the colours xDrip uses, so they read the same as the xDrip notification.
        val lowLine = 0xFFC62828.toInt()
        val highLine = 0xFFFFB300.toInt()
        val target = if (night) 0x66FFFFFF else 0x66000000
    }

    /**
     * @return null when there is no valid profile or no reading: the caller then keeps the plain notification
     * @param withGraph the graph bitmap is only needed for the custom layout
     * @param detailed the detailed or the simple sensitivity line
     * @param night dark colours. The widget always uses them: it sits on the wallpaper, not on a notification.
     */
    suspend fun build(
        withGraph: Boolean,
        detailed: Boolean,
        night: Boolean = isNight(),
        graphWidthPx: Int = defaultGraphWidthPx(),
        graphHeightPx: Int = defaultGraphHeightPx()
    ): Status? {
        if (!profileFunction.isProfileValid("Notification")) return null
        val lastBg = lastBgData.lastBg() ?: return null
        val now = dateUtil.now()
        val palette = Palette(night)

        val bgColor = when {
            lastBgData.isLow()  -> palette.low
            lastBgData.isHigh() -> palette.high
            else                -> palette.inRange
        }
        // Same filter as the plain notification: no arrow when the trend is not known.
        val trendSymbol = trendCalculator.getTrendArrow(iobCobCalculator.ads)
            ?.takeIf { it != TrendArrow.NONE && it != TrendArrow.TRIPLE_UP && it != TrendArrow.TRIPLE_DOWN }
            ?.symbol
        val deltaText = glucoseStatusProvider.glucoseStatusData?.let { profileUtil.fromMgdlToSignedStringInUnits(it.delta) }

        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val iob = rh.gs(MainR.string.notification_iob_short, bolusIob.iob + basalIob.basaliob)
        val cob = iobCobCalculator.getCobInfo("DetailedStatusRenderer").generateCOBString(decimalFormatter)
        // The customised name already carries the percentage and time shift when they are not the default.
        val profile = profileFunction.getProfileName()
        val scene = scene(now)

        val smbMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb)
        // Like the overview chip: nothing to show without a limit. Blue when on, red and struck through when off.
        val smb = when {
            smbMinutes <= 0                       -> null
            preferences.get(BooleanKey.ApsUseSmb) -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb, smbMinutes), color = palette.element.insulin.toArgb())
            else                                  -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb, smbMinutes), color = palette.low, strike = true)
        }
        val sensitivity = sensitivityOverview.build()
        val target = cacheProvider().tempTargetFlow.value

        val iobCob = SpannableStringBuilder()
            .add(iob, color = palette.element.insulin.toArgb())
            .dot()
            .add(cob, color = palette.element.cob.toArgb())
        val cardValues = SpannableStringBuilder(iobCob)
        smb?.let { cardValues.bar().append(it) }

        // Always autosens first, then ISF, then the target.
        val parts =
            if (detailed) listOf(autosens(sensitivity, palette), isf(sensitivity, palette), target?.let { target(it, palette) })
            else listOf(simpleAutosens(sensitivity, palette), simpleIsf(sensitivity, palette), target?.let { simpleTarget(it, palette) })
        val sensitivityLine = SpannableStringBuilder()
        parts.filterNotNull().forEach { sensitivityLine.barIfNotEmpty().append(it) }

        return Status(
            bgText = profileUtil.fromMgdlToStringInUnits(lastBg.recalculated),
            bgColor = bgColor,
            trendSymbol = trendSymbol,
            deltaText = deltaText,
            readingTimestamp = lastBg.timestamp,
            // The same limit the overview uses to strike the glucose through.
            isStale = !lastBgData.isActualBg(),
            profileLine = scene?.let { "$profile$DOT$it" } ?: profile,
            header = scene?.let { "$profile$BAR$it" } ?: profile,
            cardValues = cardValues,
            iobCob = iobCob,
            smb = smb,
            sensitivityLine = sensitivityLine,
            graph = if (withGraph) graph(now, palette, graphWidthPx, graphHeightPx) else null
        )
    }

    /**
     * The notification title for the live chip: the coloured glucose, trend and delta, struck through together
     * when the reading is stale, then the profile and the scene when [withProfile].
     */
    fun title(status: Status, withProfile: Boolean): CharSequence {
        val title = headline(status, deltaScale = 1.0f)
        if (withProfile) title.append(BAR).append(status.profileLine)
        return title
    }

    /** The two lines of the live chip card under the title. */
    fun card(status: Status): CharSequence =
        SpannableStringBuilder(status.cardValues).append("\n").append(status.sensitivityLine)

    /** The custom layout, used as collapsed and expanded content alike. */
    fun remoteViews(status: Status): RemoteViews =
        RemoteViews(context.packageName, AppR.layout.notification_status_detailed).apply {
            setTextViewText(AppR.id.status_bg, headline(status, deltaScale = 0.85f))
            // Ticks by itself, so the age is right without a notification update every minute. The system UI
            // redraws it only while the notification is on screen, AAPS is not woken up for it.
            setChronometer(
                AppR.id.status_age,
                SystemClock.elapsedRealtime() - (dateUtil.now() - status.readingTimestamp),
                rh.gs(AppR.string.notification_age_format),
                true
            )
            setTextViewText(AppR.id.status_top_values, status.cardValues)
            status.graph?.let { setImageViewBitmap(AppR.id.status_graph, it) }
            if (status.sensitivityLine.isNotEmpty()) setTextViewText(AppR.id.status_values, status.sensitivityLine)
            else setViewVisibility(AppR.id.status_values, View.GONE)
        }

    /**
     * The home screen widget layout. White text on [background], which the user sets per widget.
     * Glucose and arrow are one text of one size, so a stale reading gets one straight line through both.
     */
    fun widgetViews(status: Status, background: Int, openApp: PendingIntent?): RemoteViews =
        RemoteViews(context.packageName, AppR.layout.widget_status).apply {
            setInt(AppR.id.widget_background, "setColorFilter", background or 0xFF000000.toInt())
            setInt(AppR.id.widget_background, "setImageAlpha", Color.alpha(background))
            setTextViewText(AppR.id.widget_header, status.header)
            val glucose = SpannableStringBuilder().add(status.bgText, color = status.bgColor, bold = true)
            status.trendSymbol?.let { glucose.add(" $it", color = status.bgColor, bold = true) }
            setTextViewText(AppR.id.widget_bg, glucose.strikeIf(status.isStale))
            setTextViewText(AppR.id.widget_delta, SpannableStringBuilder(status.deltaText.orEmpty()).strikeIf(status.isStale))
            // No format: the age sits under the delta, without the bullet the notification needs on its one line.
            setChronometer(AppR.id.widget_age, SystemClock.elapsedRealtime() - (dateUtil.now() - status.readingTimestamp), null, true)
            setTextViewText(AppR.id.widget_iob_cob, status.iobCob)
            if (status.smb != null) setTextViewText(AppR.id.widget_smb, status.smb)
            else setViewVisibility(AppR.id.widget_smb, View.GONE)
            status.graph?.let { setImageViewBitmap(AppR.id.widget_graph, it) }
            if (status.sensitivityLine.isNotEmpty()) setTextViewText(AppR.id.widget_values, status.sensitivityLine)
            else setViewVisibility(AppR.id.widget_values, View.GONE)
            openApp?.let { setOnClickPendingIntent(AppR.id.widget_root, it) }
        }

    /** The widget without a reading or a profile: only the background and a short message. */
    fun emptyWidgetViews(background: Int, openApp: PendingIntent?): RemoteViews =
        RemoteViews(context.packageName, AppR.layout.widget_status).apply {
            setInt(AppR.id.widget_background, "setColorFilter", background or 0xFF000000.toInt())
            setInt(AppR.id.widget_background, "setImageAlpha", Color.alpha(background))
            setTextViewText(AppR.id.widget_header, rh.gs(CoreUiR.string.no_data))
            listOf(AppR.id.widget_status_row, AppR.id.widget_graph, AppR.id.widget_values).forEach { setViewVisibility(it, View.GONE) }
            openApp?.let { setOnClickPendingIntent(AppR.id.widget_root, it) }
        }

    private fun SpannableStringBuilder.strikeIf(stale: Boolean): SpannableStringBuilder {
        if (stale) setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    /** Glucose and trend in the range colour, then the delta. All struck through together when the reading is stale. */
    private fun headline(status: Status, deltaScale: Float): SpannableStringBuilder {
        val line = SpannableStringBuilder()
            .add(status.bgText, color = status.bgColor, bold = true)
        status.trendSymbol?.let { line.add(" $it", color = status.bgColor, bold = true) }
        status.deltaText?.let { line.add(" $it", scale = deltaScale) }
        if (status.isStale) line.setSpan(StrikethroughSpan(), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return line
    }

    /** The active scene with its remaining time, null when no scene is active. A scene without an end has no time. */
    private fun scene(now: Long): String? {
        val state = activeSceneSync.activeSceneState.value?.takeIf { it.lifecycle == SceneLifecycle.ACTIVE } ?: return null
        val end = state.endsAt ?: return state.scene.name
        return rh.gs(InterfacesR.string.value_with_detail, state.scene.name, dateUtil.untilString(end, rh, withParentheses = false))
    }

    /** The current autosens. Empty at exactly 100 % in the overview data: then the ratio itself is the number, as on the chip. */
    private fun currentAutosens(data: SensitivityOverviewData): String =
        data.asText.ifEmpty { rh.gs(CoreUiR.string.autosens_chip_value, data.ratio * 100) }

    /**
     * Autosens, then the algorithm's autosens after the wizard mark when it reads differently. The same rules as the
     * overview autosens chip: the last value shown is bold.
     */
    private fun autosens(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData) return null
        val color = palette.element.autosens.toArgb()
        val autosens = currentAutosens(data)
        val algorithm = data.algorithmAutosens.takeIf { it.isNotEmpty() && it != autosens }
        val line = SpannableStringBuilder().add(autosens, color = color, bold = algorithm == null)
        algorithm?.let { line.add(" $WIZARD ").add(it, color = color, bold = true) }
        return line
    }

    /**
     * ISF from → to, then the Wizard ISF after the wizard mark when it reads differently. The same rules as the
     * overview ISF chip: the last value shown is bold.
     */
    private fun isf(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData) return null
        val color = palette.element.sensitivity.toArgb()
        val wizard = data.wizardIsf.takeIf { it.isNotEmpty() && it != data.isfTo }
        val line = SpannableStringBuilder()
        if (data.isfFrom.isNotBlank()) {
            line.add("${data.isfFrom} ${arrow(data.isfStep)} ", color = color)
            line.add(data.isfTo, color = color, bold = wizard == null)
        }
        wizard?.let {
            if (line.isNotEmpty()) line.append(" ")
            line.add("$WIZARD ").add(it, color = color, bold = true)
        }
        return line.takeIf { it.isNotEmpty() }
    }

    /**
     * The target as the overview target chip shows it: one value in bold when nothing moves it, profile → algorithm
     * target when the algorithm adjusts it to a different value, or the temp target in the colour of its reason.
     */
    private fun target(data: TempTargetDisplayData, palette: Palette): CharSequence? {
        if (data.targetRangeText.isBlank()) return null
        return when (data.state) {
            TempTargetState.NONE     -> SpannableStringBuilder().add(data.targetRangeText, bold = true)
            TempTargetState.ACTIVE   -> SpannableStringBuilder().add(data.targetRangeText, color = targetColor(data, palette), bold = true)
            TempTargetState.ADJUSTED -> {
                val color = targetColor(data, palette)
                val line = SpannableStringBuilder()
                if (data.profileTargetRangeText.isNotBlank() && data.profileTargetRangeText != data.targetRangeText)
                    line.add("${data.profileTargetRangeText} ${arrow(data.targetStep)} ", color = color)
                line.add(data.targetRangeText, color = color, bold = true)
            }
        }
    }

    /** Simple mode: the current autosens with its label, not bold, because it is not what the algorithm used. */
    private fun simpleAutosens(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData) return null
        return SpannableStringBuilder().add(rh.gs(AppR.string.notification_autosens_simple, currentAutosens(data)), color = palette.element.autosens.toArgb())
    }

    /** Simple mode: the ISF in use now with its label, not bold. Nothing when the ISF is the profile ISF unchanged. */
    private fun simpleIsf(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData || data.isfTo.isBlank()) return null
        return SpannableStringBuilder().add(rh.gs(AppR.string.notification_isf_simple, data.isfTo), color = palette.element.sensitivity.toArgb())
    }

    /** Simple mode: the target the algorithm uses, the only bold value, in the overview target chip's colour. */
    private fun simpleTarget(data: TempTargetDisplayData, palette: Palette): CharSequence? {
        if (data.targetRangeText.isBlank()) return null
        val line = SpannableStringBuilder()
        val color = if (data.state == TempTargetState.NONE) null else targetColor(data, palette)
        val text = rh.gs(AppR.string.notification_target_simple, data.targetRangeText)
        // Only the value in bold, not the target mark before it.
        val valueStart = text.indexOf(data.targetRangeText)
        line.add(text, color = color)
        if (valueStart >= 0) line.setSpan(StyleSpan(Typeface.BOLD), valueStart, valueStart + data.targetRangeText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return line
    }

    /** No colour without an adjustment, the "adjusted" colour when the algorithm moves it, else the colour of the temp target's reason. */
    private fun targetColor(data: TempTargetDisplayData, palette: Palette): Int? = when (data.state) {
        TempTargetState.NONE     -> null
        TempTargetState.ADJUSTED -> palette.general.adjusted.toArgb()
        TempTargetState.ACTIVE   -> data.reason.ttReasonColor(palette.general).toArgb()
    }

    private fun arrow(step: IsfStep): String = when (step) {
        IsfStep.UP   -> "↗"
        IsfStep.DOWN -> "↘"
        IsfStep.SAME -> "→"
    }

    private fun graph(now: Long, palette: Palette, widthPx: Int, heightPx: Int): Bitmap {
        val cache = cacheProvider()
        val margin = when (profileFunction.getUnits()) {
            GlucoseUnit.MGDL -> Y_MARGIN_MGDL
            GlucoseUnit.MMOL -> Y_MARGIN_MMOL
        }
        val fromTime = now - HISTORY_WINDOW_MS
        val bucketed = cache.bucketedDataFlow.value
        val shown = bucketed.filter { it.timestamp in fromTime..now }
        // Both marks are in the user's units, as are the readings.
        val low = preferences.get(UnitDoubleKey.OverviewLowMark)
        val high = preferences.get(UnitDoubleKey.OverviewHighMark)
        // From just under the low line to just over the high line, and further when a reading is outside them, so
        // no reading is ever drawn off the graph and no room is spent under the low line.
        val yMin = minOf(low, shown.minOfOrNull { it.value } ?: low) - margin
        val yMax = maxOf(high, shown.maxOfOrNull { it.value } ?: high) + margin
        val tempTarget = cache.tempTargetFlow.value?.takeIf { it.state == TempTargetState.ACTIVE }
        val treatments = cache.treatmentGraphFlow.value
        val input = BgGraphInput(
            bucketed = bucketed,
            fromTimeMs = fromTime,
            toTimeMs = now,
            yMinUserUnits = yMin,
            yMaxUserUnits = yMax,
            lowUserUnits = low,
            highUserUnits = high,
            targets = cache.targetLineFlow.value.targets,
            tempTargetFromMs = tempTarget?.timestamp,
            // A temp target without an end has duration 0 or is very long: clip to the graph either way.
            tempTargetToMs = tempTarget?.let { if (it.duration > 0) it.timestamp + it.duration else now },
            smbTimesMs = treatments.boluses.filter { it.isValid && it.bolusType == BolusType.SMB }.map { it.timestamp },
            bolusTimesMs = treatments.boluses.filter { it.isValid && it.bolusType != BolusType.SMB }.map { it.timestamp },
            carbsTimesMs = treatments.carbs.filter { it.isValid }.map { it.timestamp }
        )
        val colors = BgGraphColors(
            low = palette.low,
            inRange = palette.inRange,
            high = palette.high,
            lowLine = palette.lowLine,
            highLine = palette.highLine,
            target = palette.target,
            // The colour of the temp target's reason, as on the overview target chip.
            tempTarget = tempTarget?.reason.ttReasonColor(palette.general).toArgb(),
            bolus = palette.element.insulin.toArgb(),
            carbs = palette.element.carbs.toArgb()
        )
        val density = context.resources.displayMetrics.density
        return BgGraphBitmapRenderer().render(
            widthPx, heightPx, input, colors,
            dotRadiusPx = DOT_RADIUS_DP * density,
            markerSizePx = MARKER_SIZE_DP * density
        )
    }

    /** The notification's graph: nearly the screen width, 48 dp high. */
    private fun defaultGraphWidthPx(): Int = (context.resources.displayMetrics.widthPixels * GRAPH_WIDTH_FRACTION).toInt()

    private fun defaultGraphHeightPx(): Int = (GRAPH_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

    private fun isNight(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun SpannableStringBuilder.dot(): SpannableStringBuilder = append(DOT)

    private fun SpannableStringBuilder.bar(): SpannableStringBuilder = append(BAR)

    private fun SpannableStringBuilder.barIfNotEmpty(): SpannableStringBuilder = if (isEmpty()) this else bar()

    private fun SpannableStringBuilder.add(
        text: CharSequence,
        color: Int? = null,
        bold: Boolean = false,
        scale: Float? = null,
        strike: Boolean = false
    ): SpannableStringBuilder {
        val start = length
        append(text)
        color?.let { setSpan(ForegroundColorSpan(it), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        scale?.takeIf { it != 1.0f }?.let { setSpan(RelativeSizeSpan(it), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (strike) setSpan(StrikethroughSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    companion object {

        /** A reading older than this is struck through, the same limit the overview uses. */
        val STALE_AFTER_MS = T.mins(Constants.OLD_BG_MINUTES).msecs()

        private const val HISTORY_WINDOW_MS = 3L * 60 * 60 * 1000
        private const val GRAPH_HEIGHT_DP = 48
        private const val GRAPH_WIDTH_FRACTION = 0.9f
        private const val DOT_RADIUS_DP = 2.2f
        private const val MARKER_SIZE_DP = 4.0f

        /** Room above the high line and under the low line, in mg/dL and mmol/L, for the SMB marks and the dots on the lines. */
        private const val Y_MARGIN_MGDL = 10.0
        private const val Y_MARGIN_MMOL = 0.6

        /** Separates values of one group, like IOB and COB. */
        private const val DOT = "  •  "

        /** Separates groups of values. */
        private const val BAR = "  |  "

        /** The Wizard mark (abacus) before the Wizard ISF and the algorithm's autosens. Notification text cannot hold icons. */
        private const val WIZARD = "🧮"
    }
}
