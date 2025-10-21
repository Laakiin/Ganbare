package fr.lkn.ganbare.domain.calendar

import java.time.LocalDate

data class CalendarEvent(
    val id: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String? = null
)

/**
 * Contrat d'accès aux événements d'une date donnée.
 * L’implémentation réelle branchera ton parseur iCal + préférences (URL).
 */
interface CalendarRepository {
    suspend fun eventsFor(date: LocalDate): List<CalendarEvent>
}
