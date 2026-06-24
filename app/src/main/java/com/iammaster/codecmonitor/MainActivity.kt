package com.iammaster.codecmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.iammaster.codecmonitor.data.bluetooth.BluetoothMonitor
import com.iammaster.codecmonitor.ui.screens.DashboardScreen
import com.iammaster.codecmonitor.ui.theme.CodecMonitorTheme
import com.iammaster.codecmonitor.ui.viewmodels.DashboardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // In a real app, use Hilt or Dagger for Dependency Injection
        val bluetoothMonitor = BluetoothMonitor(applicationContext)
        val viewModel = DashboardViewModel(bluetoothMonitor)
        
        setContent {
            CodecMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val codecStatus by viewModel.codecStatus.collectAsState()
                    DashboardScreen(codecStatus = codecStatus)
                }
            }
        }
    }
}
