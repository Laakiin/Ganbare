package fr.lkn.ganbare.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.work.Scheduler

/**
 * Relancé au BOOT et lors des mises à jour du package pour
 * reprogrammer (ou annuler) la notif quotidienne selon les préférences.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = PreferencesManager(context)
            val s = prefs.current()
            if (s.recapEnabled) {
                Scheduler.scheduleDailySummary(
                    context = context,
                    hour = s.recapHour,
                    minute = s.recapMinute
                )
            } else {
                Scheduler.cancelDailySummary(context)
            }
        }
    }
}
