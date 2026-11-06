package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "active_waypoints",
    foreignKeys = [
        ForeignKey(
            entity = ActiveTripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["timestamp"]),
        Index(value = ["tripId", "sourceFixId"]),
        Index(value = ["tripId", "sequenceNumber"])
    ]
)
data class ActiveWaypointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "tripId")
    val tripId: String,

    @ColumnInfo(name = "sourceFixId")
    val sourceFixId: Long? = null,

    @ColumnInfo(name = "sequenceNumber", defaultValue = "0")
    val sequenceNumber: Long = 0L,

    @ColumnInfo(name = "elapsedRealtimeNanos", defaultValue = "0")
    val elapsedRealtimeNanos: Long = 0L,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float,

    @ColumnInfo(name = "speed")
    val speed: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
