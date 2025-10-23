package fr.lkn.ganbare.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.ui.vm.RecurrenceUiState
import fr.lkn.ganbare.ui.vm.SettingsViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Réglages") }) }
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(paddings)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Tâches") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Planning") }
                )
            }

            when (tabIndex) {
                0 -> TasksSettingsTab(
                    ui = ui,
                    onP1 = vm::setP1,
                    onP2 = vm::setP2,
                    onP3 = vm::setP3,
                    onP4 = vm::setP4,
                    onDayBefore = vm::toggleDayBefore,
                    onTwoHours = vm::toggleTwoHours,
                    onOnDay = vm::toggleOnDay,
                    onTestDayBefore = vm::testTaskDayBefore,
                    onTestTwoHours = vm::testTaskTwoHours,
                    onTestOnDay = vm::testTaskOnDay
                )
                1 -> PlanningSettingsTab(
                    summaryTime = ui.dailySummaryTime,
                    firstEventInfoTime = ui.firstEventInfoTime,
                    rolloverTime = ui.agendaRolloverTime,
                    enableDailySummary = ui.enableDailySummary,
                    enableFirstEventInfo = ui.enableFirstEventInfo,
                    onSummaryTime = vm::setDailySummaryTime,
                    onFirstEventTime = vm::setFirstEventInfoTime,
                    onRolloverTime = vm::setAgendaRolloverTime,
                    onEnableSummary = vm::toggleEnableDailySummary,
                    onEnableFirstEvent = vm::toggleEnableFirstEventInfo,
                    onTestSummary = vm::testDailySummary,
                    onTestFirstEvent = vm::testFirstEventInfo
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = vm::save,
                    enabled = !ui.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (ui.isSaving) "Enregistrement..." else "Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun TasksSettingsTab(
    ui: RecurrenceUiState,
    onP1: (Recurrence) -> Unit,
    onP2: (Recurrence) -> Unit,
    onP3: (Recurrence) -> Unit,
    onP4: (Recurrence) -> Unit,
    onDayBefore: (Boolean) -> Unit,
    onTwoHours: (Boolean) -> Unit,
    onOnDay: (Boolean) -> Unit,
    onTestDayBefore: () -> Unit,
    onTestTwoHours: () -> Unit,
    onTestOnDay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Notifications de tâches", style = MaterialTheme.typography.titleMedium)

        RecurrenceRow(label = "P1 (priorité 1)", value = ui.p1, onChange = onP1)
        RecurrenceRow(label = "P2 (priorité 2)", value = ui.p2, onChange = onP2)
        RecurrenceRow(label = "P3 (priorité 3)", value = ui.p3, onChange = onP3)
        RecurrenceRow(label = "P4 (priorité 4)", value = ui.p4, onChange = onP4)

        Divider()

        Text("Rappels systématiques", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Rappel la veille (à l’heure du récap)", ui.enableDayBefore, onDayBefore)
        SwitchRow("Rappel 2 heures avant", ui.enableTwoHoursBefore, onTwoHours)
        SwitchRow("Rappel le jour J (à l’heure du récap)", ui.enableOnDay, onOnDay)

        Text(
            "Astuce : les rappels « veille » et « jour J » utilisent l’heure du récap définie dans l’onglet Planning.",
            style = MaterialTheme.typography.bodySmall
        )

        Divider()

        Text("Tester les notifications de tâches", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTestDayBefore) { Text("Tester veille") }
            OutlinedButton(onClick = onTestTwoHours) { Text("Tester 2 h avant") }
            OutlinedButton(onClick = onTestOnDay) { Text("Tester jour J") }
        }
    }
}

@Composable
private fun PlanningSettingsTab(
    summaryTime: LocalTime,
    firstEventInfoTime: LocalTime,
    rolloverTime: LocalTime,
    enableDailySummary: Boolean,
    enableFirstEventInfo: Boolean,
    onSummaryTime: (LocalTime) -> Unit,
    onFirstEventTime: (LocalTime) -> Unit,
    onRolloverTime: (LocalTime) -> Unit,
    onEnableSummary: (Boolean) -> Unit,
    onEnableFirstEvent: (Boolean) -> Unit,
    onTestSummary: () -> Unit,
    onTestFirstEvent: () -> Unit
) {
    val tf = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Paramètres du planning", style = MaterialTheme.typography.titleMedium)

        // Switchs d’activation/désactivation des notifs planning
        SwitchRow(
            label = "Activer la notif « Récap quotidien »",
            checked = enableDailySummary,
            onCheckedChange = onEnableSummary
        )
        SwitchRow(
            label = "Activer la notif « 1er évènement de demain »",
            checked = enableFirstEventInfo,
            onCheckedChange = onEnableFirstEvent
        )

        Divider()

        TimePickerRow(
            label = "Heure du récap quotidien",
            time = summaryTime,
            onPick = onSummaryTime,
            tf = tf
        )

        TimePickerRow(
            label = "Heure de la notif « 1er évènement de demain »",
            time = firstEventInfoTime,
            onPick = onFirstEventTime,
            tf = tf
        )

        TimePickerRow(
            label = "Heure de changement de jour (afficher l’agenda du lendemain)",
            time = rolloverTime,
            onPick = onRolloverTime,
            tf = tf
        )

        Divider()

        Text("Tester les notifications de planning", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTestSummary) { Text("Tester récap") }
            OutlinedButton(onClick = onTestFirstEvent) { Text("Tester 1er évènement") }
        }
    }
}

@Composable
private fun TimePickerRow(
    label: String,
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
    tf: java.time.format.DateTimeFormatter
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(time.format(tf), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = {
            TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, time.hour, time.minute, true).show()
        }) { Text("Changer") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceRow(
    label: String,
    value: Recurrence,
    onChange: (Recurrence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                label = { Text("Récurrence") },
                value = value.toDisplay(),
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Recurrence.values().forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.toDisplay()) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Recurrence.toDisplay(): String = when (this) {
    Recurrence.NONE -> "Aucune"
    Recurrence.DAILY -> "Quotidienne"
    Recurrence.WEEKLY -> "Hebdomadaire"
    Recurrence.MONTHLY -> "Mensuelle (30 j)"
}
