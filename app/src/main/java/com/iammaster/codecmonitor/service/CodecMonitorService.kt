package com.iammaster.codecmonitor.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.iammaster.codecmonitor.CodecMonitorApp
import com.iammaster.codecmonitor.data.bluetooth.MonitorEvent
import com.iammaster.codecmonitor.data.repository.HistoryPoint
import com.iammaster.codecmonitor.notifications.AlertNotifier
import com.iammaster.codecmonitor.notifications.MonitorServiceNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CodecMonitorService : Service() {

    companion object {
        const val ACTION_STOP = "com.iammaster.codecmonitor.action.STOP_MONITORING"
        private const val PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CodecMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CodecMonitorService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var lastEventSignature: Pair<String, Long> = "" to 0L
    private lateinit var stopIntent: PendingIntent
    // onStartCommand runs again on every startForegroundService() call (e.g. if MainActivity
    // re-requests the service while it's already running), but the observer coroutines below
    // must only ever be launched once — otherwise each restart stacks another set of
    // collectors, causing duplicate history rows, duplicate alert notifications, and duplicate
    // pruning passes.
    private var observersStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as CodecMonitorApp
        stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CodecMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = MonitorServiceNotifications.build(this, "Monitoring Bluetooth codecs…", stopIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MonitorServiceNotifications.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(MonitorServiceNotifications.SERVICE_NOTIFICATION_ID, notification)
        }

        if (!observersStarted) {
            observersStarted = true
            app.bluetoothMonitor.startMonitoring()
            observeCodecForTooltipAndHistory(app)
            observeEventsForAlerts(app)
            observePruning(app)
        }

        return START_STICKY
    }

    private fun observeCodecForTooltipAndHistory(app: CodecMonitorApp) {
        serviceScope.launch {
            app.bluetoothMonitor.codecStatus.collect { status ->
                val device = app.bluetoothMonitor.connectedDevice.value
                val text = if (status != null) {
                    "${status.codecName} — ${status.estimatedBitrate}kbps"
                } else {
                    "No device connected"
                }
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(
                    MonitorServiceNotifications.SERVICE_NOTIFICATION_ID,
                    MonitorServiceNotifications.build(this@CodecMonitorService, text, stopIntent)
                )

                if (status != null) {
                    app.historyRepository.record(
                        HistoryPoint(
                            timestampMs = System.currentTimeMillis(),
                            device = device?.name,
                            mac = device?.address,
                            codec = status.codecName,
                            bitrateKbps = status.estimatedBitrate,
                            battery = null,
                            type = "bluetooth"
                        )
                    )
                }
            }
        }
    }

    private fun observeEventsForAlerts(app: CodecMonitorApp) {
        serviceScope.launch {
            app.bluetoothMonitor.events.collect { event: MonitorEvent ->
                val signature = event.type.name + "|" + event.deviceName + "|" + event.codecName
                val now = System.currentTimeMillis()
                if (signature == lastEventSignature.first && now - lastEventSignature.second < 3000) {
                    return@collect
                }
                lastEventSignature = signature to now
                val enabled = app.settingsRepository.settings.first().notificationsEnabled
                if (enabled) {
                    AlertNotifier.notify(this@CodecMonitorService, event)
                }
            }
        }
    }

    private fun observePruning(app: CodecMonitorApp) {
        serviceScope.launch {
            while (true) {
                val retentionDays = app.settingsRepository.settings.first().historyRetentionDays
                app.historyRepository.prune(retentionDays)
                delay(PRUNE_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as CodecMonitorApp).bluetoothMonitor.stopMonitoring()
        serviceScope.cancel()
    }
}
