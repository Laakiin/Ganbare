package fr.lkn.ganbare.ui.vm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.reminders.Recurrence
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Bus simple
object RefreshBus {
    val agendaRefresh = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun pingAgenda() = agendaRefresh.tryEmit(Unit)
}

data class RecurrenceUiState(
    val p1: Recurrence,
    val p2: Recurrence,
    val p3: Recurrence,
    val p4: Recurrence,
    val enableDayBefore: Boolean,
    val enableTwoHoursBefore: Boolean,
    val enableOnDay: Boolean,
    val dailySummaryTime: LocalTime,
    val firstEventInfoTime: LocalTime,
    val agendaRolloverTime: LocalTime,
    val enableDailySummary: Boolean,
    val enableFirstEventInfo: Boolean,
    val icalUrl: String,
    val isSaving: Boolean = false
) {
    companion object {
        fun default() = RecurrenceUiState(
            p1 = Recurrence.DAILY,
            p2 = Recurrence.WEEKLY,
            p3 = Recurrence.MONTHLY,
            p4 = Recurrence.NONE,
            enableDayBefore = true,
            enableTwoHoursBefore = true,
            enableOnDay = true,
            dailySummaryTime = LocalTime.of(20, 0),
            firstEventInfoTime = LocalTime.of(20, 0),
            agendaRolloverTime = LocalTime.of(18, 0),
            enableDailySummary = true,
            enableFirstEventInfo = true,
            icalUrl = ""
        )
    }
}

