package fr.lkn.ganbare.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.work.Scheduler

/**
 * Reprogramme (ou annule) la notif quotidienne au boot et après mise à jour d’app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val prefs = PreferencesManager(context)
                val s = prefs.current()
                if (s.recapEnabled) {
                    Scheduler.scheduleDailySummary(context, s.recapHour, s.recapMinute)
                } else {
                    Scheduler.cancelDailySummary(context)
                }
            }
        }
    }
}
