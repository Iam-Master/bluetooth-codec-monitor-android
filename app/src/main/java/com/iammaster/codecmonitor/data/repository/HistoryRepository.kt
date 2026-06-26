package com.iammaster.codecmonitor.data.repository

import com.iammaster.codecmonitor.data.local.DeviceRef
import com.iammaster.codecmonitor.data.local.HistoryDao
import com.iammaster.codecmonitor.data.local.HistoryEntity
import kotlinx.coroutines.flow.Flow

data class HistoryPoint(
    val timestampMs: Long,
    val device: String?,
    val mac: String?,
    val codec: String?,
    val bitrateKbps: Int?,
    val battery: Int?,
    val type: String?
)

enum class HistoryRange(val label: String, val durationMs: Long) {
    TEN_MIN("10m", 10L * 60_000),
    THIRTY_MIN("30m", 30L * 60_000),
    ONE_HOUR("1h", 60L * 60_000),
    ONE_DAY("1d", 24L * 60 * 60_000),
    ONE_WEEK("1w", 7L * 24 * 60 * 60_000)
}

class HistoryRepository(private val dao: HistoryDao) {

    suspend fun record(point: HistoryPoint) {
        dao.insert(
            HistoryEntity(
                t = point.timestampMs,
                device = point.device,
                mac = point.mac,
                codec = point.codec,
                bitrateKbps = point.bitrateKbps,
                battery = point.battery,
                type = point.type
            )
        )
    }

    fun observe(range: HistoryRange, mac: String? = null): Flow<List<HistoryEntity>> {
        val since = System.currentTimeMillis() - range.durationMs
        return if (mac == null) dao.observeSince(since) else dao.observeSinceForDevice(since, mac)
    }

    suspend fun getSince(range: HistoryRange, mac: String? = null): List<HistoryEntity> {
        val since = System.currentTimeMillis() - range.durationMs
        return if (mac == null) dao.getSince(since) else dao.getSinceForDevice(since, mac)
    }

    suspend fun getDistinctDevices(): List<DeviceRef> = dao.getDistinctDevices()

    suspend fun prune(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
        dao.deleteOlderThan(cutoff)
    }
}
