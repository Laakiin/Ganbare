package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.lkn.ganbare.core.ical.IcalRepository
import fr.lkn.ganbare.core.notify.Notifier
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.feature.tasks.data.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PreferencesManager,
    private val tasks: TaskRepository,
    private val ical: IcalRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val zone = ZoneId.systemDefault()
        val tomorrowDate = Instant.now().atZone(zone).toLocalDate().plusDays(1)
        val fromInstant = tomorrowDate.atStartOfDay(zone).toInstant()

        val s = prefs.settingsFlow.first()

        // Cours (iCal)
        val courses = try {
            if (s.icalUrl.isBlank()) emptyList()
            else ical.eventsForDate(s.icalUrl, tomorrowDate, zone)
        } catch (_: Exception) { emptyList() }

        // Tâches à partir de demain
        val upcoming = tasks.observeUpcoming(fromInstant).first()

        val fmtHeader = DateTimeFormatter.ofPattern("EEEE d MMMM")
        val header = "Récap du ${fmtHeader.format(tomorrowDate)}"

        val fmtTask = DateTimeFormatter.ofPattern("EEE d MMM HH:mm").withZone(zone)
        val taskLines = if (upcoming.isEmpty()) "Aucun devoir/exam."
        else upcoming.joinToString("\n") { "• ${it.title} — ${fmtTask.format(it.dueAt)} (P${it.priority})" }

        val fmtTime = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val courseLines = if (courses.isEmpty()) "Aucun cours."
        else courses.joinToString("\n") { c ->
            val t = "${fmtTime.format(c.start)}-${fmtTime.format(c.end)}"
            val loc = c.location?.let { " @ $it" } ?: ""
            "• $t  ${c.title}$loc"
        }

        val body = buildString {
            appendLine(header)
            appendLine()
            appendLine("Cours :")
            appendLine(courseLines)
            appendLine()
            appendLine("Devoirs/Exams :")
            appendLine(taskLines)
        }

        Notifier.showDailySummary(applicationContext, "Récap du lendemain", body)
        return Result.success()
    }
}
