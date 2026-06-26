package com.iammaster.codecmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iammaster.codecmonitor.data.export.ExportFormat
import com.iammaster.codecmonitor.data.local.DeviceRef
import com.iammaster.codecmonitor.data.local.HistoryEntity
import com.iammaster.codecmonitor.data.repository.HistoryRange
import com.iammaster.codecmonitor.ui.components.HistoryGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    points: List<HistoryEntity>,
    range: HistoryRange,
    onRangeSelected: (HistoryRange) -> Unit,
    onExportRequested: (ExportFormat) -> Unit,
    deviceOptions: List<DeviceRef>,
    selectedMac: String?,
    onDeviceFilterSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                var deviceMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { deviceMenuExpanded = true }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Filter by device")
                    }
                    DropdownMenu(expanded = deviceMenuExpanded, onDismissRequest = { deviceMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All devices" + if (selectedMac == null) " ✓" else "") },
                            onClick = { onDeviceFilterSelected(null); deviceMenuExpanded = false }
                        )
                        deviceOptions.forEach { ref ->
                            DropdownMenuItem(
                                text = { Text((ref.device ?: ref.mac ?: "Unknown") + if (ref.mac == selectedMac) " ✓" else "") },
                                onClick = { onDeviceFilterSelected(ref.mac); deviceMenuExpanded = false }
                            )
                        }
                    }
                }
                var exportMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { exportMenuExpanded = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = { onExportRequested(ExportFormat.CSV); exportMenuExpanded = false })
                        DropdownMenuItem(text = { Text("Export Markdown") }, onClick = { onExportRequested(ExportFormat.MARKDOWN); exportMenuExpanded = false })
                        DropdownMenuItem(text = { Text("Export PDF") }, onClick = { onExportRequested(ExportFormat.PDF); exportMenuExpanded = false })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        val trackingLabel = if (selectedMac == null) {
            val names = deviceOptions.mapNotNull { it.device }
            if (names.isEmpty()) "Not tracking any device yet" else "Tracking: ${names.joinToString(", ")}"
        } else {
            "Tracking: ${deviceOptions.firstOrNull { it.mac == selectedMac }?.device ?: "selected device"}"
        }
        Text(
            trackingLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryRange.values().forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { onRangeSelected(r) },
                    label = { Text(r.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (points.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "No history yet for this range.",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            val bitrates = points.mapNotNull { it.bitrateKbps }
            if (bitrates.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatChip("Min", "${bitrates.min()} kbps")
                    StatChip("Avg", "${bitrates.average().toInt()} kbps")
                    StatChip("Max", "${bitrates.max()} kbps")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                HistoryGraph(points = points)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
