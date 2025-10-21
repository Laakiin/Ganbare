package fr.lkn.ganbare.domain.calendar

import android.content.Context
import fr.lkn.ganbare.core.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Implémentation réelle: télécharge et parse l'iCal de l'URL sauvegardée.
 * Cache en mémoire pour éviter de reparser à chaque appel.
 */
class CalendarRepositoryImpl(
    private val appContext: Context
) : CalendarRepository {

    private val prefs = PreferencesManager(appContext)

    // Cache simple en mémoire
    @Volatile private var cachedUrl: String? = null
    @Volatile private var cachedAtEpochMs: Long = 0L
    @Volatile private var cachedEvents: List<CalendarEvent> = emptyList()

    // Durée max du cache (ms)
    private val cacheTtlMs: Long = 15 * 60 * 1000 // 15 min

    override suspend fun eventsFor(date: LocalDate): List<CalendarEvent> {
        val url = prefs.current().icalUrl.trim()
        if (url.isEmpty()) return emptyList()

        ensureLoaded(url)

        // Filtrer par jour (chevauchement)
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return cachedEvents.filter { ev ->
            ev.startEpochMillis < dayEnd && ev.endEpochMillis > dayStart
        }.sortedBy { it.startEpochMillis }
    }

    private suspend fun ensureLoaded(url: String) {
        val now = System.currentTimeMillis()
        val shouldReload = cachedUrl != url ||
                cachedEvents.isEmpty() ||
                (now - cachedAtEpochMs) > cacheTtlMs

        if (!shouldReload) return

        val ics = fetchIcs(url)
        val events = parseIcs(ics)
        cachedUrl = url
        cachedEvents = events
        cachedAtEpochMs = now
    }

    private suspend fun fetchIcs(urlStr: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        conn.inputStream.use { input ->
            BufferedReader(InputStreamReader(input)).use { br ->
                buildString {
                    var line = br.readLine()
                    while (line != null) {
                        appendLine(line)
                        line = br.readLine()
                    }
                }
            }
        }
    }

    // ---------------- ICS parsing ----------------

    private data class RawEvent(
        val uid: String?,
        val summary: String?,
        val location: String?,
        val dtStart: ZonedDateTime,
        val dtEnd: ZonedDateTime
    )

    private fun parseIcs(icsRaw: String): List<CalendarEvent> {
        // "Unfold" des lignes (les lignes commençant par espace/tab sont la suite de la ligne précédente)
        val unfolded = unfoldIcs(icsRaw)

        // Extraire chaque bloc VEVENT
        val blocks = extractBlocks(unfolded, "VEVENT")

        val events = mutableListOf<RawEvent>()
        val sysZone = ZoneId.systemDefault()

        for (block in blocks) {
            val props = blockToMap(block)

            // DTSTART / DTEND: différents formats (UTC 'Z', TZID=..., VALUE=DATE)
            val start = parseIcsDateTime("DTSTART", props, sysZone) ?: continue
            val end = parseIcsDateTime("DTEND", props, sysZone) ?: start.plusHours(1)

            val uid = props["UID"]?.firstOrNull()?.second
            val summary = props["SUMMARY"]?.firstOrNull()?.second
            val location = props["LOCATION"]?.firstOrNull()?.second

            events += RawEvent(uid, summary, location, start, end)
        }

        // Convertir en CalendarEvent
        return events.map { e ->
            val id = e.uid ?: stableId(e)
            CalendarEvent(
                id = id,
                title = e.summary ?: "(Sans titre)",
                startEpochMillis = e.dtStart.toInstant().toEpochMilli(),
                endEpochMillis = e.dtEnd.toInstant().toEpochMilli(),
                location = e.location
            )
        }
    }

    private fun stableId(e: RawEvent): String {
        val base = listOf(
            e.summary ?: "",
            e.location ?: "",
            e.dtStart.toInstant().toEpochMilli().toString(),
            e.dtEnd.toInstant().toEpochMilli().toString()
        ).joinToString("|")
        return base.hashCode().toString()
    }

    private fun unfoldIcs(ics: String): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        ics.lineSequence().forEach { raw ->
            val line = raw.replace("\r", "")
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line.drop(1))
            } else {
                if (current.isNotEmpty()) out += current.toString()
                current = StringBuilder(line)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun extractBlocks(lines: List<String>, name: String): List<List<String>> {
        val begin = "BEGIN:$name"
        val end = "END:$name"
        val blocks = mutableListOf<List<String>>()
        var cur: MutableList<String>? = null
        for (l in lines) {
            when {
                l.equals(begin, ignoreCase = true) -> cur = mutableListOf()
                l.equals(end, ignoreCase = true) -> {
                    cur?.let { blocks += it.toList() }
                    cur = null
                }
                cur != null -> cur.add(l)
            }
        }
        return blocks
    }

    /**
     * Transforme un bloc en map: NOM -> liste de paires (params, valeur)
     * Exemple:
     *   DTSTART;TZID=Europe/Paris:20250909T111500
     * -> key = "DTSTART", params = "TZID=Europe/Paris", value = "20250909T111500"
     */
    private fun blockToMap(block: List<String>): Map<String, List<Pair<Map<String, String>, String>>> {
        val map = linkedMapOf<String, MutableList<Pair<Map<String, String>, String>>>()
        for (line in block) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val head = line.substring(0, idx)
            val value = line.substring(idx + 1)
            val headParts = head.split(';')
            val name = headParts.first().uppercase(Locale.ROOT).trim()
            val params = mutableMapOf<String, String>()
            for (i in 1 until headParts.size) {
                val p = headParts[i]
                val eq = p.indexOf('=')
                if (eq > 0) {
                    val k = p.substring(0, eq).uppercase(Locale.ROOT).trim()
                    val v = p.substring(eq + 1).trim()
                    params[k] = v
                } else {
                    params[p.uppercase(Locale.ROOT).trim()] = ""
                }
            }
            map.getOrPut(name) { mutableListOf() }.add(params to value)
        }
        return map
    }

    /**
     * Parse un champ DTSTART/DTEND avec gestion:
     *  - suffixe Z (UTC)
     *  - TZID=... (ex: Europe/Paris)
     *  - VALUE=DATE (all-day yyyyMMdd)
     *  - seconds optionnelles (yyyyMMdd'T'HHmm[ss])
     */
    private fun parseIcsDateTime(
        name: String,
        props: Map<String, List<Pair<Map<String, String>, String>>>,
        defaultZone: ZoneId
    ): ZonedDateTime? {
        val entries = props[name] ?: return null
        val (params, raw) = entries.first()

        // All-day ?
        val isAllDay = params.any { it.key.equals("VALUE", true) && it.value.equals("DATE", true) }
        if (isAllDay) {
            val ld = LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE) // yyyyMMdd
            return ld.atStartOfDay(defaultZone)
        }

        // TZID ?
        val tzid = params.entries.firstOrNull { it.key.equals("TZID", true) }?.value
        val zone = runCatching { if (tzid != null) ZoneId.of(tzid) else defaultZone }.getOrElse { defaultZone }

        // Z (UTC) ?
        val valueNoZ = if (raw.endsWith("Z", true)) raw.dropLast(1) else raw

        val ldt = parseLocalDateTimeFlexible(valueNoZ)
        // Si c'était en UTC (raw se terminait par Z), on considère l'instant UTC puis on convertit vers zone
        return if (raw.endsWith("Z", true)) {
            ZonedDateTime.of(ldt, ZoneId.of("UTC")).withZoneSameInstant(zone)
        } else {
            ZonedDateTime.of(ldt, zone)
        }
    }

    // yyyyMMdd'T'HHmm[ss] -> seconds optionnelles
    private val dtfOptionalSeconds: DateTimeFormatter by lazy {
        DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter(Locale.ROOT)
    }

    private fun parseLocalDateTimeFlexible(v: String): LocalDateTime {
        return runCatching {
            LocalDateTime.parse(v, dtfOptionalSeconds)
        }.getOrElse {
            // Fallback robustes: tronque/complète au besoin
            when {
                v.length >= 15 -> LocalDateTime.parse(v.substring(0, 15), dtfOptionalSeconds) // yyyyMMddTHHmmss
                v.length >= 13 -> LocalDateTime.parse(v.substring(0, 13), dtfOptionalSeconds) // yyyyMMddTHHmm
                v.length == 8  -> LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() // yyyyMMdd
                else -> {
                    // Dernier recours: pad pour atteindre HHmm
                    val base = when {
                        v.length < 13 -> v.padEnd(13, '0')
                        else -> v
                    }
                    LocalDateTime.parse(base.take(15), dtfOptionalSeconds)
                }
            }
        }
    }
}
