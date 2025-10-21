package fr.lkn.ganbare.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val DAILY_SUMMARY = "daily_summary"
    const val TASKS = "tasks"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val daily = NotificationChannel(
                DAILY_SUMMARY, "Récap quotidien", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Cours iCal + devoirs/exams du lendemain" }

            val tasks = NotificationChannel(
                TASKS, "Rappels de tâches", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications pour devoirs/exams" }

            nm.createNotificationChannel(daily)
            nm.createNotificationChannel(tasks)
        }
    }
}
