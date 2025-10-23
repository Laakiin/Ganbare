package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.lkn.ganbare.core.prefs.AppPrefs
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Gère la planification des rappels liés au planning (récap quotidien).
 * Cette version lit les nouvelles préférences via AppPrefs.getPlanning(...).
 */
object ReminderScheduler {

    private const val WORK_DAILY_SUMMARY = "work-daily-summary"

    /**
     * Reprogramme (ou annule) le récap quotidien selon les préférences.
     * - Si `enableDailySummary` est false → on annule le Work.
     * - Sinon on (re)planifie un PeriodicWork à 24h avec un initialDelay
     *   calculé pour tomber à l’heure choisie.
     */
    suspend fun rescheduleDailySummary(context: Context) {
        val planning = AppPrefs.getPlanning(context)
        if (!planning.enableDailySummary) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY_SUMMARY)
            return
        }
        scheduleDailySummaryAt(context, planning.dailySummaryTime)
    }

    /**
     * Force la planification à l’heure donnée (utilisé après changement d’heure).
     */
    fun scheduleDailySummaryAt(context: Context, time: LocalTime) {
        val initialDelayMs = computeInitialDelayMs(time)
        val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_DAILY_SUMMARY,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    /**
     * Annule explicitement le récap quotidien.
     */
    fun cancelDailySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY_SUMMARY)
    }

    // --- Utils ---

    private fun computeInitialDelayMs(targetTime: LocalTime): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var first = now.withHour(targetTime.hour)
            .withMinute(targetTime.minute)
            .withSecond(0)
            .withNano(0)
        if (!first.toInstant().isAfter(Instant.now())) {
            first = first.plusDays(1)
        }
        return max(0L, Duration.between(Instant.now(), first.toInstant()).toMillis())
    }
}
