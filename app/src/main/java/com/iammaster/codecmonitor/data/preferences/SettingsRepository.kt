package com.iammaster.codecmonitor.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iammaster.codecmonitor.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "codec_monitor_settings")

data class AppSettings(
    val pollIntervalMs: Int = 800,
    val historyRetentionDays: Int = 14,
    val notificationsEnabled: Boolean = true,
    val monitorInBackground: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val POLL_INTERVAL_MS = intPreferencesKey("poll_interval_ms")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val MONITOR_IN_BACKGROUND = booleanPreferencesKey("monitor_in_background")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            pollIntervalMs = prefs[Keys.POLL_INTERVAL_MS] ?: 800,
            historyRetentionDays = prefs[Keys.HISTORY_RETENTION_DAYS] ?: 14,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            monitorInBackground = prefs[Keys.MONITOR_IN_BACKGROUND] ?: true,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        )
    }

    suspend fun setPollIntervalMs(value: Int) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL_MS] = value }
    }

    suspend fun setHistoryRetentionDays(value: Int) {
        context.dataStore.edit { it[Keys.HISTORY_RETENTION_DAYS] = value }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setMonitorInBackground(value: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_IN_BACKGROUND] = value }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = value.name }
    }
}
