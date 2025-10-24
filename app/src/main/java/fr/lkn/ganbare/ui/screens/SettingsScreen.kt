@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package fr.lkn.ganbare.ui.screens

import android.app.Application
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.core.reminders.Recurrence
import fr.lkn.ganbare.ui.vm.RecurrenceUiState
import fr.lkn.ganbare.ui.vm.SettingsViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun provideSettingsViewModel(): SettingsViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(
        modelClass = SettingsViewModel::class.java,
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = provideSettingsViewModel()
) {
    val state by vm.ui.collectAsState(initial = RecurrenceUiState.default())
    var selectedTab by remember { mutableStateOf(0) } // 0 = Tâches, 1 = Planning

    Scaffold(
        topBar = { TopAppBar(title = { Text("Réglages") }) }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tâches") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Planning") })
            }

            when (selectedTab) {
                0 -> TasksSettingsTab(state = state, vm = vm)
                1 -> PlanningSettingsTab(state = state, vm = vm)
                else -> Unit
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { vm.reload() }) { Text("Annuler") }
                Button(onClick = { vm.save() }) {
                    Text(if (state.isSaving) "Enregistrement..." else "Enregistrer")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TasksSettingsTab(state: RecurrenceUiState, vm: SettingsViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("Rappels des tâches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Récurrences par priorité", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                RecurrencePickerRow("P1", state.p1) { vm.setP1(it) }
                RecurrencePickerRow("P2", state.p2) { vm.setP2(it) }
                RecurrencePickerRow("P3", state.p3) { vm.setP3(it) }
                RecurrencePickerRow("P4", state.p4) { vm.setP4(it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Types de notifications", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LabeledSwitch("Rappel la veille", state.enableDayBefore) { vm.toggleDayBefore(it) }
                LabeledSwitch("Rappel 2h avant", state.enableTwoHoursBefore) { vm.toggleTwoHours(it) }
                LabeledSwitch("Rappel jour J", state.enableOnDay) { vm.toggleOnDay(it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Tester les notifications (tâches)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testTaskDayBefore() }) { Text("Test veille") }
                    OutlinedButton(onClick = { vm.testTaskTwoHours() }) { Text("Test 2h avant") }
                    OutlinedButton(onClick = { vm.testTaskOnDay() }) { Text("Test jour J") }
                }
            }
        }
    }
}

@Composable
private fun PlanningSettingsTab(state: RecurrenceUiState, vm: SettingsViewModel) {
    val scroll = rememberScrollState()
    val ctx = LocalContext.current.applicationContext

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("Planning / Agenda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Calendrier iCal", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.icalUrl,
                    onValueChange = { vm.setIcalUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Lien iCal (URL)") },
                    placeholder = { Text("https://... .ics") }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { vm.applyIcal() }) {
                        Text("Enregistrer & appliquer")
                    }
                }

                // Statut du fichier ICS local
                Spacer(Modifier.height(8.dp))
                val (hasLocal, sizeBytes) = SettingsViewModel.hasLocalIcs(ctx)
                Text(
                    text = if (hasLocal)
                        "Fichier ICS local présent (${sizeBytes} o)."
                    else
                        "Aucun fichier ICS local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasLocal) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Heures", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                TimeRow("Heure du récap quotidien", state.dailySummaryTime) { vm.setDailySummaryTime(it) }
                TimeRow("Heure de bascule jour (afficher J+1)", state.agendaRolloverTime) { vm.setAgendaRolloverTime(it) }
                TimeRow("Heure d’envoi \"premier cours demain\"", state.firstEventInfoTime) { vm.setFirstEventInfoTime(it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Types de notifications", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LabeledSwitch("Activer récap quotidien", state.enableDailySummary) { vm.toggleEnableDailySummary(it) }
                LabeledSwitch("Activer notif premier cours", state.enableFirstEventInfo) { vm.toggleEnableFirstEventInfo(it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Tester les notifications (planning)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testDailySummary() }) { Text("Test récap") }
                    OutlinedButton(onClick = { vm.testFirstEventInfo() }) { Text("Test 1er cours") }
                }
            }
        }
    }
}

/* ---------- UI helpers ---------- */

@Composable
private fun RecurrencePickerRow(label: String, value: Recurrence, onChange: (Recurrence) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = Recurrence.values()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label :", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { expanded = true }) { Text(value.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            all.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.label()) },
                    onClick = {
                        onChange(r)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun Recurrence.label(): String = when (this) {
    Recurrence.NONE   -> "Aucune"
    Recurrence.DAILY  -> "Tous les jours"
    Recurrence.WEEKLY -> "Toutes les semaines"
    Recurrence.MONTHLY-> "Tous les mois"
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeRow(label: String, value: LocalTime, onPick: (LocalTime) -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            showTimePickerDialog(ctx, initial = value, onPicked = onPick)
        }) {
            Text(value.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())))
        }
    }
}

// Non-composable helper (appelé depuis onClick)
private fun showTimePickerDialog(
    context: Context,
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hourOfDay, minute -> onPicked(LocalTime.of(hourOfDay, minute)) },
        initial.hour,
        initial.minute,
        true
    ).show()
}
