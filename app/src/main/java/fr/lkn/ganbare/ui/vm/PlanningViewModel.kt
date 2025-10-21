package fr.lkn.ganbare.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.domain.calendar.CalendarEvent
import fr.lkn.ganbare.domain.calendar.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class PlanningUiState(
    val selectedDate: LocalDate,
    val events: List<CalendarEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlanningViewModel(
    private val calendarRepository: CalendarRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlanningUiState(
            selectedDate = computeDefaultDate(prefs)
        )
    )
    val state: StateFlow<PlanningUiState> = _state.asStateFlow()

    init {
        loadFor(_state.value.selectedDate)
    }

    fun previousDay() = moveBy(-1)
    fun nextDay() = moveBy(+1)

    /** Revenir à la date par défaut (aujourd’hui avant l’heure de bascule, sinon demain) */
    fun resetToAuto() {
        setSelectedDate(computeDefaultDate(prefs))
    }

    private fun moveBy(deltaDays: Long) {
        setSelectedDate(_state.value.selectedDate.plusDays(deltaDays))
    }

    private fun setSelectedDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date, isLoading = true, error = null) }
        loadFor(date)
    }

    private fun loadFor(date: LocalDate) {
        viewModelScope.launch {
            runCatching { calendarRepository.eventsFor(date) }
                .onSuccess { events ->
                    _state.update { it.copy(events = events, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            events = emptyList(),
                            isLoading = false,
                            error = e.message ?: "Erreur inconnue"
                        )
                    }
                }
        }
    }

    companion object {
        private fun computeDefaultDate(prefs: PreferencesManager): LocalDate {
            val now = LocalDateTime.now()
            val s = prefs.current()
            val switch = LocalTime.of(s.switchHour, s.switchMinute)
            return if (now.toLocalTime() >= switch) LocalDate.now().plusDays(1) else LocalDate.now()
        }

        fun factory(repo: CalendarRepository, prefs: PreferencesManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanningViewModel(repo, prefs) as T
            }
        }
    }
}
