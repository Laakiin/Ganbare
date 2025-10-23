package fr.lkn.ganbare.core.work

import android.content.Context
import java.time.LocalTime
import kotlinx.coroutines.runBlocking

/**
 * Façade unifiée (compatibilité avec ton code existant).
 * - Planning quotidien (récap) : délégué à ReminderScheduler (inchangé).
 * - Rappels de tâches : délégué à TaskReminderScheduler (nouvelle implémentation réelle).
 */
object RemindersScheduler {

    // --- Planning : récap quotidien ---
    suspend fun rescheduleDailySummary(context: Context) =
        ReminderScheduler.rescheduleDailySummary(context)

    fun scheduleDailySummaryAt(context: Context, time: LocalTime) =
        ReminderScheduler.scheduleDailySummaryAt(context, time)

    fun cancelDailySummary(context: Context) =
        ReminderScheduler.cancelDailySummary(context)

    // --- Tâches : rappels (API de base) ---
    fun scheduleForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int
    ) = TaskReminderScheduler.scheduleForTask(context, taskId, title, dueAtMillis, priority)

    fun updateForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int
    ) = TaskReminderScheduler.scheduleForTask(context, taskId, title, dueAtMillis, priority)

    fun cancelForTask(context: Context, taskId: Long) =
        TaskReminderScheduler.cancelForTask(context, taskId)

    suspend fun rescheduleAll(context: Context) =
        TaskReminderScheduler.rescheduleAll(context)

    fun rescheduleAllBlocking(context: Context) = runBlocking {
        TaskReminderScheduler.rescheduleAll(context)
    }

    // --- Surcharges avec `summaryTime` (utilisées par ton ViewModel) ---

    fun scheduleForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int,
        summaryTime: LocalTime?
    ) = TaskReminderScheduler.scheduleForTask(context, taskId, title, dueAtMillis, priority, summaryTime)

    fun updateForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int,
        summaryTime: LocalTime?
    ) = TaskReminderScheduler.scheduleForTask(context, taskId, title, dueAtMillis, priority, summaryTime)
}
