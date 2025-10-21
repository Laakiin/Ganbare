package fr.lkn.ganbare.core.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import fr.lkn.ganbare.R

object Notifier {
    private const val ID_DAILY = 1001
    private const val ID_TASK_BASE = 2000

    fun showDailySummary(context: Context, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, NotificationChannels.DAILY_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(ID_DAILY, notif)
    }

    fun showTaskReminder(context: Context, title: String, text: String, id: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, NotificationChannels.TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(ID_TASK_BASE + id, notif)
    }
}
