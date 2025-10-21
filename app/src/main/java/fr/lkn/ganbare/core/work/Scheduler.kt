package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val UNIQUE_DAILY = "daily_summary_once"

    fun scheduleDailyAt(context: Context, time: LocalTime) {
        val now = LocalDateTime.now()
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val delay = Duration.between(now, next)
        val req = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_DAILY, ExistingWorkPolicy.REPLACE, req)
    }
}
