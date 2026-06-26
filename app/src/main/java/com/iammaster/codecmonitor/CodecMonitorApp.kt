package com.iammaster.codecmonitor

import android.app.Application
import com.iammaster.codecmonitor.data.bluetooth.BluetoothMonitor
import com.iammaster.codecmonitor.data.local.AppDatabase
import com.iammaster.codecmonitor.data.preferences.SettingsRepository
import com.iammaster.codecmonitor.data.repository.HistoryRepository
import com.iammaster.codecmonitor.notifications.AlertNotifier
import com.iammaster.codecmonitor.notifications.MonitorServiceNotifications

class CodecMonitorApp : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(AppDatabase.getInstance(this).historyDao())
    }
    val bluetoothMonitor: BluetoothMonitor by lazy { BluetoothMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.createChannels(this)
        MonitorServiceNotifications.createChannel(this)
    }
}
