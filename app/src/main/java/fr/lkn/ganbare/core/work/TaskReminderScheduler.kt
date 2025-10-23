package fr.lkn.ganbare.core.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.lkn.ganbare.core.work.TaskReminderWorker.Companion.KEY_DUE_AT
import fr.lkn.ganbare.core.work.TaskReminderWorker.Companion.KEY_KIND
import fr.lkn.ganbare.core.work.TaskReminderWorker.Companion.KEY_TASK_ID
import fr.lkn.ganbare.core.work.TaskReminderWorker.Companion.KEY_TITLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.util.concurrent.TimeUnit

/**
 * Planifie réellement les rappels de tâches via WorkManager.
 *
 * Stratégie :
 *  - On (ré)crée 3 rappels ponctuels optionnels : VEILLE @ summaryTime, 2H_AVANT, JOUR_J @ summaryTime.
 *  - On crée 1 rappel "RÉCURRENCE" (DAILY/WEEKLY/MONTHLY) à la prochaine occurrence < dueAt.
 *
 * Les réglages (activation des switches) sont supposés gérés côté UI/ViewModel
 * qui appelle/synchronise les planifs (cf. RemindersScheduler).
 */
object TaskReminderScheduler {

    private const val TAG = "TaskReminderScheduler"

    const val KIND_DAY_BEFORE = "DAY_BEFORE"
    const val KIND_TWO_HOURS = "TWO_HOURS"
    const val KIND_ON_DAY = "ON_DAY"
    const val KIND_PERIODIC = "PERIODIC"

    private fun workName(taskId: Long, kind: String) = "task_${taskId}_$kind"

    /**
     * Version sans heure de récap : fallback 18:00.
     */
    fun scheduleForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int
    ) = scheduleForTask(
        context = context,
        taskId = taskId,
        title = title,
        dueAtMillis = dueAtMillis,
        priority = priority,
        summaryTime = LocalTime.of(18, 0)
    )

    /**
     * Version avec heure de récap venant des réglages (préférable).
     */
    fun scheduleForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int,
        summaryTime: LocalTime?
    ) {
        cancelForTask(context, taskId) // on repart propre

        val wm = WorkManager.getInstance(context)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        val dueInstant = Instant.ofEpochMilli(dueAtMillis)
        val dueZdt = dueInstant.atZone(zone)
        val dueDate = dueZdt.toLocalDate()
        val dueHasTime = dueZdt.toLocalTime() != LocalTime.MIDNIGHT // approximation raisonnable
        val recapTime = summaryTime ?: LocalTime.of(18, 0)

        // --- Rappels système (si en futur) ---
        // Veille @ summaryTime
        scheduleAt(
            wm = wm,
            name = workName(taskId, KIND_DAY_BEFORE),
            whenInstant = dueDate.minusDays(1).atTime(recapTime).atZone(zone).toInstant(),
            now = now,
            input = baseInput(taskId, title, dueAtMillis, KIND_DAY_BEFORE)
        )

        // 2h avant (utile surtout si heure précisée)
        if (dueHasTime) {
            scheduleAt(
                wm = wm,
                name = workName(taskId, KIND_TWO_HOURS),
                whenInstant = dueInstant.minus(Duration.ofHours(2)),
                now = now,
                input = baseInput(taskId, title, dueAtMillis, KIND_TWO_HOURS)
            )
        }

        // Jour J @ summaryTime
        scheduleAt(
            wm = wm,
            name = workName(taskId, KIND_ON_DAY),
            whenInstant = dueDate.atTime(recapTime).atZone(zone).toInstant(),
            now = now,
            input = baseInput(taskId, title, dueAtMillis, KIND_ON_DAY)
        )

        // --- Récurrence selon criticité ---
        val nextPeriodic = nextPeriodicOccurrence(
            now = now.atZone(zone).toLocalDateTime(),
            until = dueZdt.toLocalDateTime(),
            priority = priority,
            at = recapTime
        )

        if (nextPeriodic != null) {
            scheduleAt(
                wm = wm,
                name = workName(taskId, KIND_PERIODIC),
                whenInstant = nextPeriodic.atZone(zone).toInstant(),
                now = now,
                input = baseInput(taskId, title, dueAtMillis, KIND_PERIODIC)
            )
        }

        Log.d(TAG, "Scheduled reminders for task=$taskId ($title)")
    }

    fun cancelForTask(context: Context, taskId: Long) {
        val wm = WorkManager.getInstance(context)
        listOf(KIND_DAY_BEFORE, KIND_TWO_HOURS, KIND_ON_DAY, KIND_PERIODIC)
            .forEach { wm.cancelUniqueWork(workName(taskId, it)) }
    }

    /**
     * Replanif de masse — à brancher si tu veux relire la DB de tâches.
     * Pour l’instant, on laisse volontairement vide (pour ne pas deviner ta DAO).
     */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "rescheduleAll(): not implemented (needs tasks DB).")
    }

    // --- Helpers ---

    private fun baseInput(
        taskId: Long,
        title: String,
        dueAt: Long,
        kind: String
    ) = workDataOf(
        KEY_TASK_ID to taskId,
        KEY_TITLE to title,
        KEY_DUE_AT to dueAt,
        KEY_KIND to kind
    )

    private fun scheduleAt(
        wm: WorkManager,
        name: String,
        whenInstant: Instant,
        now: Instant,
        input: androidx.work.Data
    ) {
        val delay = Duration.between(now, whenInstant).toMillis()
        if (delay <= 0) return

        val req = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * Récurrence en fonction de la priorité :
     *  P1 = DAILY, P2 = WEEKLY, P3 = MONTHLY, P4 = NONE (exemple par défaut).
     *  -> Retourne la **prochaine** occurrence (LocalDateTime) entre `now` exclu et `until` inclus.
     */
    private fun nextPeriodicOccurrence(
        now: LocalDateTime,
        until: LocalDateTime,
        priority: Int,
        at: LocalTime
    ): LocalDateTime? {
        var candidate = when (priority) {
            1 -> LocalDateTime.of(now.toLocalDate(), at).let { if (!it.isAfter(now)) it.plusDays(1) else it } // daily
            2 -> {
                val base = LocalDateTime.of(now.toLocalDate(), at)
                var next = base
                while (!next.isAfter(now)) next = next.plusWeeks(1)
                next
            }
            3 -> {
                val base = LocalDateTime.of(now.toLocalDate().withDayOfMonth(1), at)
                var next = base
                while (!next.isAfter(now)) next = next.plusMonths(1)
                next
            }
            else -> return null // P4 = no periodic
        }
        if (candidate.isAfter(until)) return null
        return candidate
    }
}
