package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSMBChange(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var preferences: Preferences

    //Display On, Off, Toggle based on current state of smbState
    companion object {
        const val SMB_ON = "On"
        const val SMB_OFF = "Off"
        const val SMB_TOGGLE = "Toggle"
    }

    //Initialize SMB to on, create dropdown with 3 options.
    var smbState: InputDropdownMenu = InputDropdownMenu(rh, SMB_ON).also {
        it.setList(arrayListOf(SMB_ON, SMB_OFF, SMB_TOGGLE))
    }

    override fun friendlyName(): Int = R.string.changeSmbState
    override fun shortDescription(): String = rh.gs(R.string.changeSmbTo, smbState.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_running_mode

    override fun doAction(callback: Callback) {

        //Determine new value for smbState, true or false
        val newValue = when (smbState.value) {
            SMB_ON  -> true
            SMB_OFF -> false
            else    -> !preferences.get(BooleanKey.ApsUseSmb) //set value to opposite of current state
        }

        preferences.put(BooleanKey.ApsUseSmb, newValue)
        callback.result(pumpEnactResultProvider.get().success(true).comment("SMB set to ${if (newValue) SMB_ON else SMB_OFF}")).run()
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.newSmbMode), "", smbState))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("smbState", smbState.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        smbState.value = JsonHelper.safeGetString(o, "smbState", SMB_ON)
        return this
    }

    override fun isValid(): Boolean = true
}