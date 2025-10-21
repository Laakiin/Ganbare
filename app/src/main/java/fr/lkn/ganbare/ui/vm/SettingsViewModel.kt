package fr.lkn.ganbare.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.core.work.Scheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val settings = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, PreferencesManager.Settings())

    fun applySettings(
        icalUrl: String,
        dailyEnabled: Boolean,
        dailyHour: Int,
        dailyMinute: Int
    ) {
        viewModelScope.launch {
            prefs.updateIcalUrl(icalUrl)
            prefs.updateDailyEnabled(dailyEnabled)
            val time = LocalTime.of(dailyHour.coerceIn(0,23), dailyMinute.coerceIn(0,59))
            prefs.updateDailyTime(time)
            if (dailyEnabled) {
                Scheduler.scheduleDailyAt(appContext, time)
            }
        }
    }
}
