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
 * Envoie des notifications "de test" instantanées depuis l'écran Réglages.
 * - TYPE_DAILY_SUMMARY       -> Test récap planning
 * - TYPE_FIRST_EVENT         -> Test 1er évènement
 * - TYPE_TASK (KEY_TASK_KIND -> DAY_BEFORE / TWO_HOURS / ON_DAY) -> Test rappel tâche
 */
class NotifTestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TEST_TYPE = "test_type"
        const val TYPE_DAILY_SUMMARY = "DAILY_SUMMARY"
        const val TYPE_FIRST_EVENT = "FIRST_EVENT"
        const val TYPE_TASK = "TASK"

        const val KEY_TASK_KIND = "task_kind"

        private const val CH_PLANNING = "planning_notifications"
        private const val CH_TASKS = "task_reminders"
    }

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TEST_TYPE) ?: return Result.success()

        when (type) {
            TYPE_DAILY_SUMMARY -> {
                ensurePlanningChannel()
                val text = "Récap quotidien (test)"
                val notif = NotificationCompat.Builder(applicationContext, CH_PLANNING)
                    .setSmallIcon(android.R.drawable.ic_menu_today)
                    .setContentTitle("Ganbare — Test planning")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
            }
            TYPE_FIRST_EVENT -> {
                ensurePlanningChannel()
                val text = "1er évènement de demain (test)"
                val notif = NotificationCompat.Builder(applicationContext, CH_PLANNING)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("Ganbare — Test planning")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
            }
            TYPE_TASK -> {
                ensureTaskChannel()
                val kind = inputData.getString(KEY_TASK_KIND) ?: "ON_DAY"
                val text = when (kind) {
                    "DAY_BEFORE" -> "Rappel de tâche (test — veille)"
                    "TWO_HOURS"  -> "Rappel de tâche (test — 2 h avant)"
                    else         -> "Rappel de tâche (test — jour J)"
                }
                val notif = NotificationCompat.Builder(applicationContext, CH_TASKS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Ganbare — Test tâche")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(applicationContext).notify((System.nanoTime() % Int.MAX_VALUE).toInt(), notif)
            }
        }
        return Result.success()
    }

    private fun ensurePlanningChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CH_PLANNING,
            "Notifications planning",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Récap quotidien et 1er évènement de demain (tests et notifs)" }
        mgr.createNotificationChannel(ch)
    }

    private fun ensureTaskChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CH_TASKS,
            "Rappels des tâches",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Rappels (veille, 2 h avant, jour J, périodiques)" }
        mgr.createNotificationChannel(ch)
    }
}
