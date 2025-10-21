package fr.lkn.ganbare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.domain.calendar.CalendarEvent
import fr.lkn.ganbare.domain.calendar.CalendarRepositoryImpl
import fr.lkn.ganbare.ui.vm.PlanningViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlanningScreen(
    viewModel: PlanningViewModel = run {
        val ctx = LocalContext.current.applicationContext
        val repo = remember { CalendarRepositoryImpl(ctx) }
        viewModel(factory = PlanningViewModel.factory(repo))
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        DateNavigator(
            date = state.selectedDate,
            onPrev = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onReset = viewModel::resetToTomorrow
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
            }
            else -> {
                EventsList(events = state.events)
            }
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    val locale = Locale.getDefault()
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)

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
            TextButton(onClick = onReset, content = { Text("Revenir à demain") })
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "Jour suivant")
        }
    }
}

@Composable
private fun EventsList(events: List<CalendarEvent>) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (events.isEmpty()) {
            item {
                Text(
                    "Aucun cours/événement pour ce jour.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(events, key = { it.id }) { event ->
                val start = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone)
                val end = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone)
                ListItem(
                    headlineContent = {
                        Text(
                            event.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        val loc = event.location?.takeIf { it.isNotBlank() } ?: ""
                        Text(
                            "${start.toLocalTime().format(timeFmt)} – ${end.toLocalTime().format(timeFmt)}  $loc",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Divider()
            }
        }
    }
}
