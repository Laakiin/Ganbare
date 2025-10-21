package fr.lkn.ganbare.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lkn.ganbare.core.ical.CourseEvent
import fr.lkn.ganbare.core.ical.IcalRepository
import fr.lkn.ganbare.core.prefs.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val ical: IcalRepository
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _events = MutableStateFlow<List<CourseEvent>>(emptyList())
    val events: StateFlow<List<CourseEvent>> = _events

    fun loadFor(date: LocalDate = LocalDate.now(ZoneId.systemDefault()).plusDays(1)) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val s = prefs.settingsFlow.first()
                if (s.icalUrl.isBlank()) {
                    _events.value = emptyList()
                    _error.value = "Aucune URL iCal configurée."
                } else {
                    _events.value = ical.eventsForDate(s.icalUrl, date)
                }
            } catch (e: Exception) {
                _error.value = "Erreur iCal: ${e.message}"
                _events.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
