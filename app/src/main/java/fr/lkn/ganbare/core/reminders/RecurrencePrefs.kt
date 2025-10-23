package fr.lkn.ganbare.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.core.reminders.RecurrenceMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension DataStore au niveau du context (fichier top-level)
private val Context.recurrenceDataStore by preferencesDataStore(name = "recurrence_prefs")

data class ReminderSettings(
    val mapping: RecurrenceMapping,
    val enableDayBefore: Boolean,
    val enableTwoHoursBefore: Boolean,
    val enableOnDay: Boolean
)

object RecurrencePrefs {

    // Récurrence P1..P4
    private val KEY_P1 = stringPreferencesKey("recurrence_p1")
    private val KEY_P2 = stringPreferencesKey("recurrence_p2")
    private val KEY_P3 = stringPreferencesKey("recurrence_p3")
    private val KEY_P4 = stringPreferencesKey("recurrence_p4")

    // Switchs systèmes
    private val KEY_ENABLE_DAY_BEFORE = booleanPreferencesKey("enable_day_before")
    private val KEY_ENABLE_TWO_HOURS = booleanPreferencesKey("enable_two_hours_before")
    private val KEY_ENABLE_ON_DAY = booleanPreferencesKey("enable_on_day")

    private fun String?.toRecurrenceOr(default: Recurrence) =
        runCatching { Recurrence.valueOf(this ?: "") }.getOrElse { default }

    /** Flow uniquement du mapping (compat rétro) */
    fun flow(context: Context): Flow<RecurrenceMapping> =
        context.recurrenceDataStore.data.map { prefs ->
            RecurrenceMapping(
                p1 = prefs[KEY_P1].toRecurrenceOr(RecurrenceMapping.default().p1),
                p2 = prefs[KEY_P2].toRecurrenceOr(RecurrenceMapping.default().p2),
                p3 = prefs[KEY_P3].toRecurrenceOr(RecurrenceMapping.default().p3),
                p4 = prefs[KEY_P4].toRecurrenceOr(RecurrenceMapping.default().p4),
            )
        }

    /** Flow complet : mapping + switchs */
    fun flowSettings(context: Context): Flow<ReminderSettings> =
        context.recurrenceDataStore.data.map { prefs ->
            ReminderSettings(
                mapping = RecurrenceMapping(
                    p1 = prefs[KEY_P1].toRecurrenceOr(RecurrenceMapping.default().p1),
                    p2 = prefs[KEY_P2].toRecurrenceOr(RecurrenceMapping.default().p2),
                    p3 = prefs[KEY_P3].toRecurrenceOr(RecurrenceMapping.default().p3),
                    p4 = prefs[KEY_P4].toRecurrenceOr(RecurrenceMapping.default().p4),
                ),
                enableDayBefore = prefs[KEY_ENABLE_DAY_BEFORE] ?: true,
                enableTwoHoursBefore = prefs[KEY_ENABLE_TWO_HOURS] ?: true,
                enableOnDay = prefs[KEY_ENABLE_ON_DAY] ?: true
            )
        }

    suspend fun get(context: Context): RecurrenceMapping =
        flow(context).first()

    suspend fun getSettings(context: Context): ReminderSettings =
        flowSettings(context).first()

    suspend fun set(context: Context, mapping: RecurrenceMapping) {
        context.recurrenceDataStore.edit { prefs ->
            prefs[KEY_P1] = mapping.p1.name
            prefs[KEY_P2] = mapping.p2.name
            prefs[KEY_P3] = mapping.p3.name
            prefs[KEY_P4] = mapping.p4.name
        }
    }

    suspend fun setForBand(context: Context, bandP1toP4: Int, recurrence: Recurrence) {
        context.recurrenceDataStore.edit { prefs ->
            when (bandP1toP4.coerceIn(1, 4)) {
                1 -> prefs[KEY_P1] = recurrence.name
                2 -> prefs[KEY_P2] = recurrence.name
                3 -> prefs[KEY_P3] = recurrence.name
                4 -> prefs[KEY_P4] = recurrence.name
            }
        }
    }

    suspend fun setSwitches(
        context: Context,
        enableDayBefore: Boolean,
        enableTwoHoursBefore: Boolean,
        enableOnDay: Boolean
    ) {
        context.recurrenceDataStore.edit { prefs ->
            prefs[KEY_ENABLE_DAY_BEFORE] = enableDayBefore
            prefs[KEY_ENABLE_TWO_HOURS] = enableTwoHoursBefore
            prefs[KEY_ENABLE_ON_DAY] = enableOnDay
        }
    }
}
