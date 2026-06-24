package com.iammaster.codecmonitor.ui.screens

import android.bluetooth.BluetoothDevice
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iammaster.codecmonitor.data.bluetooth.CodecStatus
import com.iammaster.codecmonitor.ui.components.BitrateGraph
import com.iammaster.codecmonitor.ui.theme.ThemeMode

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(
    codecStatus: CodecStatus?, 
    hasPermission: Boolean, 
    deviceConnected: Boolean,
    connectedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    currentTheme: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit
) {
    var showEstimationExplanation by remember { mutableStateOf(false) }
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Theme Toggle & Device Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Codec Monitor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            var themeMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { themeMenuExpanded = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Theme Settings", tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(expanded = themeMenuExpanded, onDismissRequest = { themeMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("System Default") }, onClick = { onThemeChanged(ThemeMode.SYSTEM); themeMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Light Mode") }, onClick = { onThemeChanged(ThemeMode.LIGHT); themeMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Dark Mode") }, onClick = { onThemeChanged(ThemeMode.DARK); themeMenuExpanded = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (hasPermission && connectedDevices.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { deviceDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDevice?.name ?: "Unknown Device", color = MaterialTheme.colorScheme.onBackground)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(
                    expanded = deviceDropdownExpanded,
                    onDismissRequest = { deviceDropdownExpanded = false }
                ) {
                    connectedDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name ?: device.address) },
                            onClick = {
                                onDeviceSelected(device)
                                deviceDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasPermission) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Bluetooth permission is required to monitor codecs.",
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        if (!deviceConnected) {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Waiting for Bluetooth connection...",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        if (codecStatus != null) {
            CodecCard(codecStatus = codecStatus, onEstimationClick = { showEstimationExplanation = true })
            Spacer(modifier = Modifier.height(24.dp))
            
            // Graph Section
            Text(
                "Real-Time Transmission (kbps)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                BitrateGraph(currentBitrate = codecStatus.estimatedBitrate)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Extracting Codec Data...",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showEstimationExplanation) {
        AlertDialog(
            onDismissRequest = { showEstimationExplanation = false },
            title = { Text("How is this estimated?", color = MaterialTheme.colorScheme.onSurface) },
            text = { 
                Text(
                    "Android does not directly report the real-time bitrate over the air. " +
                    "This value is estimated based on the active codec, the negotiated sample rate, and bit depth.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                TextButton(onClick = { showEstimationExplanation = false }) {
                    Text("Got it")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun CodecCard(codecStatus: CodecStatus, onEstimationClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Active Codec",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = codecStatus.codecName,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Sample Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${codecStatus.sampleRate / 1000.0} kHz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("Bit Depth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${codecStatus.bitDepth}-bit", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { if (codecStatus.isEstimated) onEstimationClick() }
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Transmission Speed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${codecStatus.estimatedBitrate} kbps",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (codecStatus.isEstimated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "(Estimated ⓘ)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
