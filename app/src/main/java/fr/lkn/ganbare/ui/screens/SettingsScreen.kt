package fr.lkn.ganbare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.lkn.ganbare.ui.vm.SettingsViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()

    var icalUrl by remember(s) { mutableStateOf(s.icalUrl) }
    var dailyEnabled by remember(s) { mutableStateOf(s.dailyEnabled) }
    var hour by remember(s) { mutableStateOf(s.dailyTime.hour.toString()) }
    var minute by remember(s) { mutableStateOf(s.dailyTime.minute.toString()) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = icalUrl, onValueChange = { icalUrl = it },
            label = { Text("URL iCal") }, modifier = Modifier.fillMaxWidth()
        )


        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = hour, onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Heure (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minute, onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Minute (0-59)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = dailyEnabled, onCheckedChange = { dailyEnabled = it })
            Spacer(Modifier.width(8.dp))
            Text("Récap quotidien activé")
        }

    }
}
