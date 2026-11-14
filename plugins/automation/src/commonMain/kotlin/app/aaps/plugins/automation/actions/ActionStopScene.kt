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
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stops the running scene. Does nothing, and succeeds, when no scene is running.
 * With [runFollowUp] the follow-up scene of the stopped scene is started too, if it has one.
 */
class ActionStopScene(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val sceneApi: SceneAutomationApi
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var runFollowUp: Boolean = false

    override fun friendlyName(): TextRef = AutomationStrings.action_stop_scene
    override fun shortDescription(): String =
        rh.gs(if (runFollowUp) AutomationStrings.action_stop_scene_follow_up_short else AutomationStrings.action_stop_scene)

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
            put("data", buildJsonObject { put("runFollowUp", runFollowUp) })
        }.toString()

    override fun fromJSON(data: String): Action {
        runFollowUp = jsonOf(data).lenientBoolean("runFollowUp", false)
        return this
    }

    override fun isValid(): Boolean = true
}
