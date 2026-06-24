package com.iammaster.codecmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iammaster.codecmonitor.data.bluetooth.BluetoothMonitor
import com.iammaster.codecmonitor.data.bluetooth.CodecStatus
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

    fun startMonitoring() {
        bluetoothMonitor.startMonitoring()
    }

    fun stopMonitoring() {
        bluetoothMonitor.stopMonitoring()
    }
}
