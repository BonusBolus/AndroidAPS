package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputSceneName
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerSceneActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionRunScene(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val sceneApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver,
    // Only to build the Trigger precondition below.
    private val triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    /** What to do when a scene is already running. */
    enum class Guard(val stringRes: TextRef) {

        /** Skip while any scene is running. A running scene is never replaced. This is the default. */
        NO_SCENE_ACTIVE(AutomationStrings.action_run_scene_guard_no_scene),

        /** Replace other scenes. Skip when the chosen scene is already the running scene. */
        TARGET_NOT_ACTIVE(AutomationStrings.action_run_scene_guard_other_scene),

        /** Always run and replace any running scene. Also restarts the chosen scene if it is running. */
        NONE(AutomationStrings.action_run_scene_guard_always)
    }

    var scene: InputSceneName = InputSceneName()

    var guard: Guard = Guard.NO_SCENE_ACTIVE
        set(value) {
            field = value
            precondition = preconditionFor(value)
        }

    // The guard decides if the action may run. By default a running scene is not replaced,
    // so a scene the user started by hand stays.
    override var precondition: Trigger? = preconditionFor(Guard.NO_SCENE_ACTIVE)

    private fun preconditionFor(mode: Guard): Trigger? = when (mode) {
        Guard.NO_SCENE_ACTIVE   -> TriggerSceneActive(triggerDeps, sceneApi, ComparatorExists.Compare.NOT_EXISTS)

        // Shares the scene value of this action, so the check follows the chosen scene.
        Guard.TARGET_NOT_ACTIVE -> TriggerSceneActive(triggerDeps, sceneApi, ComparatorExists.Compare.NOT_EXISTS).also { it.scene = scene }

        Guard.NONE              -> null
    }

    override fun friendlyName(): TextRef = AutomationStrings.action_run_scene
    override fun shortDescription(): String =
        rh.gs(AutomationStrings.action_run_scene_short, sceneApi.getScene(scene.value)?.name ?: "")

    // Show the assigned scene's icon when one is selected; fall back to PlayArrow in the
    // chooser sheet (no scene yet) or if the scene was deleted.
    override fun composeIcon() = sceneIconResolver.iconForScene(scene.value) ?: Icons.Filled.PlayArrow
    override fun elementType() = ElementType.SCENE

    override suspend fun doAction(): PumpEnactResult =
        when (val result = sceneApi.runScene(scene.value)) {
            SceneAutomationResult.Success            ->
                pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)

            SceneAutomationResult.SceneNotFound      ->
                pumpEnactResultProvider().success(false).comment(AutomationStrings.action_scene_not_found)

            SceneAutomationResult.SceneDisabled      ->
                pumpEnactResultProvider().success(false).comment(AutomationStrings.action_scene_disabled)

            is SceneAutomationResult.Failed          ->
                pumpEnactResultProvider().success(false)
                    .comment(result.message ?: rh.gs(CoreUiStrings.error))

            // runScene never returns ChainCompleted (only stopActiveSceneAndStartScene does);
            // the sealed interface forces exhaustiveness here.
            is SceneAutomationResult.ChainCompleted  ->
                pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
        }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("sceneId", scene.value)
            put("guard", guard.name)
        }
        return buildJsonObject {
            put("type", this@ActionRunScene::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        scene.value = o.lenientString("sceneId", "")
        // A missing or unknown value gives the safest guard: do not replace a running scene.
        guard = Guard.entries.firstOrNull { it.name == o.lenientString("guard", "") } ?: Guard.NO_SCENE_ACTIVE
        return this
    }

    override fun isValid(): Boolean {
        val s = sceneApi.getScene(scene.value) ?: return false
        return s.isEnabled
    }
}
