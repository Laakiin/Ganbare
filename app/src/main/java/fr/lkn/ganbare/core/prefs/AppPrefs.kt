package fr.lkn.ganbare.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.appDataStore by preferencesDataStore(name = "app_prefs")

data class TimesSettings(
    val dailySummaryTime: LocalTime,        // heure du récap quotidien
    val agendaRolloverTime: LocalTime,      // heure de bascule “afficher le lendemain”
    val firstEventInfoTime: LocalTime       // heure de la notif “heure du 1er évènement de demain”
)

object AppPrefs {

    // récap quotidien
    private val KEY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
    private val KEY_SUMMARY_MIN = intPreferencesKey("daily_summary_min")

    // bascule d’agenda (afficher le lendemain)
    private val KEY_ROLLOVER_HOUR = intPreferencesKey("agenda_rollover_hour")
    private val KEY_ROLLOVER_MIN = intPreferencesKey("agenda_rollover_min")

    // notif “heure du 1er évènement de demain”
    private val KEY_FIRST_EVT_HOUR = intPreferencesKey("first_evt_info_hour")
    private val KEY_FIRST_EVT_MIN = intPreferencesKey("first_evt_info_min")

    private val DEFAULT_SUMMARY = LocalTime.of(20, 0)
    private val DEFAULT_ROLLOVER = LocalTime.of(18, 0)
    private val DEFAULT_FIRST_EVT = LocalTime.of(20, 0)

    fun flowTimes(context: Context): Flow<TimesSettings> =
        context.appDataStore.data.map { prefs ->
            val sh = prefs[KEY_SUMMARY_HOUR] ?: DEFAULT_SUMMARY.hour
            val sm = prefs[KEY_SUMMARY_MIN] ?: DEFAULT_SUMMARY.minute

            val rh = prefs[KEY_ROLLOVER_HOUR] ?: DEFAULT_ROLLOVER.hour
            val rm = prefs[KEY_ROLLOVER_MIN] ?: DEFAULT_ROLLOVER.minute

            val fh = prefs[KEY_FIRST_EVT_HOUR] ?: DEFAULT_FIRST_EVT.hour
            val fm = prefs[KEY_FIRST_EVT_MIN] ?: DEFAULT_FIRST_EVT.minute

            TimesSettings(
                dailySummaryTime = LocalTime.of(sh, sm),
                agendaRolloverTime = LocalTime.of(rh, rm),
                firstEventInfoTime = LocalTime.of(fh, fm)
            )
        }

    suspend fun getTimes(context: Context): TimesSettings =
        flowTimes(context).first()

    suspend fun setDailySummaryTime(context: Context, time: LocalTime) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_SUMMARY_HOUR] = time.hour
            prefs[KEY_SUMMARY_MIN] = time.minute
        }
    }

    suspend fun setAgendaRolloverTime(context: Context, time: LocalTime) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_ROLLOVER_HOUR] = time.hour
            prefs[KEY_ROLLOVER_MIN] = time.minute
        }
    }

    suspend fun setFirstEventInfoTime(context: Context, time: LocalTime) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_FIRST_EVT_HOUR] = time.hour
            prefs[KEY_FIRST_EVT_MIN] = time.minute
        }
    }

    suspend fun setTimes(
        context: Context,
        dailySummary: LocalTime,
        agendaRollover: LocalTime,
        firstEventInfo: LocalTime
    ) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_SUMMARY_HOUR] = dailySummary.hour
            prefs[KEY_SUMMARY_MIN] = dailySummary.minute
            prefs[KEY_ROLLOVER_HOUR] = agendaRollover.hour
            prefs[KEY_ROLLOVER_MIN] = agendaRollover.minute
            prefs[KEY_FIRST_EVT_HOUR] = firstEventInfo.hour
            prefs[KEY_FIRST_EVT_MIN] = firstEventInfo.minute
        }
    }
}
