package com.iammaster.codecmonitor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index(value = ["t"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val t: Long, // epoch millis
    val device: String?,
    val mac: String?,
    val codec: String?,
    val bitrateKbps: Int?,
    val battery: Int?,
    val type: String?
)
