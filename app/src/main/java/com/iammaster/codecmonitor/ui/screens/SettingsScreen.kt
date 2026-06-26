package com.iammaster.codecmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iammaster.codecmonitor.data.preferences.AppSettings
import com.iammaster.codecmonitor.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onPollIntervalChanged: (Int) -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onMonitorInBackgroundChanged: (Boolean) -> Unit,
    onThemeChanged: (ThemeMode) -> Unit
) {
    var pollIntervalText by remember(settings.pollIntervalMs) { mutableStateOf(settings.pollIntervalMs.toString()) }
    var retentionText by remember(settings.historyRetentionDays) { mutableStateOf(settings.historyRetentionDays.toString()) }
    var showPollInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeChanged(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Poll interval", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = { showPollInfo = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "What is poll interval?",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        OutlinedTextField(
            value = pollIntervalText,
            onValueChange = { pollIntervalText = it; it.toIntOrNull()?.let(onPollIntervalChanged) },
            label = { Text("Milliseconds") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = retentionText,
            onValueChange = { retentionText = it; it.toIntOrNull()?.let(onRetentionChanged) },
            label = { Text("History retention (days)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Notifications", color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = settings.notificationsEnabled, onCheckedChange = onNotificationsChanged)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Monitor in background", color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = settings.monitorInBackground, onCheckedChange = onMonitorInBackgroundChanged)
        }
    }

    if (showPollInfo) {
        AlertDialog(
            onDismissRequest = { showPollInfo = false },
            title = { Text("Poll interval") },
            text = {
                Text(
                    "How often the app checks the Bluetooth codec status, in milliseconds. " +
                        "Lower values make codec changes and device switches show up faster, but check " +
                        "more often in the background. Default is 800ms."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPollInfo = false }) { Text("Got it") }
            }
        )
    }
}
