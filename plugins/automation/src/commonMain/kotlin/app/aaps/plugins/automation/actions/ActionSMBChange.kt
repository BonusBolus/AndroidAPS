package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSMBChange(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val notificationManager: NotificationManager
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var smbState: InputDropdownOnOffMenu = InputDropdownOnOffMenu(rh, true)
    /** When true, [smbState] is ignored and the current SMB setting is inverted instead. */
    var toggle: Boolean = false

    override fun friendlyName(): TextRef = AutomationStrings.changeSmbState
    override fun shortDescription(): String =
        if (toggle) rh.gs(AutomationStrings.toggleSmb) else rh.gs(AutomationStrings.changeSmbTo, smbState.toTextValue())
    override fun composeIcon() = IcSmb
    override fun elementType() = ElementType.INSULIN

    override suspend fun doAction(): PumpEnactResult {
        refuseInSimpleMode(preferences, notificationManager)?.let { return it }
        preferences.put(BooleanKey.ApsUseSmb, if (toggle) !preferences.get(BooleanKey.ApsUseSmb) else smbState.value)
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("smbState", smbState.value)
            put("toggle", toggle)
        }
        return buildJsonObject {
            put("type", this@ActionSMBChange::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        // Old test builds stored smbState as the text "On", "Off" or "Toggle".
        val legacyState = o.lenientString("smbState").lowercase()
        smbState.value = if (legacyState == "off") false else o.lenientBoolean("smbState", true)
        toggle = o.lenientBoolean("toggle", legacyState == "toggle")
        return this
    }

    override fun isValid(): Boolean = true
}