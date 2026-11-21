package app.aaps.persistentNotification

import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.keys.StringKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The notification is updated once for a burst of the frequent events, see `frequentUpdates`. */
class PersistentNotificationPluginTest : TestBase() {

    private lateinit var sut: PersistentNotificationPlugin

    @BeforeEach
    fun prepare() {
        sut = PersistentNotificationPlugin(
            aapsLogger, mock(), mock(), mock(), mock(), mock(), mock(), mock(), rxBus, mock(), mock(), mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock()
        )
    }

    /** Collects the updates in virtual time and counts them. */
    private fun TestScope.countUpdates(): () -> Int {
        var updates = 0
        val flow = sut.frequentUpdates(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { updates++ } }
        runCurrent()
        return { updates }
    }

    /**
     * An event sent right after the start, before anything reads the flow, is not lost: the sources are
     * subscribed when `frequentUpdates` is called. With `merge` they were subscribed only when read.
     */
    @Test
    fun `an event right after the start is not lost`() = runTest {
        var updates = 0
        val flow = sut.frequentUpdates(backgroundScope)
        rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = true))

        backgroundScope.launch { flow.collect { updates++ } }
        advanceTimeBy(5.seconds)
        runCurrent()

        assertThat(updates).isEqualTo(1)
    }

    @Test
    fun `a burst after a BG gives one update`() = runTest {
        val updates = countUpdates()

        // The calculation finishes twice and the overview is refreshed, within a second
        rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = true))
        advanceTimeBy(300.milliseconds)
        rxBus.send(EventRefreshOverview("test"))
        advanceTimeBy(300.milliseconds)
        rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = false))
        advanceTimeBy(5.seconds)
        runCurrent()

        assertThat(updates()).isEqualTo(1)
    }

    @Test
    fun `one event updates after the debounce time and separate events update separately`() = runTest {
        val updates = countUpdates()

        rxBus.send(EventRefreshOverview("test"))
        advanceTimeBy(PersistentNotificationPlugin.UPDATE_DEBOUNCE - 100.milliseconds)
        runCurrent()
        assertThat(updates()).isEqualTo(0)
        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertThat(updates()).isEqualTo(1)

        // The next BG, minutes later, is a new update
        advanceTimeBy(5.seconds)
        rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = true))
        advanceTimeBy(5.seconds)
        runCurrent()
        assertThat(updates()).isEqualTo(2)
    }

    /**
     * On a Samsung phone One UI draws the notification from the live-update texts and drops the normal
     * title, text and sub text. The basal rate or temp basal is only in the title and the profile name
     * only in the sub text, so both vanished from the drawer when the short Now Bar text was used there.
     */
    @Test
    fun `the samsung drawer keeps the basal and the profile, the now bar stays short`() {
        val texts = PersistentNotificationPlugin.samsungLiveTexts(
            line1 = "5.4 → +0.1 • 0.80 U/h ",
            line1WithDelta = "5.4 → +0.1",
            line2 = "IOB: 1.2U • COB: 20g",
            line3 = "Default"
        )!!

        // The profile goes on the first row: One UI has no sub text field, where it was shown before.
        assertThat(texts.primary).isEqualTo("5.4 → +0.1 • 0.80 U/h • Default")
        assertThat(texts.secondary).isEqualTo("IOB: 1.2U • COB: 20g")
        assertThat(texts.nowBarPrimary).isEqualTo("5.4 → +0.1")
        assertThat(texts.nowBarSecondary).isEqualTo("IOB: 1.2U • COB: 20g")
    }

    // "No profile set": one line only, and nothing invented for the second one.
    @Test
    fun `with one line only the samsung texts have no secondary`() {
        val texts = PersistentNotificationPlugin.samsungLiveTexts(
            line1 = "No profile set", line1WithDelta = "No profile set", line2 = null, line3 = null
        )!!

        assertThat(texts.primary).isEqualTo("No profile set")
        assertThat(texts.secondary).isNull()
        assertThat(texts.nowBarSecondary).isNull()
    }

    @Test
    fun `nothing to show gives no samsung texts`() {
        assertThat(PersistentNotificationPlugin.samsungLiveTexts(null, null, null, null)).isNull()
        assertThat(PersistentNotificationPlugin.samsungLiveTexts("x", " ", "y", "z")).isNull()
    }

    /** Every stored value maps to a mode. The receiver runs on the main thread, so an unknown value must not throw. */
    @Test
    fun `stored status modes map to a mode and unknown values give null`() {
        assertThat(PersistentNotificationPlugin.statusModeOf("chip")).isEqualTo(PersistentNotificationPlugin.StatusMode.CHIP)
        assertThat(PersistentNotificationPlugin.statusModeOf("icon")).isEqualTo(PersistentNotificationPlugin.StatusMode.ICON)
        assertThat(PersistentNotificationPlugin.statusModeOf("graph_locked")).isEqualTo(PersistentNotificationPlugin.StatusMode.GRAPH_LOCKED)
        assertThat(PersistentNotificationPlugin.statusModeOf("standard")).isEqualTo(PersistentNotificationPlugin.StatusMode.STANDARD)
        // A mode of an older test build
        assertThat(PersistentNotificationPlugin.statusModeOf("chip_locked")).isEqualTo(PersistentNotificationPlugin.StatusMode.CHIP)
        assertThat(PersistentNotificationPlugin.statusModeOf("something_else")).isNull()
        assertThat(PersistentNotificationPlugin.statusModeOf("")).isNull()
    }

    @Test
    fun `only graph_locked depends on the lock state`() {
        for (locked in listOf(true, false)) {
            assertThat(PersistentNotificationPlugin.lookFor(PersistentNotificationPlugin.StatusMode.CHIP, locked)).isEqualTo(PersistentNotificationPlugin.Look.CHIP_CARD)
            assertThat(PersistentNotificationPlugin.lookFor(PersistentNotificationPlugin.StatusMode.ICON, locked)).isEqualTo(PersistentNotificationPlugin.Look.GRAPH)
            assertThat(PersistentNotificationPlugin.lookFor(PersistentNotificationPlugin.StatusMode.STANDARD, locked)).isEqualTo(PersistentNotificationPlugin.Look.STANDARD)
        }
        assertThat(PersistentNotificationPlugin.lookFor(PersistentNotificationPlugin.StatusMode.GRAPH_LOCKED, true)).isEqualTo(PersistentNotificationPlugin.Look.GRAPH_LOCK_SCREEN)
        assertThat(PersistentNotificationPlugin.lookFor(PersistentNotificationPlugin.StatusMode.GRAPH_LOCKED, false)).isEqualTo(PersistentNotificationPlugin.Look.CHIP_MINIMAL)
    }

    /** Every choice of the setting in the preference list must be a known mode. */
    @Test
    fun `every mode in the preference list is known`() {
        val choices = StringKey.NotificationStatusMode.entries.keys
        assertThat(choices).isNotEmpty()
        for (value in choices) assertThat(PersistentNotificationPlugin.statusModeOf(value)).isNotNull()
        assertThat(PersistentNotificationPlugin.statusModeOf(StringKey.NotificationStatusMode.defaultValue)).isNotNull()
    }
}
