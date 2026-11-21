package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.TirGraphMode
import app.aaps.core.interfaces.utils.MidnightTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The window and the filters of the TIR graph, see `calculateTir`. Readings are in mg/dL here. */
class TirCalculationTest {

    private val hour = 60L * 60L * 1000L
    private val now = MidnightTime.calc(1_700_000_000_000L) + 10 * hour

    private fun reading(timestamp: Long, value: Double) = BgDataPoint(timestamp, value, BgRange.IN_RANGE, BgType.REGULAR)

    private fun tir(readings: List<BgDataPoint>, mode: TirGraphMode, range: Pair<Long, Long>? = (now - 30 * hour) to now) =
        calculateTir(readings, mode, range, now, lowMark = 70.0, highMark = 180.0, toMgdl = { it })

    @Test
    fun dayStartsAtMidnight() {
        // One reading before midnight (high), one after (in range)
        val readings = listOf(reading(MidnightTime.calc(now) - hour, 250.0), reading(MidnightTime.calc(now) + hour, 100.0))
        val result = tir(readings, TirGraphMode.DAY)!!
        assertEquals(100.0, result.inRange)
        assertEquals(0.0, result.high)
    }

    @Test
    fun hours24CountsTheLast24Hours() {
        val readings = listOf(reading(now - 25 * hour, 250.0), reading(now - 23 * hour, 250.0), reading(now - hour, 100.0))
        val result = tir(readings, TirGraphMode.HOURS_24)!!
        assertEquals(50.0, result.inRange)
        assertEquals(50.0, result.high)
    }

    /** In the history browser the range can end in the future (today); the window must stop at now. */
    @Test
    fun windowNeverGoesPastNow() {
        val readings = listOf(reading(now - hour, 100.0), reading(now + hour, 250.0))
        val result = tir(readings, TirGraphMode.HOURS_24, range = (now - 24 * hour) to (now + 5 * hour))!!
        assertEquals(100.0, result.inRange)
    }

    /** A day in the past in the history browser: the window ends at the end of the shown range. */
    @Test
    fun pastRangeUsesItsOwnEnd() {
        val end = now - 48 * hour
        val readings = listOf(reading(end - hour, 250.0), reading(now - hour, 100.0))
        val result = tir(readings, TirGraphMode.HOURS_24, range = (end - 24 * hour) to end)!!
        assertEquals(100.0, result.high)
    }

    @Test
    fun invalidReadingsAreLeftOut() {
        val readings = listOf(reading(now - hour, 38.0), reading(now - hour, 100.0))
        assertEquals(100.0, tir(readings, TirGraphMode.HOURS_24)!!.inRange)
    }

    @Test
    fun noReadingsOrNoRangeGiveNull() {
        assertNull(tir(emptyList(), TirGraphMode.DAY))
        assertNull(tir(listOf(reading(now - hour, 100.0)), TirGraphMode.DAY, range = null))
        // A range that ends before it starts
        assertNull(tir(listOf(reading(now - hour, 100.0)), TirGraphMode.DAY, range = now to now - hour))
    }

    @Test
    fun tightIsPartOfInRange() {
        val readings = listOf(reading(now - 3 * hour, 100.0), reading(now - 2 * hour, 160.0), reading(now - hour, 200.0))
        val result = tir(readings, TirGraphMode.HOURS_24)!!
        assertEquals(200.0 / 3, result.inRange, 0.001)
        assertEquals(100.0 / 3, result.tight, 0.001)
    }
}
