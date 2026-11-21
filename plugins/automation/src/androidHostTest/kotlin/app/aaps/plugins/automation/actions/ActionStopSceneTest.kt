package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationResult
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

class ActionStopSceneTest : ActionsTestBase() {

    private lateinit var sut: ActionStopScene
    private val exercise = Scene(id = "scene-1", name = "Exercise", isEnabled = true)

    @BeforeEach fun setUp() {
        sut = ActionStopScene(aapsLogger, text, { pumpEnactResultProvider() }, sceneApi, triggerDeps)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.action_stop_scene)
    }

    @Test fun shortDescription() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Stop scene")
        sut.runFollowUp = true
        assertThat(sut.shortDescription()).isEqualTo("Stop scene, run follow-up scene")
    }

    @Test fun doActionStopsOnlyByDefault() = runTest {
        whenever(sceneApi.stopActiveScene()).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).stopActiveScene()
        verify(sceneApi, never()).stopActiveSceneAndChain()
    }

    @Test fun doActionRunsFollowUpWhenChosen() = runTest {
        sut.runFollowUp = true
        whenever(sceneApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).stopActiveSceneAndChain()
        verify(sceneApi, never()).stopActiveScene()
    }

    @Test fun doActionFailsWhenStopFails() = runTest {
        whenever(sceneApi.stopActiveScene()).thenReturn(SceneAutomationResult.Failed("boom"))
        assertThat(sut.doAction().success).isFalse()
    }

    @Test fun doActionSucceedsWhenFollowUpStarted() = runTest {
        sut.runFollowUp = true
        whenever(sceneApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.ChainCompleted("Sleep", "Sleep in", 0, 3))
        assertThat(sut.doAction().success).isTrue()
    }

    @Test fun doActionFailsWhenFollowUpHadFailedActions() = runTest {
        sut.runFollowUp = true
        whenever(sceneApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.ChainCompleted("Sleep", "Sleep in", 1, 3))
        assertThat(sut.doAction().success).isFalse()
    }

    @Test fun doActionFailsWhenFollowUpSceneIsMissingOrDisabled() = runTest {
        sut.runFollowUp = true
        whenever(sceneApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.SceneNotFound)
        assertThat(sut.doAction().success).isFalse()
        whenever(sceneApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.SceneDisabled)
        assertThat(sut.doAction().success).isFalse()
    }

    @Test fun hasDialogAndIsValid() {
        assertThat(sut.hasDialog()).isTrue()
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.runFollowUp = true
        JSONAssert.assertEquals("""{"data":{"runFollowUp":true,"sceneId":""},"type":"ActionStopScene"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"runFollowUp":true}""")
        assertThat(sut.runFollowUp).isTrue()
    }

    @Test fun shortDescriptionNamesTheChosenScene() = runTest {
        whenever(sceneApi.getScene("scene-1")).thenReturn(exercise)
        sut.scene.value = "scene-1"
        assertThat(sut.shortDescription()).isEqualTo("Stop scene: Exercise")
        sut.runFollowUp = true
        assertThat(sut.shortDescription()).isEqualTo("Stop scene: Exercise, run follow-up scene")
    }

    /** With a scene chosen, the rule must not end another scene, for example one the user started by hand. */
    @Test fun preconditionOnlyRunsWhenTheChosenSceneIsRunning() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.activeSceneId()).thenReturn("scene-1")
        assertThat(sut.precondition!!.shouldRun()).isTrue()
        whenever(sceneApi.activeSceneId()).thenReturn("scene-2")
        assertThat(sut.precondition!!.shouldRun()).isFalse()
        whenever(sceneApi.activeSceneId()).thenReturn(null)
        assertThat(sut.precondition!!.shouldRun()).isFalse()
    }

    @Test fun preconditionWithAnySceneNeedsARunningScene() = runTest {
        whenever(sceneApi.isAnySceneActive()).thenReturn(true)
        assertThat(sut.precondition!!.shouldRun()).isTrue()
        whenever(sceneApi.isAnySceneActive()).thenReturn(false)
        assertThat(sut.precondition!!.shouldRun()).isFalse()
    }

    @Test fun deletedSceneIsInvalid() {
        whenever(sceneApi.getScene("missing")).thenReturn(null)
        sut.scene.value = "missing"
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun sceneIdRoundTrip() {
        sut.fromJSON("""{"runFollowUp":false,"sceneId":"scene-1"}""")
        assertThat(sut.scene.value).isEqualTo("scene-1")
        sut.fromJSON("""{"runFollowUp":false}""")
        assertThat(sut.scene.value).isEmpty()
    }

    @Test fun missingOptionMeansNoFollowUp() {
        sut.runFollowUp = true
        sut.fromJSON("""{}""")
        assertThat(sut.runFollowUp).isFalse()
    }
}
