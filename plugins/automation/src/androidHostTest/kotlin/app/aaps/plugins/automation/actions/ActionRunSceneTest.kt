package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.triggers.TriggerSceneActive
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"sceneId":"scene-1","guard":"NO_SCENE_ACTIVE"},"type":"ActionRunScene"}"""

class ActionRunSceneTest : ActionsTestBase() {

    private lateinit var sut: ActionRunScene

    private val enabledScene = Scene(id = "scene-1", name = "Sport", isEnabled = true, defaultDurationMinutes = 60)
    private val disabledScene = Scene(id = "scene-2", name = "Sleep", isEnabled = false)

    @BeforeEach fun setUp() {
        sut = ActionRunScene(aapsLogger, text, { pumpEnactResultProvider() }, sceneApi, sceneIconResolver, triggerDeps)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.action_run_scene)
    }

    @Test fun shortDescription() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(enabledScene)
        assertThat(sut.shortDescription()).isEqualTo("Run scene: Sport")
    }

    @Test fun doActionSuccess() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.runScene(eq("scene-1"), anyOrNull())).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).runScene(eq("scene-1"), anyOrNull())
    }

    @Test fun doActionFailsWhenSceneNotFound() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.runScene(eq("missing"), anyOrNull())).thenReturn(SceneAutomationResult.SceneNotFound)
        val result = sut.doAction()
        assertThat(result.success).isFalse()
    }

    @Test fun doActionFailsWhenSceneDisabled() = runTest {
        sut.scene.value = "scene-2"
        whenever(sceneApi.runScene(eq("scene-2"), anyOrNull())).thenReturn(SceneAutomationResult.SceneDisabled)
        val result = sut.doAction()
        assertThat(result.success).isFalse()
    }

    @Test fun hasDialog() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.scene.value = "scene-1"
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"sceneId":"scene-1"}""")
        assertThat(sut.scene.value).isEqualTo("scene-1")
    }

    @Test fun fromJSONIgnoresLegacyDurationField() = runTest {
        // Older saved automations may have stored "durationInMinutes" — must not crash.
        sut.fromJSON("""{"sceneId":"scene-1","durationInMinutes":45}""")
        assertThat(sut.scene.value).isEqualTo("scene-1")
    }

    @Test fun isValidWhenSceneExistsAndEnabled() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(enabledScene)
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun isValidFalseWhenSceneDisabled() = runTest {
        sut.scene.value = "scene-2"
        whenever(sceneApi.getScene("scene-2")).thenReturn(disabledScene)
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun isValidFalseWhenSceneMissing() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.getScene("missing")).thenReturn(null)
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun defaultGuardSkipsWhileAnySceneIsRunning() = runTest {
        assertThat(sut.guard).isEqualTo(ActionRunScene.Guard.NO_SCENE_ACTIVE)
        val precondition = sut.precondition as TriggerSceneActive
        assertThat(precondition.comparator.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
        assertThat(precondition.scene.value).isEmpty()

        whenever(sceneApi.isAnySceneActive()).thenReturn(true)
        assertThat(precondition.shouldRun()).isFalse()
        whenever(sceneApi.isAnySceneActive()).thenReturn(false)
        assertThat(precondition.shouldRun()).isTrue()
    }

    @Test fun targetNotActiveGuardReplacesOtherScenesButNotItself() = runTest {
        sut.guard = ActionRunScene.Guard.TARGET_NOT_ACTIVE
        sut.scene.value = "scene-1" // set after the guard: the check must follow the chosen scene

        val precondition = sut.precondition!!
        whenever(sceneApi.activeSceneId()).thenReturn("scene-2")
        assertThat(precondition.shouldRun()).isTrue()
        whenever(sceneApi.activeSceneId()).thenReturn("scene-1")
        assertThat(precondition.shouldRun()).isFalse()
        whenever(sceneApi.activeSceneId()).thenReturn(null)
        assertThat(precondition.shouldRun()).isTrue()
    }

    @Test fun noneGuardHasNoPrecondition() {
        sut.guard = ActionRunScene.Guard.NONE
        assertThat(sut.precondition).isNull()
    }

    @Test fun guardIsRestoredFromJSON() {
        sut.fromJSON("""{"sceneId":"scene-1","guard":"TARGET_NOT_ACTIVE"}""")
        assertThat(sut.guard).isEqualTo(ActionRunScene.Guard.TARGET_NOT_ACTIVE)
        assertThat(sut.precondition).isNotNull()
        sut.fromJSON("""{"sceneId":"scene-1","guard":"NONE"}""")
        assertThat(sut.guard).isEqualTo(ActionRunScene.Guard.NONE)
        assertThat(sut.precondition).isNull()
    }

    @Test fun missingOrUnknownGuardGivesTheSafestGuard() {
        sut.guard = ActionRunScene.Guard.NONE
        sut.fromJSON("""{"sceneId":"scene-1"}""")
        assertThat(sut.guard).isEqualTo(ActionRunScene.Guard.NO_SCENE_ACTIVE)
        sut.guard = ActionRunScene.Guard.NONE
        sut.fromJSON("""{"sceneId":"scene-1","guard":"FROM_A_NEWER_VERSION"}""")
        assertThat(sut.guard).isEqualTo(ActionRunScene.Guard.NO_SCENE_ACTIVE)
    }
}
