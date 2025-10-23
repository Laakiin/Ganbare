package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.*
import fr.lkn.ganbare.core.prefs.AppPrefs
import fr.lkn.ganbare.core.prefs.RecurrencePrefs
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.core.reminders.RecurrenceMapping
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

object RemindersScheduler {

    /**
     * Planifie tous les rappels pour une tâche en lisant la config utilisateur (DataStore).
     * - Rappels optionnels (switchs) : veille, 2h avant, jour J (Heure = heure du récap).
     * - Récurrence selon réglages (P1..P4 -> NONE/DAILY/WEEKLY/MONTHLY).
     * - Heure du récap = AppPrefs.dailySummaryTime (ou summaryTime param si fourni).
     */
    suspend fun scheduleForTask(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int,
        summaryTime: LocalTime? = null
    ) {
        val settings = RecurrencePrefs.getSettings(context)
        val times = AppPrefs.getTimes(context)
        val recapTime = summaryTime ?: times.dailySummaryTime

        scheduleForTaskWithSettings(
            context = context,
            taskId = taskId,
            title = title,
            dueAtMillis = dueAtMillis,
            priority = priority,
            mapping = settings.mapping,
            enableDayBefore = settings.enableDayBefore,
            enableTwoHoursBefore = settings.enableTwoHoursBefore,
            enableOnDay = settings.enableOnDay,
            summaryTime = recapTime
        )
    }

    fun scheduleForTaskWithSettings(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        priority: Int,
        mapping: RecurrenceMapping,
        enableDayBefore: Boolean,
        enableTwoHoursBefore: Boolean,
        enableOnDay: Boolean,
        summaryTime: LocalTime
    ) {
        val zone = ZoneId.systemDefault()
        val dueInstant = Instant.ofEpochMilli(dueAtMillis)

        // — Rappels conditionnels par switch —
        if (enableDayBefore) {
            scheduleOneShotAt(
                context, taskId, title, dueInstant, TaskReminderWorker.KIND_DAY_BEFORE
            ) {
                dueInstant.atZone(zone).minusDays(1)
                    .withHour(summaryTime.hour).withMinute(summaryTime.minute)
                    .withSecond(0).withNano(0).toInstant()
            }
        }

        if (enableTwoHoursBefore) {
            scheduleOneShotAt(
                context, taskId, title, dueInstant, TaskReminderWorker.KIND_TWO_HOURS_BEFORE
            ) { dueInstant.minusSeconds(2 * 3600) }
        }

        if (enableOnDay) {
            scheduleOneShotAt(
                context, taskId, title, dueInstant, TaskReminderWorker.KIND_ON_DAY
            ) {
                dueInstant.atZone(zone)
                    .withHour(summaryTime.hour).withMinute(summaryTime.minute)
                    .withSecond(0).withNano(0).toInstant()
            }
        }

        // — Récurrence configurable —
        when (mapping.forPriority(priority)) {
            Recurrence.NONE -> Unit
            Recurrence.DAILY -> schedulePeriodic(context, taskId, title, dueAtMillis, TaskReminderWorker.KIND_DAILY, Duration.ofDays(1), summaryTime)
            Recurrence.WEEKLY -> schedulePeriodic(context, taskId, title, dueAtMillis, TaskReminderWorker.KIND_WEEKLY, Duration.ofDays(7), summaryTime)
            Recurrence.MONTHLY -> schedulePeriodic(context, taskId, title, dueAtMillis, TaskReminderWorker.KIND_MONTHLY, Duration.ofDays(30), summaryTime)
        }
    }

    private fun scheduleOneShotAt(
        context: Context,
        taskId: Long,
        title: String,
        dueInstant: Instant,
        kind: String,
        targetInstantProvider: () -> Instant
    ) {
        val now = Instant.now()
        val target = targetInstantProvider()
        if (target.isBefore(now)) return

        val delayMs = Duration.between(now, target).toMillis()
        val data = TaskReminderWorker.inputData(taskId, title, dueInstant.toEpochMilli(), kind)

        val req = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(taskId, kind),
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    private fun schedulePeriodic(
        context: Context,
        taskId: Long,
        title: String,
        dueAtMillis: Long,
        kind: String,
        period: Duration,
        fireAt: LocalTime
    ) {
        val zone = ZoneId.systemDefault()
        val nowZdt = ZonedDateTime.now(zone)
        var first = nowZdt.withHour(fireAt.hour).withMinute(fireAt.minute).withSecond(0).withNano(0)
        if (!first.toInstant().isAfter(Instant.now())) {
            first = first.plus(period)
        }
        val initialDelayMs = max(0L, Duration.between(Instant.now(), first.toInstant()).toMillis())

        val data = TaskReminderWorker.inputData(taskId, title, dueAtMillis, kind)

        val req = PeriodicWorkRequestBuilder<TaskReminderWorker>(
            period.toHours(), TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName(taskId, kind),
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancelForTask(context: Context, taskId: Long) {
        val wm = WorkManager.getInstance(context)
        listOf(
            TaskReminderWorker.KIND_DAILY,
            TaskReminderWorker.KIND_WEEKLY,
            TaskReminderWorker.KIND_MONTHLY,
            TaskReminderWorker.KIND_DAY_BEFORE,
            TaskReminderWorker.KIND_TWO_HOURS_BEFORE,
            TaskReminderWorker.KIND_ON_DAY
        ).forEach { kind ->
            wm.cancelUniqueWork(uniqueName(taskId, kind))
        }
    }

    private fun uniqueName(taskId: Long, kind: String) = "task-$taskId-$kind"
}
