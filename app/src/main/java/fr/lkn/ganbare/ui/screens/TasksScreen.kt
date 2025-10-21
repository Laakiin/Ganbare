package fr.lkn.ganbare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.lkn.ganbare.ui.vm.TasksViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TasksScreen(vm: TasksViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(tasks) { t ->
                TaskRow(
                    title = t.title,
                    due = t.dueAt,
                    priority = t.priority,
                    notes = t.notes
                )
                Divider()
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+") }
    }

    if (showDialog) {
        AddTaskDialog(
            onDismiss = { showDialog = false },
            onCreate = { title, dateTimeStr, priority, notes ->
                val instant = parseDateTimeToInstant(dateTimeStr)
                if (instant != null) {
                    vm.addTask(title, instant, priority, notes)
                    showDialog = false
                }
            }
        )
    }
}

@Composable
private fun TaskRow(title: String, due: Instant, priority: Int, notes: String?) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
    val date = fmt.format(due.atZone(ZoneId.systemDefault()))
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$title  (P$priority)", style = MaterialTheme.typography.titleMedium)
        Text(date, style = MaterialTheme.typography.bodyMedium)
        if (!notes.isNullOrBlank()) {
            Text(notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, dateTime: String, priority: Int, notes: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") } // "yyyy-MM-dd HH:mm"
    var priority by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onCreate(title.trim(), dateTime.trim(), priority.toIntOrNull() ?: 1, notes.trim().ifBlank { null })
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Nouvelle tâche") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") })
                OutlinedTextField(
                    value = dateTime, onValueChange = { dateTime = it },
                    label = { Text("Échéance (yyyy-MM-dd HH:mm)") },
                    placeholder = { Text("2025-10-21 18:00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                OutlinedTextField(
                    value = priority, onValueChange = { priority = it },
                    label = { Text("Priorité (entier)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optionnel)") }
                )
            }
        }
    )
}

private fun parseDateTimeToInstant(input: String): Instant? = try {
    val dt = LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    dt.atZone(ZoneId.systemDefault()).toInstant()
} catch (_: Exception) { null }
