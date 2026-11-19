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
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.SensitivityOverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
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
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.ElementColors
import app.aaps.core.ui.compose.navigation.LightElementColors
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
 * SMB state, IOB, COB, profile, sensitivity and the glucose graph - as a coloured text card (for the live
 * chip modes, where Android allows no custom views) or as a custom [RemoteViews] layout with the graph.
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
        val smb: CharSequence?,
        val values: CharSequence,
        val sensitivity: CharSequence?,
        val graph: Bitmap?
    )

    /** Colours that read on the notification background, which follows the system light or dark mode. */
    private class Palette(night: Boolean) {

        val element: ElementColors = if (night) DarkElementColors else LightElementColors
        val low = 0xFFE53935.toInt()
        val inRange = if (night) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
        val high = if (night) 0xFFFFCA28.toInt() else 0xFFF57F17.toInt()
        val limitLine = if (night) 0x88FFFFFF.toInt() else 0x88000000.toInt()
        val target = if (night) 0xFFB0BEC5.toInt() else 0xFF607D8B.toInt()
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
        val iobText = rh.gs(CoreUiR.string.value_with_unit, rh.gs(CoreUiR.string.treatments_iob_label_string), rh.gs(MainR.string.notification_iob_short, bolusIob.iob + basalIob.basaliob))
        val cobText = rh.gs(InterfacesR.string.confirmation_line, rh.gs(CoreUiR.string.cob), iobCobCalculator.getCobInfo("DetailedStatusRenderer").generateCOBString(decimalFormatter))
        val percentage = profileFunction.getProfile()?.percentage ?: 100
        val profileText = profileFunction.getProfileName() + if (percentage != 100) " $percentage%" else ""
        val values = SpannableStringBuilder()
            .add(iobText, color = palette.element.insulin)
            .add("  •  ")
            .add(cobText, color = palette.element.cob)
            .add("  •  ")
            .add(profileText, color = palette.element.profileSwitch)

        val smbMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb)
        // Like the overview chip: nothing to show without a limit, red when SMB is switched off.
        val smb = when {
            smbMinutes <= 0                           -> null
            preferences.get(BooleanKey.ApsUseSmb)     -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb_on, smbMinutes))
            else                                      -> SpannableStringBuilder().add(rh.gs(AppR.string.notification_smb_off), color = palette.low, bold = true)
        }

        return Status(
            bgText = profileUtil.fromMgdlToStringInUnits(lastBg.recalculated),
            bgColor = bgColor,
            trendSymbol = trendSymbol,
            deltaText = deltaText,
            readingTimestamp = lastBg.timestamp,
            isStale = now - lastBg.timestamp > STALE_AFTER_MS,
            smb = smb,
            values = values,
            sensitivity = sensitivityLine(sensitivityOverview.build(), palette),
            graph = if (withGraph) graph(now, palette) else null
        )
    }

    /** The text card for the live chip modes. */
    fun card(status: Status): CharSequence {
        val card = headline(status, bgScale = 1.8f, deltaScale = 0.67f)
        status.smb?.let { card.append("    ").append(it) }
        card.append("\n").append(status.values)
        status.sensitivity?.let { card.append("\n").append(it) }
        return card
    }

    /** The custom layout, used as collapsed and expanded content alike. */
    fun remoteViews(status: Status): RemoteViews =
        RemoteViews(context.packageName, AppR.layout.notification_status_detailed).apply {
            setTextViewText(AppR.id.status_bg, headline(status, bgScale = 1.0f, deltaScale = 0.65f))
            // Ticks by itself, so the age is right without a notification update every minute.
            setChronometer(AppR.id.status_age, SystemClock.elapsedRealtime() - (dateUtil.now() - status.readingTimestamp), null, true)
            if (status.smb != null) setTextViewText(AppR.id.status_smb, status.smb)
            else setViewVisibility(AppR.id.status_smb, View.GONE)
            status.graph?.let { setImageViewBitmap(AppR.id.status_graph, it) }
            setTextViewText(AppR.id.status_values, status.values)
            if (status.sensitivity != null) setTextViewText(AppR.id.status_sensitivity, status.sensitivity)
            else setViewVisibility(AppR.id.status_sensitivity, View.GONE)
        }

    /** Glucose, trend and delta, struck through together when the reading is stale. */
    private fun headline(status: Status, bgScale: Float, deltaScale: Float): SpannableStringBuilder {
        val line = SpannableStringBuilder()
            .add(status.bgText, color = status.bgColor, bold = true, scale = bgScale)
        status.trendSymbol?.let { line.add(" $it", color = status.bgColor, bold = true, scale = bgScale) }
        status.deltaText?.let { line.add("  $it", scale = bgScale * deltaScale) }
        if (status.isStale) line.setSpan(StrikethroughSpan(), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return line
    }

    private fun sensitivityLine(data: SensitivityOverviewData, palette: Palette): CharSequence? {
        if (!data.hasData) return null
        val line = SpannableStringBuilder()
        val isfColor = if (data.isVariableIsf) palette.element.scene else palette.element.sensitivity
        if (data.asText.isNotBlank()) line.add(data.asText, color = palette.element.sensitivity)
        if (data.isfFrom.isNotBlank() || data.isfTo.isNotBlank()) {
            if (line.isNotEmpty()) line.add("  ")
            val arrow = when (data.isfStep) {
                IsfStep.UP   -> "↗"
                IsfStep.DOWN -> "↘"
                IsfStep.SAME -> "→"
            }
            line.add("ISF ${data.isfFrom}$arrow", color = isfColor)
            line.add(data.isfTo, color = isfColor, bold = true)
        }
        // The ISF the bolus wizard and carb absorption work with.
        if (data.wizardIsf.isNotBlank()) {
            if (line.isNotEmpty()) line.add("  ")
            line.add("(${data.wizardIsf})", color = palette.element.sensitivity, bold = true)
        }
        return line.takeIf { it.isNotEmpty() }
    }

    private fun graph(now: Long, palette: Palette): Bitmap {
        val cache = cacheProvider()
        val units = profileFunction.getUnits()
        val (yMinFloor, yMaxFloor) = when (units) {
            GlucoseUnit.MGDL -> Y_MIN_MGDL to Y_MAX_MGDL
            GlucoseUnit.MMOL -> Y_MIN_MMOL to Y_MAX_MMOL
        }
        val fromTime = now - HISTORY_WINDOW_MS
        val bucketed = cache.bucketedDataFlow.value
        val dataMax = bucketed.asSequence().filter { it.timestamp in fromTime..now }.maxOfOrNull { it.value } ?: yMaxFloor
        val tempTarget = cache.tempTargetFlow.value?.takeIf { it.state == TempTargetState.ACTIVE }
        val input = BgGraphInput(
            bucketed = bucketed,
            fromTimeMs = fromTime,
            toTimeMs = now,
            yMinUserUnits = yMinFloor,
            yMaxUserUnits = maxOf(yMaxFloor, dataMax + dataMax * 0.05),
            lowUserUnits = preferences.get(UnitDoubleKey.OverviewLowMark),
            highUserUnits = preferences.get(UnitDoubleKey.OverviewHighMark),
            targets = cache.targetLineFlow.value.targets,
            tempTargetFromMs = tempTarget?.timestamp,
            // A temp target without an end has duration 0 or is very long: clip to the graph either way.
            tempTargetToMs = tempTarget?.let { if (it.duration > 0) it.timestamp + it.duration else now }
        )
        val colors = BgGraphColors(
            low = palette.low,
            inRange = palette.inRange,
            high = palette.high,
            limitLine = palette.limitLine,
            target = palette.target,
            tempTarget = palette.element.tempTarget.toArgb()
        )
        val density = context.resources.displayMetrics.density
        val widthPx = (context.resources.displayMetrics.widthPixels * GRAPH_WIDTH_FRACTION).toInt()
        return BgGraphBitmapRenderer().render(widthPx, (GRAPH_HEIGHT_DP * density).toInt(), input, colors)
    }

    private fun palette(): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Palette(night)
    }

    private fun SpannableStringBuilder.add(
        text: CharSequence,
        color: Int? = null,
        bold: Boolean = false,
        scale: Float? = null
    ): SpannableStringBuilder {
        val start = length
        append(text)
        color?.let { setSpan(ForegroundColorSpan(it), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        scale?.takeIf { it != 1.0f }?.let { setSpan(RelativeSizeSpan(it), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return this
    }

    companion object {

        /** A reading older than this is struck through. */
        const val STALE_AFTER_MS = 5L * 60 * 1000

        private const val HISTORY_WINDOW_MS = 3L * 60 * 60 * 1000
        private const val GRAPH_HEIGHT_DP = 36
        private const val GRAPH_WIDTH_FRACTION = 0.9f

        private const val Y_MIN_MGDL = 40.0
        private const val Y_MAX_MGDL = 220.0
        private const val Y_MIN_MMOL = 2.2
        private const val Y_MAX_MMOL = 12.2
    }
}
