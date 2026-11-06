package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_trip")
data class ActiveTripEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "startTime")
    val startTime: Long,

    @ColumnInfo(name = "startLatitude")
    val startLatitude: Double,

    @ColumnInfo(name = "startLongitude")
    val startLongitude: Double,

    @ColumnInfo(name = "lastLatitude")
    val lastLatitude: Double,

    @ColumnInfo(name = "lastLongitude")
    val lastLongitude: Double,

    @ColumnInfo(name = "lastTimestamp")
    val lastTimestamp: Long,

    @ColumnInfo(name = "totalDistanceMeters")
    val totalDistanceMeters: Double,

    @ColumnInfo(name = "trackingMethod")
    val trackingMethod: String,

    @ColumnInfo(name = "isPaused")
    val isPaused: Boolean = false,

    @ColumnInfo(name = "pausedDurationMillis")
    val pausedDurationMillis: Long = 0L,

    @ColumnInfo(name = "suggestedBusiness")
    val suggestedBusiness: String = "MySucculentCorner"
)
