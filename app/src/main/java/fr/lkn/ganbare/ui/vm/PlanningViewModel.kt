package fr.lkn.ganbare.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.domain.calendar.CalendarEvent
import fr.lkn.ganbare.domain.calendar.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanningUiState(
    val selectedDate: LocalDate = LocalDate.now().plusDays(1), // Demain par défaut
    val events: List<CalendarEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlanningViewModel(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanningUiState())
    val state: StateFlow<PlanningUiState> = _state.asStateFlow()

    init {
        loadFor(_state.value.selectedDate)
    }

    fun previousDay() = moveBy(-1)
    fun nextDay() = moveBy(+1)

    fun resetToTomorrow() {
        setSelectedDate(LocalDate.now().plusDays(1))
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
        fun factory(repo: CalendarRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanningViewModel(repo) as T
            }
        }
    }
}
