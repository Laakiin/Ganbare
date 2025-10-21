package fr.lkn.ganbare.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

    // booleans plus nécessaires avec le TimePicker natif (on affiche direct le dialog)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Récap & Réglages") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL iCal
            OutlinedTextField(
                value = state.icalUrl,
                onValueChange = vm::onIcalUrlChange,
                label = { Text("Lien iCal (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AssistiveInfo("Colle ici l’URL iCal de ton emploi du temps. Elle sera stockée en local.")

            // Activation du récap
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activer le récap quotidien", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enverra une notification la veille avec les cours du lendemain.",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = state.recapEnabled,
                    onCheckedChange = vm::onRecapEnabledChange
                )
            }

            // Heure du récap — bouton + TimePicker natif
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Heure du récap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatTime(state.hour, state.minute),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Button(onClick = {
                    showTimePickerDialog(
                        context = context,
                        initialHour = state.hour,
                        initialMinute = state.minute
                    ) { h, m -> vm.onHourMinuteChange(h, m) }
                }) {
                    Text("Modifier")
                }
            }

            // Séparateur visuel
            Spacer(Modifier.height(8.dp))
            Text("Planning", style = MaterialTheme.typography.titleMedium)

            // Heure de bascule vers J+1 — bouton + TimePicker natif
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bascule vers J+1 à partir de", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatTime(state.switchHour, state.switchMinute),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Button(onClick = {
                    showTimePickerDialog(
                        context = context,
                        initialHour = state.switchHour,
                        initialMinute = state.switchMinute
                    ) { h, m -> vm.onSwitchHourMinuteChange(h, m) }
                }) {
                    Text("Modifier")
                }
            }
            AssistiveInfo(
                "Avant cette heure, le Planning affiche AUJOURD’HUI ; " +
                        "à partir de cette heure, il affiche DEMAIN."
            )

            Spacer(Modifier.height(8.dp))

            // Bouton Sauvegarder
            Button(
                onClick = {
                    vm.saveAll()
                    Toast.makeText(context, "Préférences sauvegardées", Toast.LENGTH_SHORT).show()
                },
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Sauvegarder")
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Astuce : la bascule n’affecte que la date par défaut à l’ouverture du Planning.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun showTimePickerDialog(
    context: android.content.Context,
    initialHour: Int,
    initialMinute: Int,
    onTimePicked: (Int, Int) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hourOfDay, minute -> onTimePicked(hourOfDay, minute) },
        initialHour.coerceIn(0, 23),
        initialMinute.coerceIn(0, 59),
        true // 24h
    ).show()
}

@Composable
private fun AssistiveInfo(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatTime(h: Int, m: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", h, m)
