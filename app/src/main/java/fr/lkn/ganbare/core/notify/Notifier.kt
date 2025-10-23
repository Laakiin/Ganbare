package fr.lkn.ganbare.core.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifier {

    private const val CHANNEL_DAILY = "ganbare_daily"
    private const val CHANNEL_TASKS = "ganbare_tasks"

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                CHANNEL_DAILY,
                "Récap quotidien",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications du récap quotidien (planning du lendemain)." },
            NotificationChannel(
                CHANNEL_TASKS,
                "Rappels de tâches",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Rappels des tâches/devoirs/examens." }
        )
        nm.createNotificationChannels(channels)
    }

    fun showDailySummary(
        ctx: Context,
        notificationId: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent? = null
    ) {
        ensureChannels(ctx)
        val nb = NotificationCompat.Builder(ctx, CHANNEL_DAILY)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // icône système pour éviter une res manquante
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        contentIntent?.let { nb.setContentIntent(it) }

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, nb.build())
    }

    fun showTaskReminder(
        ctx: Context,
        notificationId: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent? = null
    ) {
        ensureChannels(ctx)
        val nb = NotificationCompat.Builder(ctx, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        contentIntent?.let { nb.setContentIntent(it) }

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, nb.build())
    }
}
