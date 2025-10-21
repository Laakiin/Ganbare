package fr.lkn.ganbare.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.core.work.Scheduler
import fr.lkn.ganbare.ui.vm.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = run {
        val ctx = LocalContext.current
        viewModel(factory = SettingsViewModel.factory(ctx))
    }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Récap & Réglages") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.icalUrl,
                onValueChange = vm::onIcalUrlChange,
                label = { Text("Lien iCal (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AssistiveInfo("Colle ici l’URL iCal de ton emploi du temps.")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notification récap du lendemain", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Envoie une notif avec les cours de demain à l’heure choisie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = state.recapEnabled, onCheckedChange = vm::onRecapEnabledChange)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Heure du récap", style = MaterialTheme.typography.titleMedium)
                    Text(formatTime(state.recapHour, state.recapMinute), style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = {
                    showTimePickerDialog(
                        context = context,
                        initialHour = state.recapHour,
                        initialMinute = state.recapMinute
                    ) { h, m -> vm.onHourMinuteChange(h, m) }
                }) { Text("Modifier") }
            }

            Spacer(Modifier.height(8.dp))
            Text("Planning", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Heure de bascule jour suivant", style = MaterialTheme.typography.titleMedium)
                    Text(formatTime(state.switchHour, state.switchMinute), style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = {
                    showTimePickerDialog(
                        context = context,
                        initialHour = state.switchHour,
                        initialMinute = state.switchMinute
                    ) { h, m -> vm.onSwitchHourMinuteChange(h, m) }
                }) { Text("Modifier") }
            }
            AssistiveInfo("Avant cette heure, le Planning affiche AUJOURD’HUI ; après, il affiche DEMAIN.")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        vm.saveAll()
                        Toast.makeText(context, "Préférences sauvegardées", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !state.isSaving
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Sauvegarder")
                }

                // 🔧 Bouton de test : lance le worker dans 10 secondes
                OutlinedButton(onClick = {
                    Scheduler.debugRunIn(context, 10)
                    Toast.makeText(context, "Test programmé dans 10s", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Tester (10s)")
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Astuce : la bascule n’affecte que la date par défaut du Planning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun showTimePickerDialog(
    context: android.content.Context,
    initialHour: Int,
    initialMinute: Int,
    onPicked: (Int, Int) -> Unit
) {
    android.app.TimePickerDialog(
        context,
        { _, h, m -> onPicked(h, m) },
        initialHour.coerceIn(0, 23),
        initialMinute.coerceIn(0, 59),
        true
    ).show()
}

@Composable
private fun AssistiveInfo(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTime(h: Int, m: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", h, m)
