package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.PS
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent

    @BeforeEach
    fun setup() {
        runBlocking { whenever(profileFunction.isProfileChangePending()).thenReturn(false) }
        sut = ActionProfileSwitchPercent(aapsLogger, text, { pumpEnactResultProvider() }, profileFunction, triggerDeps)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.profilepercentage)
    }

    @Test fun shortDescriptionTest() = runTest {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.shortDescription()).isEqualTo("Start profile 100% for 30 min")
    }

    @Test fun permanentSwitchTest() = runTest {
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mock<PS>())
        sut.pct = InputPercent(90.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.permanent = true
        assertThat(sut.isValid()).isTrue()
        assertThat(sut.shortDescription()).isEqualTo("Start profile 90%")
        assertThat(sut.doAction().success).isTrue()
        // A permanent switch passes duration 0, also when a duration is still set.
        verify(profileFunction, times(1)).createProfileSwitch(eq(0), eq(90), eq(0), any(), any(), any(), any(), eq(0L))
    }

    /** The running profile in the test base is 100%. */
    @Test fun temporarySwitchOnlyStartsFrom100Percent() = runTest {
        sut.pct = InputPercent(120.0)
        assertThat(sut.precondition!!.shouldRun()).isTrue()
    }

    /** A permanent switch to the percentage that already runs must not fire again every loop run. */
    @Test fun permanentSwitchDoesNotRunWhenPercentageIsTheSame() = runTest {
        sut.permanent = true
        sut.pct = InputPercent(100.0)
        assertThat(sut.precondition!!.shouldRun()).isFalse()
        sut.pct = InputPercent(90.0)
        assertThat(sut.precondition!!.shouldRun()).isTrue()
        sut.pct = InputPercent(110.0)
        assertThat(sut.precondition!!.shouldRun()).isTrue()
    }

    @Test fun durationMustBeAtLeastOneMinuteUnlessPermanent() = runTest {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.isValid()).isFalse()
        sut.duration = InputDuration(1, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.isValid()).isTrue()
        sut.duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)
        sut.permanent = true
        assertThat(sut.isValid()).isTrue()
    }

    /** A rule with no duration in its JSON must be invalid, not silently permanent. */
    @Test fun missingDurationIsNotPermanent() = runTest {
        sut.fromJSON("""{"percentage":110}""")
        assertThat(sut.permanent).isFalse()
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun doActionTest() = runTest {
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mock<PS>())
        sut.pct = InputPercent(110.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        // The last matcher is the timestamp: an automation rule fires at its trigger time, so it passes 0 = now.
        verify(profileFunction, times(1)).createProfileSwitch(eq(30), eq(110), eq(0), any(), any(), any(), any(), eq(0L))
    }

    /** A refused switch says why in the automation log, not "OK" next to a failure. */
    @Test fun aRefusedSwitchSaysWhy() = runTest {
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null)
        whenever(profileFunction.profileSwitchRefusal(eq(30), eq(150), eq(0), any())).thenReturn("The pump cannot take it")
        sut.pct = InputPercent(150.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

        val result = sut.doAction()

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("The pump cannot take it")
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        JSONAssert.assertEquals("""{"data":{"percentage":100,"durationInMinutes":30,"permanent":false},"type":"ActionProfileSwitchPercent"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"percentage":100,"durationInMinutes":30}""")
        assertThat(sut.pct.value).isWithin(0.001).of(100.0)
        assertThat(sut.duration.getMinutes().toDouble()).isWithin(0.001).of(30.0)
        assertThat(sut.permanent).isFalse()
        sut.fromJSON("""{"percentage":90,"durationInMinutes":30,"permanent":true}""")
        assertThat(sut.permanent).isTrue()
    }
}
