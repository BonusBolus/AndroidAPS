package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientString
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputSceneName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Triggers based on whether a scene is currently active.
 * With no scene chosen ([scene] is empty) it checks for any scene. With a scene chosen it checks
 * that this one scene is the running scene.
 * Also used as a precondition by [app.aaps.plugins.automation.actions.ActionRunScene].
 */
class TriggerSceneActive(
    deps: TriggerDeps,
    private val sceneApi: SceneAutomationApi
) : Trigger(deps) {


    var comparator = ComparatorExists(rh)

    /** The scene to check. An empty value means any scene. */
    var scene = InputSceneName()

    constructor(deps: TriggerDeps, sceneApi: SceneAutomationApi, compare: ComparatorExists.Compare) : this(deps, sceneApi) {
        comparator = ComparatorExists(rh, compare)
    }

    constructor(deps: TriggerDeps, sceneApi: SceneAutomationApi, other: TriggerSceneActive) : this(deps, sceneApi) {
        comparator = ComparatorExists(rh, other.comparator.value)
        scene.value = other.scene.value
    }

    fun scenes(): List<Scene> = sceneApi.getScenes()

    override suspend fun shouldRun(): Boolean {
        val active = if (scene.value.isEmpty()) sceneApi.isAnySceneActive() else sceneApi.activeSceneId() == scene.value
        val ready = (active && comparator.value == ComparatorExists.Compare.EXISTS) ||
            (!active && comparator.value == ComparatorExists.Compare.NOT_EXISTS)
        aapsLogger.debug(
            LTag.AUTOMATION,
            (if (ready) "Ready for execution: " else "NOT ready for execution: ") + friendlyDescription()
        )
        return ready
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("comparator", comparator.value.toString())
            put("sceneId", scene.value)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        comparator.value = ComparatorExists.Compare.valueOf(d.lenientStringOrNull("comparator")!!)
        scene.value = d.lenientString("sceneId", "")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.trigger_scene_active
    override fun friendlyDescription(): String =
        if (scene.value.isEmpty()) rh.gs(AutomationStrings.trigger_scene_active_compared, rh.gs(comparator.value.stringRes))
        else rh.gs(AutomationStrings.trigger_scene_named_compared, sceneApi.getScene(scene.value)?.name ?: "", rh.gs(comparator.value.stringRes))

    override fun composeIcon() = Icons.Filled.PlayArrow
    override fun elementType() = ElementType.SCENE

    override fun duplicate(): Trigger = TriggerSceneActive(deps, sceneApi, this)
}
