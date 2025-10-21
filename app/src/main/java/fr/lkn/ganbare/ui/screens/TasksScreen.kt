package fr.lkn.ganbare.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import fr.lkn.ganbare.core.db.AppDatabase
import fr.lkn.ganbare.feature.tasks.data.TaskEntity
import fr.lkn.ganbare.feature.tasks.data.TaskRepository
import fr.lkn.ganbare.ui.vm.TasksViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: TasksViewModel = run {
        val ctx = LocalContext.current.applicationContext
        val repo = remember {
            TaskRepository(AppDatabase.getDatabase(ctx).taskDao())
        }
        viewModel(factory = TasksViewModel.factory(repo))
    }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val new by vm.new.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tâches") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ---- Formulaire d'ajout ----
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = new.title,
                    onValueChange = vm::onTitleChange,
                    label = { Text("Titre de la tâche") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Priorité :", style = MaterialTheme.typography.bodyMedium)
                    PriorityChips(
                        selected = new.priority,
                        onSelect = vm::onPriorityChange
                    )
                }

                // Sélecteurs natifs Date & Heure
                DateTimePickersRow(
                    date = new.date,
                    onPickDate = { vm.onDatePicked(it) },
                    hasTime = new.hasDueTime,
                    time = new.time,
                    onPickTime = { vm.onTimePicked(it) },
                    onClearTime = vm::clearTime
                )

                Button(
                    onClick = vm::addTask,
                    enabled = new.title.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Ajouter")
                }
            }

            Divider()

            // ---- Liste ----
            TasksList(
                tasks = state.tasks,
                onDelete = vm::delete
            )
        }
    }
}

@Composable
private fun PriorityChips(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { p ->
            val isSel = p == selected
            Button(
                onClick = { onSelect(p) },
                enabled = !isSel
            ) { Text("P$p") }
        }
    }
}

@Composable
private fun DateTimePickersRow(
    date: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    hasTime: Boolean,
    time: LocalTime,
    onPickTime: (LocalTime) -> Unit,
    onClearTime: () -> Unit
) {
    val ctx = LocalContext.current
    val locale = Locale.getDefault()
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date
        Button(onClick = {
            DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    onPickDate(LocalDate.of(y, m + 1, d))
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        }) {
            Text(date.format(dateFmt))
        }

        // Heure
        if (hasTime) {
            Button(onClick = {
                TimePickerDialog(
                    ctx,
                    { _, h, min -> onPickTime(LocalTime.of(h, min)) },
                    time.hour,
                    time.minute,
                    true
                ).show()
            }) { Text(time.format(timeFmt)) }

            Button(onClick = onClearTime) { Text("Sans heure") }
        } else {
            Button(onClick = {
                TimePickerDialog(
                    ctx,
                    { _, h, min -> onPickTime(LocalTime.of(h, min)) },
                    18,
                    0,
                    true
                ).show()
            }) { Text("Ajouter une heure") }
        }
    }
}

@Composable
private fun TasksList(
    tasks: List<TaskEntity>,
    onDelete: (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val timeFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM • HH:mm", Locale.getDefault()) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (tasks.isEmpty()) {
            item {
                Text(
                    "Aucune tâche",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(tasks, key = { it.id }) { t ->
                val z = t.dueAt.atZone(zone)
                val subtitle =
                    if (z.toLocalTime().hour == 0 && z.toLocalTime().minute == 0) {
                        z.toLocalDate().format(dateFmt)
                    } else {
                        z.format(timeFmt)
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "P${t.priority} • $subtitle",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = { onDelete(t.id) }) { Text("Suppr") }
                }
                Divider()
            }
        }
    }
}
