package fr.lkn.ganbare.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_DAILY = "daily_summary"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val daily = NotificationChannel(
                CHANNEL_DAILY,
                "Récap quotidien",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Résumé des cours du lendemain (depuis ton iCal)."
            }
            nm.createNotificationChannel(daily)
        }
    }
}
