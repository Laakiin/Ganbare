package fr.lkn.ganbare.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.ui.vm.TasksViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: TasksViewModel = viewModel()
) {
    val ctx = LocalContext.current

    // --- État UI local pour la création d’une tâche ---
    var title by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var hasDueTime by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf(LocalTime.of(8, 0)) }

    val df = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val tf = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Liste des tâches (depuis VM)
    val tasks by vm.tasks.collectAsState()

    // --- Dialog pickers ---
    fun openDatePicker() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedDate.year)
            set(Calendar.MONTH, selectedDate.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, selectedDate.dayOfMonth)
        }
        DatePickerDialog(
            ctx,
            { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        TimePickerDialog(
            ctx,
            { _, h, m -> selectedTime = LocalTime.of(h, m) },
            selectedTime.hour,
            selectedTime.minute,
            true
        ).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tâches") }) }
    ) { paddings ->

        Column(
            modifier = Modifier
                .padding(paddings)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ---- Formulaire création ----
            Text("Ajouter une tâche", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Date", style = MaterialTheme.typography.labelLarge)
                    Text(selectedDate.format(df), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { openDatePicker() }) { Text("Choisir") }
            }

            // Heure
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(checked = hasDueTime, onCheckedChange = { hasDueTime = it })
                Text("Spécifier une heure")
            }
            if (hasDueTime) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Heure", style = MaterialTheme.typography.labelLarge)
                        Text(selectedTime.format(tf), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { openTimePicker() }) { Text("Choisir") }
                }
            }

            // Priorité simple
            var showPrioMenu by remember { mutableStateOf(false) }
            val priorities = listOf("P1", "P2", "P3", "P4")
            var priorityIndex by remember { mutableStateOf(0) }
            Box {
                OutlinedButton(onClick = { showPrioMenu = true }) {
                    Text("Priorité : ${priorities[priorityIndex]}")
                }
                DropdownMenu(expanded = showPrioMenu, onDismissRequest = { showPrioMenu = false }) {
                    priorities.forEachIndexed { idx, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                priorityIndex = idx
                                showPrioMenu = false
                            }
                        )
                    }
                }
            }

            // Bouton ajouter
            Button(
                onClick = {
                    val timeForDue = if (hasDueTime) selectedTime else LocalTime.of(23, 59)
                    val dueAtMillis = ZonedDateTime.of(selectedDate, timeForDue, ZoneId.systemDefault())
                        .toInstant().toEpochMilli()

                    if (title.isNotBlank()) {
                        vm.addTask(
                            title = title.trim(),
                            dueAt = dueAtMillis,
                            priority = priorityIndex // 0..3
                        )
                        title = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ajouter") }

            Divider()

            // ---- Liste des tâches ----
            Text("Mes tâches", style = MaterialTheme.typography.titleMedium)

            if (tasks.isEmpty()) {
                Text("Aucune tâche pour l’instant.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { t ->
                        TaskRow(
                            title = t.title,
                            dueAt = t.dueAt,
                            priorityIdx = t.priority,
                            onRemove = { vm.removeTask(t.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    title: String,
    dueAt: Long,
    priorityIdx: Int,
    onRemove: () -> Unit
) {
    val dateTime = remember(dueAt) {
        val zdt = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
        val d = zdt.toLocalDate().toString()
        val t = zdt.toLocalTime().withSecond(0).withNano(0).toString()
        "$d $t"
    }
    val prioLabel = remember(priorityIdx) {
        listOf("P1", "P2", "P3", "P4").getOrElse(priorityIdx) { "P${priorityIdx + 1}" }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("Échéance : $dateTime", style = MaterialTheme.typography.bodySmall)
                Text("Priorité : $prioLabel", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRemove) { Text("Supprimer") }
        }
    }
}
