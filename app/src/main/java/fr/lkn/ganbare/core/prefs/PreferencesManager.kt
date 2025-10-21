package fr.lkn.ganbare.core.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PREFS_NAME = "ganbare_prefs"

// Récap (notif quotidienne)
private const val KEY_ICAL_URL = "ical_url"
private const val KEY_RECAP_ENABLED = "recap_enabled"
private const val KEY_RECAP_HOUR = "recap_hour"
private const val KEY_RECAP_MINUTE = "recap_minute"

// Bascule J+1 (vue Planning)
private const val KEY_SWITCH_HOUR = "switch_hour"
private const val KEY_SWITCH_MINUTE = "switch_minute"

// Valeurs par défaut
private const val DEFAULT_ICAL_URL = ""
private const val DEFAULT_RECAP_ENABLED = false
private const val DEFAULT_RECAP_HOUR = 20
private const val DEFAULT_RECAP_MINUTE = 0

// Par défaut, on bascule vers J+1 à 18:00
private const val DEFAULT_SWITCH_HOUR = 18
private const val DEFAULT_SWITCH_MINUTE = 0

data class Settings(
    val icalUrl: String = DEFAULT_ICAL_URL,
    val recapEnabled: Boolean = DEFAULT_RECAP_ENABLED,
    val recapHour: Int = DEFAULT_RECAP_HOUR,     // 0..23
    val recapMinute: Int = DEFAULT_RECAP_MINUTE, // 0..59
    val switchHour: Int = DEFAULT_SWITCH_HOUR,   // 0..23
    val switchMinute: Int = DEFAULT_SWITCH_MINUTE // 0..59
)

class PreferencesManager(appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _settingsFlow = MutableStateFlow(readAll())
    val settingsFlow: StateFlow<Settings> = _settingsFlow

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settingsFlow.value = readAll()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readAll(): Settings {
        return Settings(
            icalUrl = prefs.getString(KEY_ICAL_URL, DEFAULT_ICAL_URL) ?: DEFAULT_ICAL_URL,
            recapEnabled = prefs.getBoolean(KEY_RECAP_ENABLED, DEFAULT_RECAP_ENABLED),
            recapHour = prefs.getInt(KEY_RECAP_HOUR, DEFAULT_RECAP_HOUR),
            recapMinute = prefs.getInt(KEY_RECAP_MINUTE, DEFAULT_RECAP_MINUTE),
            switchHour = prefs.getInt(KEY_SWITCH_HOUR, DEFAULT_SWITCH_HOUR),
            switchMinute = prefs.getInt(KEY_SWITCH_MINUTE, DEFAULT_SWITCH_MINUTE)
        )
    }

    fun current(): Settings = readAll()

    // --- Récap (notif) ---
    fun setIcalUrl(url: String) {
        scope.launch { prefs.edit().putString(KEY_ICAL_URL, url.trim()).apply() }
    }

    fun setRecapEnabled(enabled: Boolean) {
        scope.launch { prefs.edit().putBoolean(KEY_RECAP_ENABLED, enabled).apply() }
    }

    fun setRecapTime(hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        scope.launch {
            prefs.edit()
                .putInt(KEY_RECAP_HOUR, h)
                .putInt(KEY_RECAP_MINUTE, m)
                .apply()
        }
    }

    fun updateAll(url: String, enabled: Boolean, hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        scope.launch {
            prefs.edit()
                .putString(KEY_ICAL_URL, url.trim())
                .putBoolean(KEY_RECAP_ENABLED, enabled)
                .putInt(KEY_RECAP_HOUR, h)
                .putInt(KEY_RECAP_MINUTE, m)
                .apply()
        }
    }

    // --- Heure de bascule vers J+1 (Planning) ---
    fun setSwitchTime(hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        scope.launch {
            prefs.edit()
                .putInt(KEY_SWITCH_HOUR, h)
                .putInt(KEY_SWITCH_MINUTE, m)
                .apply()
        }
    }
}
