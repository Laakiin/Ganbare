package fr.lkn.ganbare.ui.screens

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.domain.calendar.CalendarEvent
import fr.lkn.ganbare.domain.calendar.CalendarRepositoryImpl
import fr.lkn.ganbare.ui.vm.PlanningViewModel
import fr.lkn.ganbare.ui.vm.SettingsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun PlanningScreen(
    viewModel: PlanningViewModel = run {
        val ctx = LocalContext.current.applicationContext
        val repo = remember { CalendarRepositoryImpl(ctx) }
        val prefs = remember { PreferencesManager(ctx) }
        viewModel(factory = PlanningViewModel.factory(repo, prefs))
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as Application

    // ➜ Au lancement : tentative de refresh ICS (best-effort)
    var lastDownloadOk by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        val ok = SettingsViewModel.refreshIcsFromStoredUrl(app)
        lastDownloadOk = ok
        if (ok) SettingsViewModel.broadcastRefresh(app)
    }

    val icsFile = SettingsViewModel.activeIcsFile(app)
    val hasLocal = icsFile.exists() && icsFile.length() > 0L

    Column(Modifier.fillMaxSize()) {
        DateNavigator(
            date = state.selectedDate,
            onPrev = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onResetAuto = viewModel::resetToAuto,
            onPickDate = viewModel::jumpTo
        )

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Text(
                    text = "Erreur : ${state.error}",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
                if (lastDownloadOk == false && !hasLocal) {
                    Text(
                        text = "Impossible de se connecter et aucun fichier ICS enregistré.",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            else -> {
                EventsList(events = state.events)
            }
        }
    }
}

/* --------------------------------- Header / navigation --------------------------------- */

@Composable
private fun DateNavigator(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetAuto: () -> Unit,
    onPickDate: (LocalDate) -> Unit
) {
    val locale = Locale.getDefault()
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    val ctx = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Jour précédent")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.format(dateFmt).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                },
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onResetAuto) { Text("Revenir (auto)") }
                TextButton(onClick = {
                    showDatePickerDialog(ctx, date) { picked -> onPickDate(picked) }
                }) { Text("Choisir un jour") }
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "Jour suivant")
        }
    }
}

/* --------------------------------- Liste + pauses --------------------------------- */

private sealed interface AgendaItem { val key: String }
private data class AgendaEvent(val event: CalendarEvent) : AgendaItem {
    override val key: String get() = "e_${event.id}"
}
private data class AgendaPause(
    val fromMs: Long,
    val toMs: Long,
    val minutes: Long
) : AgendaItem {
    override val key: String get() = "p_${fromMs}_${toMs}"
}

@Composable
private fun EventsList(events: List<CalendarEvent>) {
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    val items = remember(events) { buildAgendaWithPauses(events) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (items.isEmpty()) {
            item {
                Text(
                    "Aucun cours/événement pour ce jour.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is AgendaPause -> {
                        PauseIntersticeOnDivider(item.minutes)
                    }
                    is AgendaEvent -> {
                        val ev = item.event
                        val start = Instant.ofEpochMilli(ev.startEpochMillis).atZone(zone)
                        val end = Instant.ofEpochMilli(ev.endEpochMillis).atZone(zone)

                        val isExam = remember(ev.title) {
                            ev.title.contains("exam", ignoreCase = true) ||
                                    ev.title.contains("examen", ignoreCase = true)
                        }

                        val examColor = Color(0xFFFF1744) // rouge très pétant
                        val headColor = if (isExam) examColor else MaterialTheme.colorScheme.onSurface
                        val subColor = if (isExam) examColor else MaterialTheme.colorScheme.onSurfaceVariant

                        ListItem(
                            headlineContent = {
                                Text(
                                    ev.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = headColor
                                )
                            },
                            supportingContent = {
                                val loc = ev.location?.takeIf { it.isNotBlank() } ?: ""
                                Text(
                                    "${start.toLocalTime().format(timeFmt)} – ${end.toLocalTime().format(timeFmt)}  $loc",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = subColor
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

private fun buildAgendaWithPauses(events: List<CalendarEvent>): List<AgendaItem> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedBy { it.startEpochMillis }
    val out = mutableListOf<AgendaItem>()
    var prevEnd: Long? = null
    for (ev in sorted) {
        if (prevEnd != null) {
            val gapMin = ((ev.startEpochMillis - prevEnd!!) / 60000L)
            if (gapMin > 0) {
                out += AgendaPause(
                    fromMs = prevEnd!!,
                    toMs = ev.startEpochMillis,
                    minutes = gapMin
                )
            }
        }
        out += AgendaEvent(ev)
        prevEnd = ev.endEpochMillis
    }
    return out
}

/* -------- Pause interstice : badge centré + ligne dessous -------- */

@Composable
private fun PauseIntersticeOnDivider(gapMinutes: Long) {
    val text = "Pause — ${formatPause(gapMinutes)}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

/* --------------------------------- Helpers --------------------------------- */

private fun formatPause(minutes: Long): String {
    val m = abs(minutes)
    val h = m / 60
    val mm = m % 60
    return when {
        h > 0 && mm > 0 -> "${h} h ${mm} min"
        h > 0 -> "${h} h"
        else -> "${mm} min"
    }
}

private fun showDatePickerDialog(
    context: Context,
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(LocalDate.of(year, month + 1, dayOfMonth))
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}
