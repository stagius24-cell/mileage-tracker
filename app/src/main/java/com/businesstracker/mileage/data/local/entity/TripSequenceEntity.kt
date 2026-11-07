package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable per-trip monotonic sequence tracking entity in SQLite.
 * Survives queue recreation, tracking service restart, app process termination, and device reboots.
 */
@Entity(tableName = "trip_sequence")
data class TripSequenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "tripId")
    val tripId: String,

    @ColumnInfo(name = "lastSequenceNumber")
    val lastSequenceNumber: Long
)
