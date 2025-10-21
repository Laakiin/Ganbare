package fr.lkn.ganbare.core.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.lkn.ganbare.core.notify.Notifier
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.domain.calendar.CalendarRepository
import fr.lkn.ganbare.domain.calendar.CalendarRepositoryImpl
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Envoie la notif avec les cours de DEMAIN, puis reprogramme le prochain passage à l'heure choisie.
 * (Version SANS Hilt pour éviter toute config WorkerFactory)
 */
class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val context = appContext.applicationContext
    private val prefs = PreferencesManager(context)
    private val calendar: CalendarRepository = CalendarRepositoryImpl(context)

    override suspend fun doWork(): Result {
        val tag = "DailySummaryWorker"
        val settings = prefs.current()
        Log.d(tag, "doWork: recapEnabled=${settings.recapEnabled} at ${settings.recapHour}:${settings.recapMinute}")

        if (!settings.recapEnabled) {
            Scheduler.cancelDailySummary(context)
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val tomorrow = LocalDate.now().plusDays(1)
        val locale = Locale.getDefault()
        val dateLabel = tomorrow.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

        val events = runCatching { calendar.eventsFor(tomorrow) }
            .onFailure { Log.e(tag, "eventsFor($tomorrow) failed", it) }
            .getOrElse { emptyList() }

        val body = if (events.isEmpty()) {
            "Aucun cours prévu pour $dateLabel."
        } else buildString {
            append("Demain ($dateLabel) :\n")
            events.take(8).forEach { e ->
                val s = java.time.Instant.ofEpochMilli(e.startEpochMillis).atZone(zone)
                val eZ = java.time.Instant.ofEpochMilli(e.endEpochMillis).atZone(zone)
                val loc = e.location?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                append("- ${s.toLocalTime().format(timeFmt)}–${eZ.toLocalTime().format(timeFmt)} ${e.title}$loc\n")
            }
            if (events.size > 8) append("+${events.size - 8} autres…")
        }

        Notifier(context).showDailySummary("Récap du lendemain", body)

        // Replanifie pour le prochain créneau demandé
        Scheduler.scheduleDailySummary(context, settings.recapHour, settings.recapMinute)
        Log.d(tag, "Rescheduled for next day at ${settings.recapHour}:${settings.recapMinute}")
        return Result.success()
    }
}
