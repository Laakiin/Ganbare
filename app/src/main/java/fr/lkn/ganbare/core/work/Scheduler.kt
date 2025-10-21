package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Scheduler {

    private const val DAILY_UNIQUE_NAME = "daily-summary-work"
    private const val DAILY_TAG = "daily-summary"

    /**
     * Programme un worker quotidien à heure/minute fixes locales.
     * Utilise un PeriodicWorkRequest de 24h avec un initialDelay calculé.
     */
    fun scheduleDailySummary(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val targetToday = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val firstRun = if (targetToday.isAfter(now)) targetToday else targetToday.plusDays(1)

        val initialDelayMillis = Duration.between(now, firstRun).toMillis()
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .addTag(DAILY_TAG)
            // Si le worker a besoin d'info (ex: heure paramétrée), on peut la passer ici
            .setInputData(
                workDataOf(
                    "hour" to hour,
                    "minute" to minute
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_UNIQUE_NAME)
    }
}
