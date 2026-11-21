package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.AutomationStrings

/**
 * The SMB settings are fixed to their default in simple mode, so a written value is not used now,
 * but it is used later when the user turns simple mode off. Refuse and tell the user instead.
 *
 * @return a failed result when simple mode is on, null when the action may run
 */
internal fun Action.refuseInSimpleMode(preferences: Preferences, notificationManager: NotificationManager): PumpEnactResult? {
    if (!preferences.simpleMode) return null
    val reason = rh.gs(AutomationStrings.action_not_in_simple_mode, shortDescription())
    aapsLogger.error(LTag.AUTOMATION, reason)
    notificationManager.post(NotificationId.AUTOMATION_MESSAGE, reason)
    return pumpEnactResultProvider().success(false).comment(reason)
}
