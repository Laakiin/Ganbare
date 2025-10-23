package fr.lkn.ganbare.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.lkn.ganbare.core.work.RemindersScheduler
import java.time.LocalTime

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // TODO: injecter/accéder à ton repo pour relire les tâches et replanifier :
        // repo.getAll().forEach { task ->
        //   RemindersScheduler.scheduleForTask(
        //      context, task.id, task.title, task.dueAt, task.priority, summaryTime = LocalTime.of(20,0)
        //   )
        // }
    }
}
