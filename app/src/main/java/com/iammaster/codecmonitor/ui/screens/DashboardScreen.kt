package com.iammaster.codecmonitor.ui.screens

import android.bluetooth.BluetoothDevice
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iammaster.codecmonitor.data.bluetooth.BatteryInfo
import com.iammaster.codecmonitor.data.bluetooth.CodecStatus
import com.iammaster.codecmonitor.data.stability.StabilityLabel
import com.iammaster.codecmonitor.data.stability.StabilityStatus
import com.iammaster.codecmonitor.ui.components.BitrateGraph
import com.iammaster.codecmonitor.ui.theme.StatusAmber
import com.iammaster.codecmonitor.ui.theme.StatusGreen
import com.iammaster.codecmonitor.ui.theme.StatusRed
import java.io.File

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(
    codecStatus: CodecStatus?,
    hasPermission: Boolean,
    deviceConnected: Boolean,
    connectedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    stabilityStatus: StabilityStatus,
    devicePhotoFile: File?,
    batteryInfo: BatteryInfo?,
    onSettingsClick: () -> Unit
) {
    var showEstimationExplanation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                DeviceSwitchButton(connectedDevices = connectedDevices, selectedDevice = selectedDevice, onDeviceSelected = onDeviceSelected)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!hasPermission) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Bluetooth permission is required to monitor codecs.",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        if (!deviceConnected) {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Waiting for Bluetooth connection...",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        if (codecStatus != null) {
            HeroDeviceCard(
                deviceName = selectedDevice?.name ?: "Unknown Device",
                devicePhotoFile = devicePhotoFile,
                stabilityStatus = stabilityStatus,
                batteryInfo = batteryInfo
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Active Codec",
                        value = codecStatus.codecName,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    StatCard(
                        label = "Bit Depth",
                        value = "${codecStatus.bitDepth}-bit",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    StatCard(
                        label = "Sample Rate",
                        value = "${codecStatus.sampleRate / 1000.0} kHz",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Transmission Speed",
                        value = "${codecStatus.estimatedBitrate} kbps",
                        caption = if (codecStatus.isEstimated) "Estimated ⓘ" else null,
                        highlighted = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (codecStatus.isEstimated) showEstimationExplanation = true }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            "Bitrate, last 30s (kbps)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            BitrateGraph(currentBitrate = codecStatus.estimatedBitrate)
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Extracting Codec Data...",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
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

@SuppressLint("MissingPermission")
@Composable
fun DeviceSwitchButton(
    connectedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.SwapHoriz, contentDescription = "Switch device", tint = MaterialTheme.colorScheme.onBackground)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (connectedDevices.isEmpty()) {
                DropdownMenuItem(text = { Text("No devices connected") }, onClick = { expanded = false }, enabled = false)
            } else {
                connectedDevices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (device.name ?: device.address) + if (device == selectedDevice) " ✓" else ""
                            )
                        },
                        onClick = {
                            onDeviceSelected(device)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HeroDeviceCard(
    deviceName: String,
    devicePhotoFile: File?,
    stabilityStatus: StabilityStatus,
    batteryInfo: BatteryInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                if (devicePhotoFile != null) {
                    AsyncImage(
                        model = devicePhotoFile,
                        contentDescription = deviceName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.BluetoothAudio,
                        contentDescription = deviceName,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                deviceName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val battery = batteryLabel(batteryInfo)
            if (battery != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    battery,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            StabilityChip(stabilityStatus)
        }
    }
}

// Returns null when no battery data is available at all — Android has no public,
// non-privileged API to query Bluetooth headset battery on demand for most devices, so
// rather than show a permanent "Battery unavailable" placeholder, the row is hidden entirely.
private fun batteryLabel(batteryInfo: BatteryInfo?): String? {
    if (batteryInfo == null) return null
    val left = batteryInfo.left
    val right = batteryInfo.right
    val case = batteryInfo.case
    return when {
        left != null && right != null -> {
            val base = "L $left% · R $right%"
            if (case != null) "$base · Case $case%" else base
        }
        batteryInfo.main != null -> "Battery ${batteryInfo.main}%"
        else -> null
    }
}

@Composable
fun StabilityChip(status: StabilityStatus) {
    val (containerColor, contentColor) = when (status.label) {
        StabilityLabel.STABLE -> StatusGreen.copy(alpha = 0.18f) to StatusGreen
        StabilityLabel.OCCASIONAL_DROPS -> StatusAmber.copy(alpha = 0.18f) to StatusAmber
        StabilityLabel.UNSTABLE -> StatusRed.copy(alpha = 0.18f) to StatusRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            status.label.display,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.85f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
        if (caption != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.85f))
        }
    }
}
