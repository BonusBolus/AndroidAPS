package app.aaps.plugins.automation.actions

import app.aaps.core.keys.IntKey
import app.aaps.plugins.automation.AutomationStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"minutes":30},"type":"ActionSMBMaxMinutesChange"}"""

class ActionSMBMaxMinutesChangeTest : ActionsTestBase() {

    private lateinit var sut: ActionSMBMaxMinutesChange

    @BeforeEach fun setUp() {
        sut = ActionSMBMaxMinutesChange(aapsLogger, text, { pumpEnactResultProvider() }, preferences, notificationManager)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.changeSmbMaxMinutes)
    }

    @Test fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Change SMB max minutes to 30 min")
    }

    @Test fun doAction() = runTest {
        sut.minutes.value = 45
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("OK")
        verify(preferences, times(1)).put(IntKey.ApsMaxMinutesOfBasalToLimitSmb, 45)
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun isValidTest() = runTest {
        assertThat(sut.isValid()).isTrue()
        sut.minutes.value = 10
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun toJSONTest() = runTest {
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"minutes":45}""")
        assertThat(sut.minutes.value).isEqualTo(45)
    }

    /** In simple mode the value would only be used later, so the action must refuse. */
    @Test fun doActionRefusesInSimpleMode() = runTest {
        whenever(preferences.simpleMode).thenReturn(true)
        sut.minutes.value = 45
        assertThat(sut.doAction().success).isFalse()
        verify(preferences, never()).put(IntKey.ApsMaxMinutesOfBasalToLimitSmb, 45)
    }
}
