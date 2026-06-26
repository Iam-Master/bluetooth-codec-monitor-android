package com.iammaster.codecmonitor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object MonitorServiceNotifications {
    const val SERVICE_CHANNEL_ID = "codec_monitor_service"
    const val SERVICE_NOTIFICATION_ID = 42
    // Its own group, distinct from AlertNotifier's group, so this never-dismissible ongoing
    // notification doesn't get OS-auto-bundled together with the swipeable alert notifications
    // (which would otherwise block clean swipe-to-dismiss on the alerts).
    private const val SERVICE_GROUP_KEY = "com.iammaster.codecmonitor.SERVICE_STATUS"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Background monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps showing the active codec while Codec Monitor runs in the background" }
        )
    }

    fun build(context: Context, text: String, stopIntent: android.app.PendingIntent?): android.app.Notification {
        val builder = NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Codec Monitor")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(SERVICE_GROUP_KEY)
        if (stopIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop monitoring", stopIntent)
        }
        return builder.build()
    }
}