// ❗ DataStore unique (pas de doublon ailleurs)
private val Application.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "SettingsViewModel"
        const val ACTION_REFRESH_AGENDA = "fr.lkn.ganbare.action.REFRESH_AGENDA"

        private fun icsDir(ctx: Context) = File(ctx.filesDir, "ics_cache")
        fun activeIcsFile(ctx: Context) = File(icsDir(ctx), "active.ics")

        /** Télécharge l’ICS à active.ics (écriture atomique). */
        suspend fun downloadIcsToCache(ctx: Context, urlStr: String): Boolean = withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout  = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "text/calendar, text/plain, */*")
                    setRequestProperty("User-Agent", "Ganbare/1.0 (Android)")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "HTTP $code sur $urlStr")
                    conn.disconnect()
                    return@withContext false
                }

                val dir = icsDir(ctx)
                if (!dir.exists()) dir.mkdirs()
                val tmp = File(dir, "active.tmp")
                val dst = activeIcsFile(ctx)

                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                if (dst.exists()) dst.delete()
                val renamed = tmp.renameTo(dst)
                if (!renamed) {
                    FileOutputStream(dst).use { out ->
                        tmp.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    tmp.delete()
                }
                Log.d(TAG, "ICS mis à jour (${dst.length()} o) -> ${dst.absolutePath}")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Échec download ICS: $urlStr", t)
                false
            }
        }

        /** Relit l’URL iCal stockée et tente un refresh du fichier local. */
        suspend fun refreshIcsFromStoredUrl(app: Application): Boolean {
            val prefs = app.settingsDataStore.data.first()
            val url = prefs[Keys.ICAL_URL] ?: ""
            if (url.isBlank()) return false
            return downloadIcsToCache(app, url)
        }

        /** Lit l’URL iCal enregistrée (si besoin ailleurs). */
        suspend fun readStoredIcalUrl(app: Application): String {
            val prefs = app.settingsDataStore.data.first()
            return prefs[Keys.ICAL_URL] ?: ""
        }

        /** Indique si on a un fichier ICS local et sa taille. */
        fun hasLocalIcs(ctx: Context): Pair<Boolean, Long> {
            val f = activeIcsFile(ctx)
            return (f.exists() && f.length() > 0L) to f.length()
        }

        /** Notifie qu’il faut recharger l’agenda. */
        fun broadcastRefresh(ctx: Context) {
            try {
                ctx.sendBroadcast(Intent(ACTION_REFRESH_AGENDA).setPackage(ctx.packageName))
            } catch (t: Throwable) {
                Log.w(TAG, "Échec broadcast refresh agenda", t)
            }
        }
    }

    private object Keys {
        val P1 = stringPreferencesKey("recurrence_p1")
        val P2 = stringPreferencesKey("recurrence_p2")
        val P3 = stringPreferencesKey("recurrence_p3")
        val P4 = stringPreferencesKey("recurrence_p4")

        val ENABLE_DAY_BEFORE = booleanPreferencesKey("enable_day_before")
        val ENABLE_TWO_HOURS  = booleanPreferencesKey("enable_two_hours")
        val ENABLE_ON_DAY     = booleanPreferencesKey("enable_on_day")

        val DAILY_SUMMARY_TIME    = stringPreferencesKey("daily_summary_time")
        val FIRST_EVENT_INFO_TIME = stringPreferencesKey("first_event_info_time")
        val AGENDA_ROLLOVER_TIME  = stringPreferencesKey("agenda_rollover_time")

        val ENABLE_DAILY_SUMMARY    = booleanPreferencesKey("enable_daily_summary")
        val ENABLE_FIRST_EVENT_INFO = booleanPreferencesKey("enable_first_event_info")

        val ICAL_URL = stringPreferencesKey("ical_url")
    }

    private val dataStore = app.settingsDataStore
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    private val _ui = MutableStateFlow(RecurrenceUiState.default())
    val ui: StateFlow<RecurrenceUiState> = _ui

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _ui.value = prefs.toState().copy(isSaving = false)
        }
    }

    private fun Preferences.toState(): RecurrenceUiState {
        fun readRec(key: Preferences.Key<String>, def: Recurrence) =
            this[key]?.runCatching { Recurrence.valueOf(this) }?.getOrNull() ?: def
        fun readTime(key: Preferences.Key<String>, def: LocalTime) =
            this[key]?.runCatching { LocalTime.parse(this, timeFmt) }?.getOrNull() ?: def

        return RecurrenceUiState(
            p1 = readRec(Keys.P1, Recurrence.DAILY),
            p2 = readRec(Keys.P2, Recurrence.WEEKLY),
            p3 = readRec(Keys.P3, Recurrence.MONTHLY),
            p4 = readRec(Keys.P4, Recurrence.NONE),
            enableDayBefore = this[Keys.ENABLE_DAY_BEFORE] ?: true,
            enableTwoHoursBefore = this[Keys.ENABLE_TWO_HOURS] ?: true,
            enableOnDay = this[Keys.ENABLE_ON_DAY] ?: true,
            dailySummaryTime = readTime(Keys.DAILY_SUMMARY_TIME, LocalTime.of(20, 0)),
            firstEventInfoTime = readTime(Keys.FIRST_EVENT_INFO_TIME, LocalTime.of(20, 0)),
            agendaRolloverTime = readTime(Keys.AGENDA_ROLLOVER_TIME, LocalTime.of(18, 0)),
            enableDailySummary = this[Keys.ENABLE_DAILY_SUMMARY] ?: true,
            enableFirstEventInfo = this[Keys.ENABLE_FIRST_EVENT_INFO] ?: true,
            icalUrl = this[Keys.ICAL_URL] ?: ""
        )
    }

    fun setP1(r: Recurrence) = update { it.copy(p1 = r) }
    fun setP2(r: Recurrence) = update { it.copy(p2 = r) }
    fun setP3(r: Recurrence) = update { it.copy(p3 = r) }
    fun setP4(r: Recurrence) = update { it.copy(p4 = r) }

    fun toggleDayBefore(v: Boolean) = update { it.copy(enableDayBefore = v) }
    fun toggleTwoHours(v: Boolean)  = update { it.copy(enableTwoHoursBefore = v) }
    fun toggleOnDay(v: Boolean)     = update { it.copy(enableOnDay = v) }

    fun setDailySummaryTime(t: LocalTime)   = update { it.copy(dailySummaryTime = t) }
    fun setFirstEventInfoTime(t: LocalTime) = update { it.copy(firstEventInfoTime = t) }
    fun setAgendaRolloverTime(t: LocalTime) = update { it.copy(agendaRolloverTime = t) }

    fun toggleEnableDailySummary(v: Boolean)   = update { it.copy(enableDailySummary = v) }
    fun toggleEnableFirstEventInfo(v: Boolean) = update { it.copy(enableFirstEventInfo = v) }

    fun setIcalUrl(url: String) = update { it.copy(icalUrl = url) }

    private fun update(block: (RecurrenceUiState) -> RecurrenceUiState) {
        _ui.value = block(_ui.value)
    }

    fun save() {
        val snapshot = _ui.value.copy(isSaving = true)
        _ui.value = snapshot
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[Keys.P1] = snapshot.p1.name
                prefs[Keys.P2] = snapshot.p2.name
                prefs[Keys.P3] = snapshot.p3.name
                prefs[Keys.P4] = snapshot.p4.name

                prefs[Keys.ENABLE_DAY_BEFORE] = snapshot.enableDayBefore
                prefs[Keys.ENABLE_TWO_HOURS]  = snapshot.enableTwoHoursBefore
                prefs[Keys.ENABLE_ON_DAY]     = snapshot.enableOnDay

                prefs[Keys.DAILY_SUMMARY_TIME]    = snapshot.dailySummaryTime.format(timeFmt)
                prefs[Keys.FIRST_EVENT_INFO_TIME] = snapshot.firstEventInfoTime.format(timeFmt)
                prefs[Keys.AGENDA_ROLLOVER_TIME]  = snapshot.agendaRolloverTime.format(timeFmt)

                prefs[Keys.ENABLE_DAILY_SUMMARY]    = snapshot.enableDailySummary
                prefs[Keys.ENABLE_FIRST_EVENT_INFO] = snapshot.enableFirstEventInfo

                prefs[Keys.ICAL_URL] = snapshot.icalUrl
            }
            _ui.value = _ui.value.copy(isSaving = false)
        }
    }

    /** Enregistre l’URL iCal, tente le refresh du fichier local, puis notifie. */
    fun applyIcal() {
        val urlNow = _ui.value.icalUrl.trim()
        save()
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (urlNow.isNotBlank()) {
                downloadIcsToCache(ctx, urlNow) // best-effort
            } else {
                withContext(Dispatchers.IO) { activeIcsFile(ctx).delete() }
            }
            broadcastRefresh(ctx)
            debugNotify("Planning", "Lien iCal enregistré — rafraîchissement demandé")
        }
    }

    // Notifications de test
    fun testTaskDayBefore()  = debugNotify("Test tâche", "Rappel la veille")
    fun testTaskTwoHours()   = debugNotify("Test tâche", "Rappel 2h avant")
    fun testTaskOnDay()      = debugNotify("Test tâche", "Rappel jour J")
    fun testDailySummary()   = debugNotify("Test planning", "Récap quotidien")
    fun testFirstEventInfo() = debugNotify("Test planning", "Premier cours demain")

    private fun debugNotify(title: String, text: String) {
        val ctx = getApplication<Application>()
        val channelId = "debug_tests"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Tests de notifications", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notif = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }
}
