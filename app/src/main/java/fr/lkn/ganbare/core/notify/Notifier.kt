package fr.lkn.ganbare.core.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.lkn.ganbare.MainActivity
import fr.lkn.ganbare.R

class Notifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    fun showDailySummary(title: String, text: String) {
        NotificationChannels.ensure(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_tab", "planning")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(1001, notif)
    }
}
