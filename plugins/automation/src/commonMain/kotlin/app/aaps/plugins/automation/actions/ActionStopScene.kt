package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputSceneName
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerSceneActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stops the running scene. With [scene] set, it only stops that scene, so a scene the user started
 * by hand is not ended by a rule meant for another scene. The precondition keeps the rule from
 * running when the scene is not running.
 * With [runFollowUp] the follow-up scene of the stopped scene is started too, if it has one.
 */
class ActionStopScene(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val sceneApi: SceneAutomationApi,
    // Only to build the Trigger precondition below.
    triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var runFollowUp: Boolean = false

    /** The scene to stop. An empty value means any scene. */
    val scene = InputSceneName()

    // Shares the scene value of this action, so the check follows the chosen scene.
    override var precondition: Trigger? =
        TriggerSceneActive(triggerDeps, sceneApi, ComparatorExists.Compare.EXISTS).also { it.scene = scene }

    override fun friendlyName(): TextRef = AutomationStrings.action_stop_scene
    override fun shortDescription(): String {
        if (scene.value.isEmpty())
            return rh.gs(if (runFollowUp) AutomationStrings.action_stop_scene_follow_up_short else AutomationStrings.action_stop_scene)
        val name = sceneApi.getScene(scene.value)?.name ?: ""
        return rh.gs(if (runFollowUp) AutomationStrings.action_stop_named_scene_follow_up_short else AutomationStrings.action_stop_named_scene_short, name)
    }

    override fun composeIcon() = Icons.Filled.Stop
    override fun elementType() = ElementType.SCENE

    override suspend fun doAction(): PumpEnactResult {
        val result = if (runFollowUp) sceneApi.stopActiveSceneAndChain() else sceneApi.stopActiveScene()
        return when (result) {
            SceneAutomationResult.Success           ->
                pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)

            // Only the follow-up scene can be missing or disabled. The stopped scene is already ended.
            SceneAutomationResult.SceneNotFound     ->
                pumpEnactResultProvider().success(false).comment(AutomationStrings.action_scene_not_found)

            SceneAutomationResult.SceneDisabled     ->
                pumpEnactResultProvider().success(false).comment(AutomationStrings.action_scene_disabled)

            is SceneAutomationResult.Failed         ->
                pumpEnactResultProvider().success(false)
                    .comment(result.message ?: rh.gs(CoreUiStrings.error))

            is SceneAutomationResult.ChainCompleted ->
                if (result.failedCount == 0) pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
                else pumpEnactResultProvider().success(false).comment(CoreUiStrings.scene_some_actions_failed)
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionStopScene::class.simpleName)
            put("data", buildJsonObject {
                put("runFollowUp", runFollowUp)
                put("sceneId", scene.value)
            })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        runFollowUp = o.lenientBoolean("runFollowUp", false)
        // Rules saved before the scene choice existed stop any scene, as they did then.
        scene.value = o.lenientString("sceneId", "")
        return this
    }

    override fun isValid(): Boolean = scene.value.isEmpty() || sceneApi.getScene(scene.value) != null
}
