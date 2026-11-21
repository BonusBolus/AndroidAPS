package app.aaps.plugins.automation.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientInt
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionProfileSwitchPercent(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val profileFunction: ProfileFunction,
    // Only to build the Trigger precondition below.
    private val triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var pct = InputPercent()
    /** Minutes of a temporary switch. Not used when [permanent] is true. */
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
    /** True = permanent switch of the current profile, [duration] is ignored. */
    var permanent = false

    private val profileAt100Percent = TriggerProfilePercent(triggerDeps, 100.0, Comparator.Compare.IS_EQUAL)

    // A temporary switch only starts from 100%. A permanent one runs only when the percentage is different,
    // otherwise a trigger that stays true would add the same switch again every loop run.
    // The setter is empty because the precondition follows the settings and is not stored.
    override var precondition: Trigger?
        get() = if (permanent) percentageIsDifferent() else profileAt100Percent
        set(_) = Unit

    private fun percentageIsDifferent(): Trigger =
        TriggerConnector(triggerDeps, TriggerConnector.Type.OR).also {
            it.list.add(TriggerProfilePercent(triggerDeps, pct.value, Comparator.Compare.IS_LESSER))
            it.list.add(TriggerProfilePercent(triggerDeps, pct.value, Comparator.Compare.IS_GREATER))
        }

    private val durationInMinutes: Int get() = if (permanent) 0 else duration.value

    override fun friendlyName(): TextRef = AutomationStrings.profilepercentage
    override fun shortDescription(): String =
        if (permanent) rh.gs(AutomationStrings.startprofileforever, pct.value.toInt())
        else rh.gs(CoreUiStrings.startprofile, pct.value.toInt(), duration.value)

    override fun composeIcon() = IcProfile
    override fun elementType() = ElementType.PROFILE_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult {
        val switched = profileFunction.createProfileSwitch(
            durationInMinutes = durationInMinutes,
            percentage = pct.value.toInt(),
            timeShiftInHours = 0,
            action = app.aaps.core.data.ue.Action.PROFILE_SWITCH,
            source = Sources.Automation,
            note = rh.gs(InterfacesStrings.confirmation_line, title, shortDescription()),
            listValues = listOf(
                ValueWithUnit.Percent(pct.value.toInt()),
                ValueWithUnit.Minute(durationInMinutes)
            )
        ) != null
        return if (switched) {
            pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
        } else {
            // Say why in the automation log, not "OK" next to a failure.
            val reason = profileFunction.profileSwitchRefusal(durationInMinutes, pct.value.toInt(), 0) ?: rh.gs(CoreUiStrings.invalid_profile)
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid: $reason")
            pumpEnactResultProvider().success(false).comment(reason)
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("percentage", pct.value)
            put("durationInMinutes", duration.value)
            put("permanent", permanent)
        }
        return buildJsonObject {
            put("type", this@ActionProfileSwitchPercent::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        pct.value = o.lenientDouble("percentage")
        duration.value = o.lenientInt("durationInMinutes")
        permanent = o.lenientBoolean("permanent", false)
        return this
    }

    override fun isValid(): Boolean =
        pct.value >= InputPercent.MIN &&
            pct.value <= InputPercent.MAX &&
            (permanent || duration.value >= MIN_DURATION_MINUTES)

    companion object {

        /** Shortest temporary switch in minutes. */
        const val MIN_DURATION_MINUTES = 1
    }
}