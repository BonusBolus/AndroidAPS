package app.aaps.persistentNotification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
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
import app.aaps.core.ui.R as CoreUiR

/**
 * Everything the ongoing status notification shows besides the plain lines: glucose, trend, delta, age,
 * SMB state, IOB, COB, profile, sensitivity, target and the glucose graph - as a coloured text card (for the
 * live chip, where Android allows no custom views) or as a custom [RemoteViews] layout with the graph.
 *
 * The values carry no labels. Their colours say what they are, the same colours as in the overview. The rules
 * for which values are shown and which one is bold are the overview chips' rules.
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
        /** IOB • COB • SMB: the first line of the chip card and the right part of the graph layout's top row */
        val cardValues: CharSequence,
        /** Chip card, second line: profile • autosens • ISF */
        val cardSensitivity: CharSequence,
        /** Line under the graph: ISF • autosens • target */
        val graphValues: CharSequence,
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
     */
    suspend fun build(withGraph: Boolean): Status? {
        if (!profileFunction.isProfileValid("Notification")) return null
        val lastBg = lastBgData.lastBg() ?: return null
        val now = dateUtil.now()
        val palette = palette()

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

        val smbMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb)
        // Like the overview chip: nothing to show without a limit. Blue when on, red and struck through when off.
        val smb = when {
            smbMinutes <= 0                       -> null
            preferences.get(BooleanKey.ApsUseSmb) -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb, smbMinutes), color = palette.element.insulin.toArgb())
            else                                  -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb, smbMinutes), color = palette.low, strike = true)
        }
        val sensitivity = sensitivityOverview.build()

        val cardValues = SpannableStringBuilder()
            .add(iob, color = palette.element.insulin.toArgb())
            .dot()
            .add(cob, color = palette.element.cob.toArgb())
        smb?.let { cardValues.dot().append(it) }

        val cardSensitivity = SpannableStringBuilder().add(profile, color = palette.element.profileSwitch.toArgb())
        autosens(sensitivity, palette)?.let { cardSensitivity.dot().append(it) }
        isf(sensitivity, palette)?.let { cardSensitivity.dot().append(it) }

        // IOB, COB and SMB are on the top row of the graph layout, so this line holds the rest.
        val graphValues = SpannableStringBuilder()
        isf(sensitivity, palette)?.let { graphValues.append(it) }
        autosens(sensitivity, palette)?.let { graphValues.dotIfNotEmpty().append(it) }
        cacheProvider().tempTargetFlow.value?.let { target(it, palette) }?.let { graphValues.dotIfNotEmpty().append(it) }

        return Status(
            bgText = profileUtil.fromMgdlToStringInUnits(lastBg.recalculated),
            bgColor = bgColor,
            trendSymbol = trendSymbol,
            deltaText = deltaText,
            readingTimestamp = lastBg.timestamp,
            // The same limit the overview uses to strike the glucose through.
            isStale = !lastBgData.isActualBg(),
            cardValues = cardValues,
            cardSensitivity = cardSensitivity,
            graphValues = graphValues,
            graph = if (withGraph) graph(now, palette) else null
        )
    }

    /**
     * The notification title for the live chip: the coloured glucose, trend and delta, struck through together
     * when the reading is stale, then [suffix] (the basal rate) as it is.
     */
    fun title(status: Status, suffix: String?): CharSequence {
        val title = headline(status, deltaScale = 1.0f)
        suffix?.let { title.append(it) }
        return title
    }

    /** The two lines of the live chip card under the title. */
    fun card(status: Status): CharSequence =
        SpannableStringBuilder(status.cardValues).append("\n").append(status.cardSensitivity)

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
            if (status.graphValues.isNotEmpty()) setTextViewText(AppR.id.status_values, status.graphValues)
            else setViewVisibility(AppR.id.status_values, View.GONE)
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

    /**
     * Autosens, then the algorithm's autosens after the wizard mark when it reads differently. The same rules as the
     * overview autosens chip: always a number, also at 100 %, and the last value shown is bold.
     */
    private fun autosens(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData) return null
        val color = palette.element.autosens.toArgb()
        // Empty at exactly 100 %: then the ratio itself is the number, as on the chip.
        val autosens = data.asText.ifEmpty { rh.gs(CoreUiR.string.autosens_chip_value, data.ratio * 100) }
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
            TempTargetState.ACTIVE   -> SpannableStringBuilder().add(data.targetRangeText, color = data.reason.ttReasonColor(palette.general).toArgb(), bold = true)
            TempTargetState.ADJUSTED -> {
                val color = palette.general.adjusted.toArgb()
                val line = SpannableStringBuilder()
                if (data.profileTargetRangeText.isNotBlank() && data.profileTargetRangeText != data.targetRangeText)
                    line.add("${data.profileTargetRangeText} ${arrow(data.targetStep)} ", color = color)
                line.add(data.targetRangeText, color = color, bold = true)
            }
        }
    }

    private fun arrow(step: IsfStep): String = when (step) {
        IsfStep.UP   -> "↗"
        IsfStep.DOWN -> "↘"
        IsfStep.SAME -> "→"
    }

    private fun graph(now: Long, palette: Palette): Bitmap {
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
        val widthPx = (context.resources.displayMetrics.widthPixels * GRAPH_WIDTH_FRACTION).toInt()
        return BgGraphBitmapRenderer().render(
            widthPx, (GRAPH_HEIGHT_DP * density).toInt(), input, colors,
            dotRadiusPx = DOT_RADIUS_DP * density,
            markerSizePx = MARKER_SIZE_DP * density
        )
    }

    private fun palette(): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Palette(night)
    }

    private fun SpannableStringBuilder.dot(): SpannableStringBuilder = append("  •  ")

    private fun SpannableStringBuilder.dotIfNotEmpty(): SpannableStringBuilder = if (isEmpty()) this else dot()

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

        /** The Wizard mark (abacus) before the Wizard ISF and the algorithm's autosens. Notification text cannot hold icons. */
        private const val WIZARD = "🧮"
    }
}
