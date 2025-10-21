package fr.lkn.ganbare.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.lkn.ganbare.ui.vm.SettingsViewModel

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Récap quotidien") })
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

            // Heure HH:mm
            var timeText by remember(state.hour, state.minute) {
                mutableStateOf("%02d:%02d".format(state.hour, state.minute))
            }
            OutlinedTextField(
                value = timeText,
                onValueChange = {
                    timeText = it
                    vm.onTimeTextChange(it)
                },
                label = { Text("Heure du récap (HH:mm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.timeError != null,
                supportingText = {
                    state.timeError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Bouton Sauvegarder
            Button(
                onClick = {
                    vm.saveAll()
                    if (state.timeError == null) {
                        Toast.makeText(context, "Préférences sauvegardées", Toast.LENGTH_SHORT).show()
                    }
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

            if (state.savedOnce && state.timeError == null) {
                Text(
                    "✅ Sauvegardé (URL, activation, heure).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Astuce : on planifiera ensuite une notification quotidienne à l’heure choisie.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AssistiveInfo(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
