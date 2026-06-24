package com.iammaster.codecmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.iammaster.codecmonitor.data.bluetooth.BluetoothMonitor
import com.iammaster.codecmonitor.ui.screens.DashboardScreen
import com.iammaster.codecmonitor.ui.theme.CodecMonitorTheme
import com.iammaster.codecmonitor.ui.viewmodels.DashboardViewModel

class MainActivity : ComponentActivity() {
    
    private lateinit var bluetoothMonitor: BluetoothMonitor
    private lateinit var viewModel: DashboardViewModel
    
    private var hasBluetoothPermission by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasBluetoothPermission = permissions.entries.all { it.value }
            if (hasBluetoothPermission) {
                viewModel.startMonitoring()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        bluetoothMonitor = BluetoothMonitor(applicationContext)
        viewModel = DashboardViewModel(bluetoothMonitor)
        
        checkAndRequestPermissions()

        setContent {
            CodecMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val codecStatus by viewModel.codecStatus.collectAsState()
                    val connectedDevice by bluetoothMonitor.connectedDevice.collectAsState()
                    
                    DashboardScreen(
                        codecStatus = codecStatus, 
                        hasPermission = hasBluetoothPermission,
                        deviceConnected = connectedDevice != null
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            hasBluetoothPermission = true
            viewModel.startMonitoring()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopMonitoring()
    }
}
