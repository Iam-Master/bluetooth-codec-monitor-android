package com.iammaster.codecmonitor.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iammaster.codecmonitor.data.bluetooth.DeviceCategory
import com.iammaster.codecmonitor.data.bluetooth.category
import com.iammaster.codecmonitor.ui.theme.StatusGreen
import java.io.File

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    bondedDevices: List<BluetoothDevice>,
    connectedDevices: List<BluetoothDevice>,
    activeDevice: BluetoothDevice?,
    photoFor: (String) -> File?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Devices",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (bondedDevices.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "No saved Bluetooth devices found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bondedDevices) { device ->
                val isConnected = connectedDevices.any { it.address == device.address }
                val isActive = activeDevice?.address == device.address
                val category = device.category()
                DeviceGridCell(
                    name = device.name ?: device.address,
                    photoFile = if (category == DeviceCategory.HEADPHONES) device.name?.let(photoFor) else null,
                    category = category,
                    isConnected = isConnected,
                    isActive = isActive
                )
            }
        }
    }
}

@Composable
private fun DeviceGridCell(
    name: String,
    photoFile: File?,
    category: DeviceCategory,
    isConnected: Boolean,
    isActive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        when (category) {
                            DeviceCategory.CAR -> Icons.Default.DirectionsCar
                            DeviceCategory.PHONE -> Icons.Default.Phone
                            DeviceCategory.COMPUTER -> Icons.Default.Computer
                            else -> Icons.Default.BluetoothAudio
                        },
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                when {
                    isActive -> "Active"
                    isConnected -> "Connected"
                    else -> "Not connected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
