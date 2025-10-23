package fr.lkn.ganbare.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val title = inputData.getString(KEY_TASK_TITLE) ?: "Tâche"
        val dueAt = inputData.getLong(KEY_DUE_AT, -1L)
        val kind = inputData.getString(KEY_KIND) ?: KIND_ON_DAY

        if (taskId < 0 || dueAt <= 0) return Result.success()

        val zone = ZoneId.systemDefault()
        val dueStr = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(zone).format(Instant.ofEpochMilli(dueAt))

        val body = when (kind) {
            KIND_DAILY -> "Rappel quotidien — échéance le $dueStr"
            KIND_WEEKLY -> "Rappel hebdomadaire — échéance le $dueStr"
            KIND_MONTHLY -> "Rappel mensuel — échéance le $dueStr"
            KIND_DAY_BEFORE -> "Demain : $title (échéance $dueStr)"
            KIND_TWO_HOURS_BEFORE -> "Bientôt : $title (dans ~2h, échéance $dueStr)"
            KIND_ON_DAY -> "Aujourd’hui : $title (échéance $dueStr)"
            else -> "Rappel — échéance le $dueStr"
        }

        val notifId = buildNotificationId(taskId, kind)
        Notifier.showTaskReminder(
            applicationContext,
            notifId,
            "Rappel — $title",
            body,
            contentIntent = null
        )
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val KEY_TASK_TITLE = "taskTitle"
        const val KEY_DUE_AT = "dueAt"
        const val KEY_KIND = "kind"

        const val KIND_DAILY = "DAILY"
        const val KIND_WEEKLY = "WEEKLY"
        const val KIND_MONTHLY = "MONTHLY"
        const val KIND_DAY_BEFORE = "DAY_BEFORE"
        const val KIND_TWO_HOURS_BEFORE = "TWO_HOURS_BEFORE"
        const val KIND_ON_DAY = "ON_DAY"

        fun inputData(
            taskId: Long,
            title: String,
            dueAt: Long,
            kind: String
        ): Data = Data.Builder()
            .putLong(KEY_TASK_ID, taskId)
            .putString(KEY_TASK_TITLE, title)
            .putLong(KEY_DUE_AT, dueAt)
            .putString(KEY_KIND, kind)
            .build()

        fun buildNotificationId(taskId: Long, kind: String): Int {
            val k = when (kind) {
                KIND_DAILY -> 1
                KIND_WEEKLY -> 2
                KIND_MONTHLY -> 3
                KIND_DAY_BEFORE -> 4
                KIND_TWO_HOURS_BEFORE -> 5
                KIND_ON_DAY -> 6
                else -> 0
            }
            return (taskId % Int.MAX_VALUE).toInt() * 10 + k
        }
    }
}
