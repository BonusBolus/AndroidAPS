package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.TirGraphMode
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.AgpTirBreakdown
import app.aaps.ui.compose.stats.TirBarCompose
import app.aaps.ui.compose.stats.calculateAgpTirBreakdown
import app.aaps.core.interfaces.utils.MidnightTime

@Composable
internal fun TirGraphCompose(
    viewModel: GraphViewModel,
    mode: TirGraphMode,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    modifier: Modifier = Modifier
) {
    val readings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val chartConfig by viewModel.chartConfigFlow.collectAsStateWithLifecycle()
    val profileUtil = LocalProfileUtil.current
    val breakdown = remember(
        readings,
        mode,
        derivedTimeRange,
        nowTimestamp,
        chartConfig.lowMark,
        chartConfig.highMark,
        profileUtil.units
    ) {
        calculateTir(
            readings = readings,
            mode = mode,
            derivedTimeRange = derivedTimeRange,
            nowTimestamp = nowTimestamp,
            lowMark = profileUtil.convertToMgdl(chartConfig.lowMark, profileUtil.units),
            highMark = profileUtil.convertToMgdl(chartConfig.highMark, profileUtil.units),
            profileUtil = profileUtil
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        breakdown?.let {
            TirBarCompose(
                veryLowPct = it.veryLow,
                lowPct = it.low,
                inRangePct = it.inRange,
                highPct = it.high,
                veryHighPct = it.veryHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            TirLegend(
                breakdown = it,
                lowMark = chartConfig.lowMark,
                highMark = chartConfig.highMark
            )
        } ?: Text(
            text = stringResource(UiStrings.agp_no_data),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private fun calculateTir(
    readings: List<BgDataPoint>,
    mode: TirGraphMode,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    lowMark: Double,
    highMark: Double,
    profileUtil: ProfileUtil
): AgpTirBreakdown? {
    val range = derivedTimeRange ?: return null
    val end = minOf(range.second, nowTimestamp)
    if (end <= range.first) return null
    val start = when (mode) {
        TirGraphMode.DAY -> MidnightTime.calc(end - 1L)
        TirGraphMode.HOURS_24 -> end - 24L * 60L * 60L * 1000L
    }
    val values = readings
        .asSequence()
        .filter { it.timestamp in start..end }
        .map { profileUtil.convertToMgdl(it.value, profileUtil.units) }
        .filter { it >= 39.0 }
        .toList()
    if (values.isEmpty()) return null
    return calculateAgpTirBreakdown(values, lowMark, highMark)
}

@Composable
private fun TirLegend(
    breakdown: AgpTirBreakdown,
    lowMark: Double,
    highMark: Double
) {
    val colors = AapsTheme.generalColors
    val profileUtil = LocalProfileUtil.current
    val inRange = stringResource(
        InterfacesStrings.value_with_detail,
        stringResource(CoreUiStrings.in_range),
        stringResource(
            InterfacesStrings.values_with_dash,
            profileUtil.fromMgdlToStringInUnits(profileUtil.convertToMgdl(lowMark, profileUtil.units)),
            profileUtil.fromMgdlToStringInUnits(profileUtil.convertToMgdl(highMark, profileUtil.units))
        )
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        TirLegendItem(stringResource(CoreUiStrings.veryLow), breakdown.veryLow, colors.bgVeryLow)
        TirLegendItem(stringResource(CoreUiStrings.low), breakdown.low, colors.bgLow)
        TirLegendItem(inRange, breakdown.inRange, colors.bgInRange)
        TirLegendItem(stringResource(CoreUiStrings.high), breakdown.high, colors.bgHigh)
        TirLegendItem(stringResource(CoreUiStrings.veryHigh), breakdown.veryHigh, colors.bgVeryHigh)
    }
}

@Composable
private fun RowScope.TirLegendItem(
    label: String,
    value: Double,
    color: Color
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = stringResource(UiStrings.agp_percent, value),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
