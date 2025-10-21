package fr.lkn.ganbare.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            CoroutineScope(Dispatchers.Default).launch {
                val s = prefs.settingsFlow.first()
                if (s.dailyEnabled) Scheduler.scheduleDailyAt(context, s.dailyTime)
            }
        }
    }
}
