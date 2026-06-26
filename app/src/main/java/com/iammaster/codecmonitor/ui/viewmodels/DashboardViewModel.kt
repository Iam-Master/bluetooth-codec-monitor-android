package com.iammaster.codecmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iammaster.codecmonitor.data.bluetooth.BluetoothMonitor
import com.iammaster.codecmonitor.data.bluetooth.CodecStatus
import com.iammaster.codecmonitor.data.stability.StabilityStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(private val bluetoothMonitor: BluetoothMonitor) : ViewModel() {

    val codecStatus: StateFlow<CodecStatus?> = bluetoothMonitor.codecStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val connectedDevicesList: StateFlow<List<android.bluetooth.BluetoothDevice>> = bluetoothMonitor.connectedDevicesList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val connectedDevice: StateFlow<android.bluetooth.BluetoothDevice?> = bluetoothMonitor.connectedDevice
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val stabilityStatus: StateFlow<StabilityStatus> = bluetoothMonitor.stabilityStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = bluetoothMonitor.stabilityStatus.value
        )

    fun selectDevice(device: android.bluetooth.BluetoothDevice) {
        bluetoothMonitor.selectDevice(device)
    }

    fun startMonitoring() {
        bluetoothMonitor.startMonitoring()
    }

    fun stopMonitoring() {
        bluetoothMonitor.stopMonitoring()
    }
}
