package fr.lkn.ganbare.core.ical

import biweekly.Biweekly
import biweekly.component.VEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class CourseEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String?
)

@Singleton
class IcalRepository @Inject constructor(
    private val client: OkHttpClient
) {
    private suspend fun fetchIcs(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string() ?: error("Body vide")
        }
    }

    suspend fun eventsForDate(
        url: String,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CourseEvent> {
        val text = fetchIcs(url)
        val calendars = Biweekly.parse(text).all()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

        val list = mutableListOf<CourseEvent>()
        calendars.forEach { cal ->
            cal.events.forEach { ev ->
                toCourseEvent(ev, zone)?.let { e ->
                    if (!(e.end <= dayStart || e.start >= dayEnd)) {
                        list += e
                    }
                }
            }
        }
        return list.sortedBy { it.start }
    }

    private fun toCourseEvent(ev: VEvent, zone: ZoneId): CourseEvent? {
        val ds = ev.dateStart?.value ?: return null
        val de = ev.dateEnd?.value ?: defaultEnd(ds)
        val title = ev.summary?.value ?: "(Sans titre)"
        val loc = ev.location?.value
        val start = ds.toInstant(zone)
        val end = de.toInstant(zone)
        return CourseEvent(title, start, end, loc)
    }

    private fun defaultEnd(start: Date): Date =
        Date(start.time + 60 * 60 * 1000)

    private fun Date.toInstant(zone: ZoneId): Instant = when (this) {
        is biweekly.util.ICalDate -> {
            if (this.hasTime()) Instant.ofEpochMilli(time)
            else this.toZonedDateTime(zone).toInstant()
        }
        else -> Instant.ofEpochMilli(time)
    }

    private fun biweekly.util.ICalDate.toZonedDateTime(zone: ZoneId) =
        java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), zone)
}
