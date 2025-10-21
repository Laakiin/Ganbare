package fr.lkn.ganbare.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.prefs.Settings
import fr.lkn.ganbare.core.work.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val icalUrl: String = "",
    val recapEnabled: Boolean = false,
    val recapHour: Int = 20,
    val recapMinute: Int = 0,
    val switchHour: Int = 18,
    val switchMinute: Int = 0,
    val isSaving: Boolean = false,
    val savedOnce: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val appContext: Context,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collectLatest { s ->
                _state.value = _state.value.copy(
                    icalUrl = s.icalUrl,
                    recapEnabled = s.recapEnabled,
                    recapHour = s.recapHour,
                    recapMinute = s.recapMinute,
                    switchHour = s.switchHour,
                    switchMinute = s.switchMinute
                )
            }
        }
    }

    // --- edits depuis l'UI ---
    fun onIcalUrlChange(url: String) { _state.value = _state.value.copy(icalUrl = url) }
    fun onRecapEnabledChange(enabled: Boolean) { _state.value = _state.value.copy(recapEnabled = enabled) }
    fun onHourMinuteChange(hour: Int, minute: Int) { _state.value = _state.value.copy(recapHour = hour, recapMinute = minute) }
    fun onSwitchHourMinuteChange(hour: Int, minute: Int) { _state.value = _state.value.copy(switchHour = hour, switchMinute = minute) }

    fun saveAll() {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                // Sauvegarde
                prefs.setIcalUrl(s.icalUrl)
                prefs.setRecapEnabled(s.recapEnabled)
                prefs.setRecapTime(s.recapHour, s.recapMinute)
                prefs.setSwitchTime(s.switchHour, s.switchMinute)

                // Planification IMMÉDIATE selon l’état
                if (s.recapEnabled) {
                    Scheduler.scheduleDailySummary(appContext, s.recapHour, s.recapMinute)
                } else {
                    Scheduler.cancelDailySummary(appContext)
                }

                _state.value = _state.value.copy(isSaving = false, savedOnce = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message ?: "Erreur inconnue")
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val pm = PreferencesManager(context.applicationContext)
                return SettingsViewModel(context.applicationContext, pm) as T
            }
        }
    }
}
