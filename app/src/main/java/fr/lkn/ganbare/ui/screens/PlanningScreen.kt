package fr.lkn.ganbare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.lkn.ganbare.core.ical.CourseEvent
import fr.lkn.ganbare.ui.vm.PlanningViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlanningScreen(vm: PlanningViewModel = hiltViewModel()) {
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val events by vm.events.collectAsState()

    LaunchedEffect(Unit) { vm.loadFor() } // Demain par défaut

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Planning (demain)", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.loadFor() }) { Text("Actualiser") }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        if (events.isEmpty() && !loading && error == null) {
            Text("Aucun cours trouvé pour demain.")
        }

        events.forEach { e ->
            CourseRow(e)
            Divider()
        }
    }
}

@Composable
private fun CourseRow(e: CourseEvent) {
    val zone = ZoneId.systemDefault()
    val fmtTime = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val start = fmtTime.format(e.start)
    val end = fmtTime.format(e.end)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(e.title, style = MaterialTheme.typography.titleMedium)
        Text("$start — $end", style = MaterialTheme.typography.bodyMedium)
        if (!e.location.isNullOrBlank()) {
            Text(e.location!!, style = MaterialTheme.typography.bodySmall)
        }
    }
}
