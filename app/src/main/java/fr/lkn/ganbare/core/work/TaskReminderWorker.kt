package fr.lkn.ganbare.core.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Exécute la notification pour une tâche donnée.
 *
 * NB: On crée un canal "task_reminders" si besoin (Android 8+).
 */
class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TITLE = "title"
        const val KEY_DUE_AT = "due_at"
        const val KEY_KIND = "kind"

        private const val CHANNEL_ID = "task_reminders"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: "Tâche"
        val kind = inputData.getString(KEY_KIND) ?: "RAPPEL"

        if (taskId <= 0L) return Result.success()

        ensureChannel()

        val notifId = (taskId % Int.MAX_VALUE).toInt() + kind.hashCode()

        val text = when (kind) {
            TaskReminderScheduler.KIND_DAY_BEFORE -> "Rappel (veille) — $title"
            TaskReminderScheduler.KIND_TWO_HOURS -> "Rappel (2 h avant) — $title"
            TaskReminderScheduler.KIND_ON_DAY -> "Rappel (jour J) — $title"
            TaskReminderScheduler.KIND_PERIODIC -> "Rappel périodique — $title"
            else -> title
        }

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Ganbare")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notifId, notif)

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Rappels des tâches",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notifications des rappels de tâches (veille, 2h avant, jour J, périodiques)" }
        mgr.createNotificationChannel(ch)
    }
}
