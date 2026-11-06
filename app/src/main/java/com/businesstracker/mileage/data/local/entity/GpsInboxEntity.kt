package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable Room-backed inbox entity for raw incoming GPS location fixes.
 * Guarantees crash-safety, strict chronological ordering via monotonic time and sequence,
 * and zero data loss on service termination or app backgrounding.
 */
@Entity(
    tableName = "gps_inbox",
    indices = [
        Index(value = ["tripId", "sequenceNumber"]),
        Index(value = ["tripId", "elapsedRealtimeNanos", "timestamp", "id"])
    ]
)
data class GpsInboxEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "tripId")
    val tripId: String,

    @ColumnInfo(name = "sequenceNumber")
    val sequenceNumber: Long = 0L,

    @ColumnInfo(name = "elapsedRealtimeNanos")
    val elapsedRealtimeNanos: Long = 0L,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float = 0f,

    @ColumnInfo(name = "speed")
    val speed: Float = 0f,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "bootCount", defaultValue = "0")
    val bootCount: Int = 0
)
