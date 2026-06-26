package com.iammaster.codecmonitor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.iammaster.codecmonitor.data.bluetooth.MonitorEvent
import com.iammaster.codecmonitor.data.bluetooth.MonitorEventType

object AlertNotifier {
    const val ALERT_CHANNEL_ID = "codec_alerts"
    private const val ALERT_NOTIFICATION_ID_BASE = 1000
    // Explicit group, separate from the foreground-service notification's group. Without
    // this, the OS auto-bundles every notification from this app into one stack — since the
    // ongoing service notification in that stack can never be swipe-dismissed, the bundling
    // can prevent these dismissible alerts from swiping away cleanly too.
    private const val ALERT_GROUP_KEY = "com.iammaster.codecmonitor.ALERTS"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Codec alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Codec upgrades, downgrades, connects and disconnects" }
        )
    }

    private fun messageFor(event: MonitorEvent): String {
        val device = event.deviceName ?: "device"
        return when (event.type) {
            MonitorEventType.CONNECT -> "Connected to $device"
            MonitorEventType.DISCONNECT -> "Disconnected from $device"
            MonitorEventType.SWITCH -> "Switched to $device"
            MonitorEventType.UPGRADE -> "Codec upgraded to ${event.codecName} on $device"
            MonitorEventType.DOWNGRADE -> "Codec downgraded to ${event.codecName} on $device"
            MonitorEventType.CODEC_CHANGE -> "Codec changed to ${event.codecName} on $device"
        }
    }

    fun notify(context: Context, event: MonitorEvent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Codec Monitor")
            .setContentText(messageFor(event))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(ALERT_GROUP_KEY)
            .build()
        manager.notify(ALERT_NOTIFICATION_ID_BASE + event.type.ordinal, notification)
    }
}
