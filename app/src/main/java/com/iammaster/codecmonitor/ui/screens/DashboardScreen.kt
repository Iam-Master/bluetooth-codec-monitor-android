package com.iammaster.codecmonitor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iammaster.codecmonitor.data.bluetooth.CodecStatus

@Composable
fun DashboardScreen(codecStatus: CodecStatus?) {
    var showEstimationExplanation by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        if (codecStatus != null) {
            Text(text = "Active Codec: ${codecStatus.codecName}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.clickable { 
                if (codecStatus.isEstimated) showEstimationExplanation = true 
            }) {
                Text(text = "Transmission Speed: ${codecStatus.estimatedBitrate} kbps ")
                if (codecStatus.isEstimated) {
                    Text(
                        text = "(Estimated ⓘ)", 
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Text("Detecting Bluetooth Device...")
        }
    }

    if (showEstimationExplanation) {
        AlertDialog(
            onDismissRequest = { showEstimationExplanation = false },
            title = { Text("How is this estimated?") },
            text = { 
                Text("Android does not directly report the real-time bitrate over the air. " +
                     "This value is estimated based on the active codec (e.g., LDAC, aptX), " +
                     "the negotiated sample rate, bit depth, and current connection quality metric (RSSI).") 
            },
            confirmButton = {
                TextButton(onClick = { showEstimationExplanation = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
