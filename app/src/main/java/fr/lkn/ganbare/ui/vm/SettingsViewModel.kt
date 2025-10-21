package fr.lkn.ganbare.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.prefs.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val icalUrl: String = "",
    val recapEnabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
    val timeError: String? = null,
    val isSaving: Boolean = false,
    val savedOnce: Boolean = false
)

class SettingsViewModel(
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collectLatest { s: Settings ->
                _state.value = _state.value.copy(
                    icalUrl = s.icalUrl,
                    recapEnabled = s.recapEnabled,
                    hour = s.recapHour,
                    minute = s.recapMinute
                )
            }
        }
    }

    fun onIcalUrlChange(url: String) {
        _state.value = _state.value.copy(icalUrl = url, savedOnce = false)
    }

    fun onRecapEnabledChange(enabled: Boolean) {
        _state.value = _state.value.copy(recapEnabled = enabled, savedOnce = false)
    }

    /** Saisie directe "HH:mm" */
    fun onTimeTextChange(text: String) {
        val regex = Regex("""^\s*([01]?\d|2[0-3]):([0-5]\d)\s*$""")
        if (regex.matches(text)) {
            val (hStr, mStr) = text.trim().split(":")
            val h = hStr.toInt()
            val m = mStr.toInt()
            _state.value = _state.value.copy(hour = h, minute = m, timeError = null, savedOnce = false)
        } else {
            _state.value = _state.value.copy(timeError = "Format attendu HH:mm")
        }
    }

    fun onHourMinuteChange(hour: Int, minute: Int) {
        _state.value = _state.value.copy(hour = hour, minute = minute, timeError = null, savedOnce = false)
    }

    fun saveAll() {
        val st = _state.value
        if (st.timeError != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            prefs.updateAll(
                url = st.icalUrl,
                enabled = st.recapEnabled,
                hour = st.hour,
                minute = st.minute
            )
            _state.value = _state.value.copy(isSaving = false, savedOnce = true)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val pm = PreferencesManager(context.applicationContext)
                return SettingsViewModel(pm) as T
            }
        }
    }
}
