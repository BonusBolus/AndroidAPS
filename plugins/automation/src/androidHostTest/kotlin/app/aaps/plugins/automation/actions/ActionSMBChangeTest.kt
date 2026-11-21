package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"smbState":true,"toggle":false},"type":"ActionSMBChange"}"""

class ActionSMBChangeTest : ActionsTestBase() {

    private lateinit var sut: ActionSMBChange

    @BeforeEach fun setUp() {

        sut = ActionSMBChange(aapsLogger, text, { pumpEnactResultProvider() }, dateUtil, preferences, notificationManager)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.changeSmbState)
    }

    @Test fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Change SMB to ON")
    }

    @Test fun doAction() = runTest {
        sut.smbState = InputDropdownOnOffMenu(text, true)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("OK")
        verify(preferences, times(1)).put(any<BooleanPreferenceKey>(), anyBoolean())
    }

    @Test fun toggleShortDescriptionTest() = runTest {
        sut.toggle = true
        assertThat(sut.shortDescription()).isEqualTo("Toggle SMB")
    }

    @Test fun doActionToggleTurnsOffWhenOn() = runTest {
        whenever(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(true)
        sut.toggle = true
        assertThat(sut.doAction().success).isTrue()
        verify(preferences, times(1)).put(BooleanKey.ApsUseSmb, false)
    }

    @Test fun doActionToggleTurnsOnWhenOff() = runTest {
        whenever(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        sut.toggle = true
        assertThat(sut.doAction().success).isTrue()
        verify(preferences, times(1)).put(BooleanKey.ApsUseSmb, true)
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.smbState = InputDropdownOnOffMenu(text, true)
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"smbState":"false"}""")
        assertThat(sut.smbState.value).isEqualTo(false)
        assertThat(sut.toggle).isFalse()
    }

    /** SMB settings are fixed in simple mode, so the action must refuse and tell the user. */
    @Test fun doActionRefusesInSimpleMode() = runTest {
        whenever(preferences.simpleMode).thenReturn(true)
        sut.toggle = true
        val result = sut.doAction()
        assertThat(result.success).isFalse()
        verify(preferences, never()).put(any<BooleanPreferenceKey>(), anyBoolean())
        verify(notificationManager).post(eq(NotificationId.AUTOMATION_MESSAGE), any<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
    }

    /** Old test builds stored the state as text. "Off" must not turn into ON. */
    @Test fun fromJSONReadsOldTextState() = runTest {
        sut.fromJSON("""{"smbState":"Off"}""")
        assertThat(sut.smbState.value).isFalse()
        assertThat(sut.toggle).isFalse()
        sut.fromJSON("""{"smbState":"Toggle"}""")
        assertThat(sut.toggle).isTrue()
        sut.fromJSON("""{"smbState":"On"}""")
        assertThat(sut.smbState.value).isTrue()
        assertThat(sut.toggle).isFalse()
    }

    @Test fun fromJSONToggleTest() = runTest {
        sut.fromJSON("""{"smbState":true,"toggle":true}""")
        assertThat(sut.toggle).isTrue()
    }
}
