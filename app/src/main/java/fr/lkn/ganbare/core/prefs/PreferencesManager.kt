package fr.lkn.ganbare.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val KEY_ICAL_URL = stringPreferencesKey("ical_url")
        private val KEY_DAILY_ENABLED = booleanPreferencesKey("daily_enabled")
        private val KEY_DAILY_HOUR = intPreferencesKey("daily_hour")
        private val KEY_DAILY_MINUTE = intPreferencesKey("daily_minute")
    }

    data class Settings(
        val icalUrl: String = "",
        val dailyEnabled: Boolean = true,
        val dailyTime: LocalTime = LocalTime.of(18, 0)
    )

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            icalUrl = p[KEY_ICAL_URL] ?: "",
            dailyEnabled = p[KEY_DAILY_ENABLED] ?: true,
            dailyTime = LocalTime.of(
                p[KEY_DAILY_HOUR] ?: 18,
                p[KEY_DAILY_MINUTE] ?: 0
            )
        )
    }

    suspend fun updateIcalUrl(url: String) = context.dataStore.edit { it[KEY_ICAL_URL] = url }
    suspend fun updateDailyEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_DAILY_ENABLED] = enabled }
    suspend fun updateDailyTime(time: LocalTime) = context.dataStore.edit {
        it[KEY_DAILY_HOUR] = time.hour
        it[KEY_DAILY_MINUTE] = time.minute
    }
}
