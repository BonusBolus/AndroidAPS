package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.extensions.displayText
import app.aaps.ui.UiStrings
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Stable
@AssistedInject
class ChipsViewModel(
    @Assisted cache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val sensitivityOverview: SensitivityOverview,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val processedDeviceStatusData: ProcessedDeviceStatusData
) : ViewModel() {

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): ChipsViewModel
    }

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val iobUiState: StateFlow<IobUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        IobUiState(
            text = rh.gs(InterfacesStrings.format_insulin_units, total),
            iobTotal = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IobUiState()
    )

    val cobUiState: StateFlow<CobUiState> = iobCobTicker.combine(cache.cobGraphFlow) { _, _ ->
        val cobInfo = iobCobCalculator.getCobInfo("ChipsViewModel COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter)
            ?: rh.gs(CoreUiStrings.value_unavailable_short)
        var carbsReq = 0

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText = rh.gs(CoreUiStrings.value_with_unit, cobText, rh.gs(CoreUiStrings.value_with_unit, constraintsProcessed.carbsReq.toString(), rh.gs(CoreUiStrings.required)))
                }
                carbsReq = constraintsProcessed.carbsReq
            }
        }

        CobUiState(text = cobText, carbsReq = carbsReq, cobValue = cobInfo.displayCob ?: 0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CobUiState()
    )

    // The IOB graph is published before the loop runs in the same calculation chain, so on its
    // own it would show the previous loop's ratio and variable ISF. Predictions are published
    // right after the loop ran on the master, and right after a device status came in on a
    // client, so they carry the fresh values on both sides.
    val sensitivityUiState: StateFlow<SensitivityUiState> = combine(iobCobTicker, cache.iobGraphFlow, cache.predictionsFlow) { _, _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    // Predictions are published right after a loop run on the master and after a device status on a client,
    // so they mark a new loop result. The ticker keeps the age right in between.
    val loopUiState: StateFlow<LoopUiState> = combine(minuteTicker, cache.predictionsFlow) { _, _ ->
        buildLoopUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoopUiState()
    )

    private fun buildLoopUiState(): LoopUiState {
        // The master's own last run; a client gets the master's from the device status.
        val (runTime, result) = when {
            config.APS        -> loop.lastRun?.let { it.lastAPSRun to it.request?.rawData() as? RT } ?: (null to null)
            config.AAPSCLIENT -> processedDeviceStatusData.openAPSData.let { it.clockSuggested.takeIf { time -> time > 0 } to it.suggested }
            else              -> null to null
        }
        val ageMinutes = runTime?.let { ((dateUtil.now() - it) / MINUTE_MS).coerceAtLeast(0L) }
        val eventualBg = result?.eventualBG?.takeIf { it.isFinite() && it > 0.0 }
        val settles = runTime?.let { predictionEnd(result, it) }
        return LoopUiState(
            ageText = ageMinutes?.let { rh.gs(UiStrings.loop_age_minutes, it.toInt()) } ?: "",
            ageStale = ageMinutes != null && ageMinutes >= LOOP_STALE_MINUTES,
            eventualBgText = eventualBg?.let { profileUtil.fromMgdlToStringInUnits(it) } ?: "",
            eventualBgTime = settles?.let { dateUtil.timeString(it) } ?: ""
        )
    }

    /**
     * About when the eventual BG is reached. The algorithm gives the eventual BG without a time, so this is the
     * end of the longest prediction line: oref cuts a line off where it stops changing. The zero-temp line is a
     * "what if" and is left out. Null when the run has no prediction lines.
     */
    private fun predictionEnd(result: RT?, runTime: Long): Long? {
        val predictions = result?.predBGs ?: return null
        val points = listOfNotNull(predictions.IOB, predictions.COB, predictions.UAM, predictions.aCOB).maxOfOrNull { it.size } ?: return null
        if (points < 2) return null
        return (result.timestamp ?: runTime) + (points - 1) * PREDICTION_STEP_MS
    }

    private suspend fun buildSensitivityUiState(): SensitivityUiState {
        // Worked out in one place for the chip, its dialog and the watch - see SensitivityOverview
        val data = sensitivityOverview.build()
        return SensitivityUiState(
            asText = data.asText,
            algorithmAutosens = data.algorithmAutosens,
            isfFrom = data.isfFrom,
            isfTo = data.isfTo,
            wizardIsf = data.wizardIsf,
            isfStep = data.isfStep,
            dialogText = data.lines.joinToString("\n"),
            ratio = data.ratio,
            isEnabled = data.isEnabled,
            hasData = data.hasData
        )
    }

    fun showIobInfo() {
        viewModelScope.launch {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val total = bolusIob.iob + basalIob.basaliob
            val message = listOf(
                CoreUiStrings.bolus_iob_label to bolusIob.iob,
                CoreUiStrings.treatments_wizard_basaliob_label to basalIob.basaliob,
                CoreUiStrings.iob to total
            ).joinToString("\n") { (label, insulin) ->
                rh.gs(InterfacesStrings.confirmation_line, rh.gs(label), rh.gs(InterfacesStrings.format_insulin_units, insulin))
            }
            rxBus.send(
                EventShowDialog.Ok(
                    title = rh.gs(CoreUiStrings.iob),
                    message = message
                )
            )
        }
    }
}

private const val MINUTE_MS = 60_000L

/** The prediction lines have one point every 5 minutes. */
private const val PREDICTION_STEP_MS = 5 * MINUTE_MS

/** A loop runs every 5 minutes. After 3 missed runs the age turns red. */
private const val LOOP_STALE_MINUTES = 15L
