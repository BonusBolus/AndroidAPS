package app.aaps.ui.compose.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the SMB chip shows: both the constraints and the last loop run must allow SMB. */
class SmbStatusTest {

    @Test
    fun constraintsAndAlgorithmAllowSmb() {
        val status = smbStatusOf(true, "", "SMB enabled for COB of 30")
        assertTrue(status.allowed)
        assertEquals(listOf("SMB enabled for COB of 30"), status.reasons)
    }

    @Test
    fun aConstraintBlocksSmb() {
        val status = smbStatusOf(false, "SMB disabled in preferences\nObjective 9 not started", null)
        assertFalse(status.allowed)
        assertEquals(listOf("SMB disabled in preferences", "Objective 9 not started"), status.reasons)
    }

    /** The preference can be on while the algorithm switched SMB off for this loop run. */
    @Test
    fun theAlgorithmBlocksSmb() {
        val status = smbStatusOf(true, "", "SMB disabled due to high temptarget of 160")
        assertFalse(status.allowed)
        assertEquals(listOf("SMB disabled due to high temptarget of 160"), status.reasons)
    }

    @Test
    fun beforeTheFirstLoopRunOnlyTheConstraintsCount() {
        assertTrue(smbStatusOf(true, "", null).allowed)
    }

    @Test
    fun theDecisionIsTheLastSmbLineOfTheRun() {
        val lines = listOf("Autosens ratio: 1.0", "SMB disabled (!microBolusAllowed)", "SMB enabled for COB of 12", "maxBolus: 1.2")
        assertEquals("SMB enabled for COB of 12", smbDecisionOf(lines))
        assertNull(smbDecisionOf(listOf("Autosens ratio: 1.0")))
        assertNull(smbDecisionOf(null))
    }
}
