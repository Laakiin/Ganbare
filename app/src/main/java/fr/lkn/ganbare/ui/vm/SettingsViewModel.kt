package fr.lkn.ganbare.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.prefs.AppPrefs
import fr.lkn.ganbare.core.prefs.RecurrencePrefs
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.core.reminders.RecurrenceMapping
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
            firstEventInfoTime = LocalTime.of(20, 0)
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
                AppPrefs.flowTimes(ctx)
            ) { settings, times ->
                RecurrenceUiState(
                    p1 = settings.mapping.p1,
                    p2 = settings.mapping.p2,
                    p3 = settings.mapping.p3,
                    p4 = settings.mapping.p4,
                    enableDayBefore = settings.enableDayBefore,
                    enableTwoHoursBefore = settings.enableTwoHoursBefore,
                    enableOnDay = settings.enableOnDay,
                    dailySummaryTime = times.dailySummaryTime,
                    agendaRolloverTime = times.agendaRolloverTime,
                    firstEventInfoTime = times.firstEventInfoTime,
                    isSaving = false
                )
            }.collect { _ui.value = it }
        }
    }

    fun setP1(r: Recurrence) = _ui.update { it.copy(p1 = r) }
    fun setP2(r: Recurrence) = _ui.update { it.copy(p2 = r) }
    fun setP3(r: Recurrence) = _ui.update { it.copy(p3 = r) }
    fun setP4(r: Recurrence) = _ui.update { it.copy(p4 = r) }

    fun toggleDayBefore(on: Boolean) = _ui.update { it.copy(enableDayBefore = on) }
    fun toggleTwoHours(on: Boolean) = _ui.update { it.copy(enableTwoHoursBefore = on) }
    fun toggleOnDay(on: Boolean) = _ui.update { it.copy(enableOnDay = on) }

    fun setDailySummaryTime(t: LocalTime) = _ui.update { it.copy(dailySummaryTime = t) }
    fun setAgendaRolloverTime(t: LocalTime) = _ui.update { it.copy(agendaRolloverTime = t) }
    fun setFirstEventInfoTime(t: LocalTime) = _ui.update { it.copy(firstEventInfoTime = t) }

    fun save() {
        val ctx = getApplication<Application>()
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true) }
            RecurrencePrefs.set(ctx, s.toMapping())
            RecurrencePrefs.setSwitches(ctx, s.enableDayBefore, s.enableTwoHoursBefore, s.enableOnDay)
            AppPrefs.setTimes(ctx, s.dailySummaryTime, s.agendaRolloverTime, s.firstEventInfoTime)
            _ui.update { it.copy(isSaving = false) }
        }
    }
}
