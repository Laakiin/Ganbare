package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

/**
 * Affiche la notif de récap. Utilise Notifier du même package (fr.lkn.ganbare.core.work),
 * donc PAS d'import vers un autre package.
 *
 * Cette version accepte (optionnel) un titre/texte via inputData.
 * Sinon, elle affiche un texte par défaut.
 */
class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Récap planning"
        val text = inputData.getString(KEY_TEXT)
            ?: "Consulte l’écran Planning pour les cours du lendemain."
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)

        // Notifier est dans le même package (core.work) -> pas besoin d'import.
        Notifier.showDailySummary(
            ctx = applicationContext,
            notificationId = notificationId,
            title = title,
            text = text,
            contentIntent = null // à brancher si tu veux ouvrir l’app sur tap
        )
        return Result.success()
    }

    companion object {
        private const val DEFAULT_NOTIFICATION_ID = 1001

        const val KEY_TITLE = "summary_title"
        const val KEY_TEXT = "summary_text"
        const val KEY_NOTIFICATION_ID = "summary_notification_id"

        fun inputData(
            title: String,
            text: String,
            notificationId: Int = DEFAULT_NOTIFICATION_ID
        ): Data = Data.Builder()
            .putString(KEY_TITLE, title)
            .putString(KEY_TEXT, text)
            .putInt(KEY_NOTIFICATION_ID, notificationId)
            .build()
    }
}
