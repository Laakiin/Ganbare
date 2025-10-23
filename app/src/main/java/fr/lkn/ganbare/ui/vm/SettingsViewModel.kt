package fr.lkn.ganbare.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.lkn.ganbare.core.prefs.AppPrefs
import fr.lkn.ganbare.core.prefs.RecurrencePrefs
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.core.reminders.RecurrenceMapping
import fr.lkn.ganbare.core.work.NotifTestWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class RecurrenceUiState(
    val p1: Recurrence,
    val p2: Recurrence,
    val p3: Recurrence,
    val p4: Recurrence,
    val enableDayBefore: Boolean,
    val enableTwoHoursBefore: Boolean,
    val enableOnDay: Boolean,

    val dailySummaryTime: LocalTime,
    val agendaRolloverTime: LocalTime,
    val firstEventInfoTime: LocalTime,
    val enableDailySummary: Boolean,
    val enableFirstEventInfo: Boolean,

    val isSaving: Boolean = false
) {
    fun toMapping() = RecurrenceMapping(p1, p2, p3, p4)

    companion object {
        fun default() = RecurrenceUiState(
            p1 = RecurrenceMapping.default().p1,
            p2 = RecurrenceMapping.default().p2,
            p3 = RecurrenceMapping.default().p3,
            p4 = RecurrenceMapping.default().p4,
            enableDayBefore = true,
            enableTwoHoursBefore = true,
            enableOnDay = true,
            dailySummaryTime = LocalTime.of(20, 0),
            agendaRolloverTime = LocalTime.of(18, 0),
            firstEventInfoTime = LocalTime.of(20, 0),
            enableDailySummary = true,
            enableFirstEventInfo = true
        )
    }
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(RecurrenceUiState.default())
    val ui: StateFlow<RecurrenceUiState> = _ui

    init {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            combine(
                RecurrencePrefs.flowSettings(ctx),
                AppPrefs.flowPlanning(ctx)
            ) { settings, planning ->
                RecurrenceUiState(
                    p1 = settings.mapping.p1,
                    p2 = settings.mapping.p2,
                    p3 = settings.mapping.p3,
                    p4 = settings.mapping.p4,
                    enableDayBefore = settings.enableDayBefore,
                    enableTwoHoursBefore = settings.enableTwoHoursBefore,
                    enableOnDay = settings.enableOnDay,
                    dailySummaryTime = planning.dailySummaryTime,
                    agendaRolloverTime = planning.agendaRolloverTime,
                    firstEventInfoTime = planning.firstEventInfoTime,
                    enableDailySummary = planning.enableDailySummary,
                    enableFirstEventInfo = planning.enableFirstEventInfo,
                    isSaving = false
                )
            }.collect { _ui.value = it }
        }
    }

    // ----- Tâches : réglages de récurrence & switches -----
    fun setP1(r: Recurrence) = _ui.update { it.copy(p1 = r) }
    fun setP2(r: Recurrence) = _ui.update { it.copy(p2 = r) }
    fun setP3(r: Recurrence) = _ui.update { it.copy(p3 = r) }
    fun setP4(r: Recurrence) = _ui.update { it.copy(p4 = r) }
    fun toggleDayBefore(on: Boolean) = _ui.update { it.copy(enableDayBefore = on) }
    fun toggleTwoHours(on: Boolean) = _ui.update { it.copy(enableTwoHoursBefore = on) }
    fun toggleOnDay(on: Boolean) = _ui.update { it.copy(enableOnDay = on) }

    // ----- Planning : heures & switches -----
    fun setDailySummaryTime(t: LocalTime) = _ui.update { it.copy(dailySummaryTime = t) }
    fun setAgendaRolloverTime(t: LocalTime) = _ui.update { it.copy(agendaRolloverTime = t) }
    fun setFirstEventInfoTime(t: LocalTime) = _ui.update { it.copy(firstEventInfoTime = t) }
    fun toggleEnableDailySummary(on: Boolean) = _ui.update { it.copy(enableDailySummary = on) }
    fun toggleEnableFirstEventInfo(on: Boolean) = _ui.update { it.copy(enableFirstEventInfo = on) }

    fun save() {
        val ctx = getApplication<Application>()
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true) }
            RecurrencePrefs.set(ctx, s.toMapping())
            RecurrencePrefs.setSwitches(ctx, s.enableDayBefore, s.enableTwoHoursBefore, s.enableOnDay)
            AppPrefs.setPlanning(
                context = ctx,
                dailySummary = s.dailySummaryTime,
                agendaRollover = s.agendaRolloverTime,
                firstEventInfo = s.firstEventInfoTime,
                enableDailySummary = s.enableDailySummary,
                enableFirstEventInfo = s.enableFirstEventInfo
            )
            _ui.update { it.copy(isSaving = false) }
        }
    }

    // ---------- TEST NOTIFS ----------
    private fun enqueueTest(type: String, extra: Map<String, Any?> = emptyMap()) {
        val ctx = getApplication<Application>()

        val data: Data = Data.Builder()
            .putString(NotifTestWorker.KEY_TEST_TYPE, type)
            .apply {
                extra.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v)
                        is Long -> putLong(k, v)
                        is Int -> putInt(k, v)
                        is Boolean -> putBoolean(k, v)
                    }
                }
            }
            .build()

        val req = OneTimeWorkRequestBuilder<NotifTestWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(ctx).enqueue(req)
    }

    fun testDailySummary() = enqueueTest(NotifTestWorker.TYPE_DAILY_SUMMARY)
    fun testFirstEventInfo() = enqueueTest(NotifTestWorker.TYPE_FIRST_EVENT)
    fun testTaskDayBefore() = enqueueTest(
        NotifTestWorker.TYPE_TASK,
        mapOf(NotifTestWorker.KEY_TASK_KIND to "DAY_BEFORE")
    )
    fun testTaskTwoHours() = enqueueTest(
        NotifTestWorker.TYPE_TASK,
        mapOf(NotifTestWorker.KEY_TASK_KIND to "TWO_HOURS")
    )
    fun testTaskOnDay() = enqueueTest(
        NotifTestWorker.TYPE_TASK,
        mapOf(NotifTestWorker.KEY_TASK_KIND to "ON_DAY")
    )
}
