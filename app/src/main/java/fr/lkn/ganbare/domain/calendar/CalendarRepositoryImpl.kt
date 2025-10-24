package fr.lkn.ganbare.domain.calendar

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lit UNIQUEMENT le fichier local: filesDir/ics_cache/active.ics
 * (Le téléchargement/maj se fait ailleurs au démarrage.)
 */
class CalendarRepositoryImpl(
    private val context: Context
) : CalendarRepository {

    companion object {
        private const val TAG = "CalendarRepositoryImpl"

        object IcsStore {
            const val DIR = "ics_cache"
            const val FILE = "active.ics"
            fun activeFile(ctx: Context): File = File(File(ctx.filesDir, DIR), FILE)
        }
    }

    /** Impl de l’interface */
    override suspend fun eventsFor(date: LocalDate): List<CalendarEvent> =
        eventsForDate(date)

    /** Helper optionnel */
    suspend fun eventsBetween(start: LocalDate, endInclusive: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val fromEpoch = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toEpoch = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val all = readAllEventsFromLocalIcs()
        return all.filter { it.endEpochMillis >= fromEpoch && it.startEpochMillis <= toEpoch }
            .sortedBy { it.startEpochMillis }
    }

    // ------------------ lecture + parsing ------------------

    private suspend fun eventsForDate(date: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val startEpoch = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endEpochExclusive = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val all = readAllEventsFromLocalIcs()
        return all.filter { evt ->
            evt.endEpochMillis > startEpoch && evt.startEpochMillis < endEpochExclusive
        }.sortedBy { it.startEpochMillis }
    }

    private suspend fun readAllEventsFromLocalIcs(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val file = IcsStore.activeFile(context)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Aucun ICS local: ${file.absolutePath}")
            return@withContext emptyList()
        }
        val raw = try {
            file.readText()
        } catch (t: Throwable) {
            Log.w(TAG, "Lecture ICS échouée", t)
            return@withContext emptyList()
        }

        val events = runCatching { parseIcs(raw) }
            .onFailure { Log.w(TAG, "Parse ICS échoué", it) }
            .getOrElse { emptyList() }

        Log.d(TAG, "ICS local -> ${events.size} évènement(s) parsé(s)")
        return@withContext events
    }

    // ------------------ Parser ICS tolérant ------------------

    private fun parseIcs(icsText: String): List<CalendarEvent> {
        if (icsText.isBlank()) return emptyList()

        val unfolded = unfoldIcsLines(icsText)
        val lines = unfolded.lines()

        val events = mutableListOf<CalendarEvent>()

        var inEvent = false
        val props = mutableMapOf<String, MutableList<String>>()
        var tzidStart: String? = null
        var tzidEnd: String? = null
        var isStartDateOnly: Boolean = false // VALUE=DATE pour DTSTART
        var durationIso: String? = null

        fun flushEvent() {
            if (!inEvent) return
            inEvent = false

            val summary = firstProp(props, "SUMMARY") ?: "(Sans titre)"
            val location = firstProp(props, "LOCATION")
            val uid = firstProp(props, "UID")

            val dtStartStr = firstProp(props, "DTSTART")
            val dtEndStr = firstProp(props, "DTEND")
            val hasEndProp = !dtEndStr.isNullOrBlank()

            val startEpoch = parseIcsDateToEpoch(dtStartStr, tzidStart, dateOnly = isStartDateOnly)

            // Déterminer end:
            val endEpoch: Long? = when {
                hasEndProp -> parseIcsDateToEpoch(dtEndStr, tzidEnd, dateOnly = isDateOnlyValue(dtEndStr))
                !hasEndProp && durationIso != null -> {
                    val base = startEpoch
                    if (base == null) null else runCatching {
                        val d = Duration.parse(durationIso)
                        base + d.toMillis()
                    }.getOrNull()
                }
                // all-day sans DTEND -> +1j
                !hasEndProp && isStartDateOnly -> startEpoch?.let { it + Duration.ofDays(1).toMillis() - 1 }
                // défaut: +1h
                else -> startEpoch?.let { it + Duration.ofHours(1).toMillis() }
            }

            if (startEpoch != null && endEpoch != null && endEpoch >= startEpoch) {
                val id = uid ?: buildId(summary, startEpoch, endEpoch, location)
                events += CalendarEvent(
                    id = id,
                    title = summary,
                    startEpochMillis = startEpoch,
                    endEpochMillis = endEpoch,
                    location = location
                )
            }

            props.clear()
            tzidStart = null
            tzidEnd = null
            isStartDateOnly = false
            durationIso = null
        }

        for (ln in lines) {
            when {
                ln.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    props.clear()
                    tzidStart = null
                    tzidEnd = null
                    isStartDateOnly = false
                    durationIso = null
                }
                ln.equals("END:VEVENT", ignoreCase = true) -> {
                    flushEvent()
                }
                inEvent -> {
                    val idx = ln.indexOf(':')
                    if (idx <= 0) continue
                    val left = ln.substring(0, idx)
                    val value = ln.substring(idx + 1).trim()
                    val propName = left.substringBefore(';').uppercase(Locale.ROOT)
                    val params = left.substringAfter(';', missingDelimiterValue = "")

                    when (propName) {
                        "DTSTART" -> {
                            val p = parseParams(params)
                            // Conserver TZID si présent (ne pas l’écraser par la suite)
                            p["TZID"]?.let { if (!it.isNullOrBlank()) tzidStart = it }
                            val valFlag = p["VALUE"]?.uppercase(Locale.ROOT)
                            isStartDateOnly = (valFlag == "DATE") || isDateOnlyValue(value)
                            props.getOrPut(propName) { mutableListOf() }.add(value)
                        }
                        "DTEND" -> {
                            val p = parseParams(params)
                            p["TZID"]?.let { if (!it.isNullOrBlank()) tzidEnd = it }
                            props.getOrPut(propName) { mutableListOf() }.add(value)
                        }
                        "DURATION" -> {
                            durationIso = value // ex: PT1H30M
                            props.getOrPut(propName) { mutableListOf() }.add(value)
                        }
                        else -> {
                            props.getOrPut(propName) { mutableListOf() }.add(value)
                        }
                    }
                }
            }
        }
        flushEvent()

        return events.sortedBy { it.startEpochMillis }
    }

    /** Déplie les lignes RFC 5545 (continuations débutant par espace/tab). */
    private fun unfoldIcsLines(src: String): String {
        val out = StringBuilder()
        val iter = src.split("\r\n", "\n").iterator()
        var current: String? = null
        while (iter.hasNext()) {
            val line = iter.next()
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current = (current ?: "") + line.drop(1)
            } else {
                if (current != null) out.append(current).append('\n')
                current = line
            }
        }
        if (current != null) out.append(current)
        return out.toString()
    }

    private fun firstProp(map: Map<String, List<String>>, key: String): String? =
        map[key]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun parseParams(params: String?): Map<String, String?> {
        if (params.isNullOrBlank()) return emptyMap()
        return params.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null
            else part.substring(0, eq).trim().uppercase(Locale.ROOT) to part.substring(eq + 1).trim()
        }.toMap()
    }

    private fun isDateOnlyValue(v: String?): Boolean =
        v != null && v.length == 8 && v.all { it.isDigit() } // yyyyMMdd

    private fun parseIcsDateToEpoch(value: String?, tzid: String?, dateOnly: Boolean): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            when {
                dateOnly -> {
                    // yyyyMMdd à minuit (zone système)
                    val d = parseDateOnly(value)
                    d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                value.endsWith("Z") -> {
                    val core = value.removeSuffix("Z")
                    val dt = parseWithPatterns(core, dateTime = true)
                    dt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
                value.contains('T') -> {
                    val ldt = parseWithPatterns(value, dateTime = true)
                    val zone = tzid?.runCatching { ZoneId.of(this) }?.getOrNull() ?: ZoneId.systemDefault()
                    ldt.atZone(zone).toInstant().toEpochMilli()
                }
                else -> {
                    // fallback : traiter comme date-only si jamais
                    val d = parseDateOnly(value)
                    d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "parseIcsDateToEpoch échec pour '$value' (tzid=$tzid, dateOnly=$dateOnly)", t)
            null
        }
    }

    private fun parseWithPatterns(s: String, dateTime: Boolean): LocalDateTime {
        val patterns = if (dateTime) listOf(
            "yyyyMMdd'T'HHmmss",
            "yyyyMMdd'T'HHmm"
        ) else listOf("yyyyMMdd")
        for (p in patterns) {
            try {
                val fmt = DateTimeFormatter.ofPattern(p, Locale.ROOT)
                return LocalDateTime.parse(s, fmt)
            } catch (_: DateTimeParseException) { /* next */ }
        }
        // Ultime fallback (peu probable) – tente un Instant ISO
        return LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC)
    }

    private fun parseDateOnly(s: String): LocalDate {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
        return LocalDate.parse(s, fmt)
    }

    private fun buildId(title: String, start: Long, end: Long, location: String?): String {
        val base = "${title.trim()}|$start|$end|${location ?: ""}"
        return base.hashCode().toString()
    }
}
