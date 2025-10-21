package fr.lkn.ganbare.core.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object Scheduler {
    private const val UNIQUE_DAILY = "daily_summary_unique"
    private const val TAG = "Scheduler"

    /**
     * Programme un Worker one-shot pour le prochain créneau à [hour]:[minute].
     * Le Worker se reprogrammera lui-même après exécution.
     */
    fun scheduleDailySummary(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val todayAt = LocalDate.now().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        val next = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        val delay = Duration.between(now, next).coerceAtLeast(Duration.ZERO)

        Log.d(TAG, "scheduleDailySummary: next=$next (in ${delay.toMinutes()} min)")

        val req = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delay)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_DAILY, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelDailySummary(context: Context) {
        Log.d(TAG, "cancelDailySummary")
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DAILY)
    }

    /** Petit utilitaire debug : planifie un run dans [seconds] secondes. */
    fun debugRunIn(context: Context, seconds: Long) {
        val delay = Duration.ofSeconds(seconds.coerceAtLeast(0))
        Log.d(TAG, "debugRunIn: ${delay.seconds}s")

        val req = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delay)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_DAILY, ExistingWorkPolicy.REPLACE, req)
    }
}
