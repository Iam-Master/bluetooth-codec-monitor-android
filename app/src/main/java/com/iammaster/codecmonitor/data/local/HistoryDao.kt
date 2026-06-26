package com.iammaster.codecmonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DeviceRef(val device: String?, val mac: String?)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("SELECT * FROM history WHERE t >= :sinceEpochMs ORDER BY t ASC")
    fun observeSince(sinceEpochMs: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE t >= :sinceEpochMs AND mac = :mac ORDER BY t ASC")
    fun observeSinceForDevice(sinceEpochMs: Long, mac: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE t >= :sinceEpochMs ORDER BY t ASC")
    suspend fun getSince(sinceEpochMs: Long): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE t >= :sinceEpochMs AND mac = :mac ORDER BY t ASC")
    suspend fun getSinceForDevice(sinceEpochMs: Long, mac: String): List<HistoryEntity>

    @Query("SELECT DISTINCT device, mac FROM history WHERE device IS NOT NULL")
    suspend fun getDistinctDevices(): List<DeviceRef>

    @Query("DELETE FROM history WHERE t < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
