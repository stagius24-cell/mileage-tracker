package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["date"]),
        Index(value = ["classification"]),
        Index(value = ["businessEntity"]),
        Index(value = ["syncStatus"])
    ]
)
data class TripEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD

    @ColumnInfo(name = "startTime")
    val startTime: Long,

    @ColumnInfo(name = "endTime")
    val endTime: Long,

    @ColumnInfo(name = "startAddress")
    val startAddress: String?,

    @ColumnInfo(name = "endAddress")
    val endAddress: String?,

    @ColumnInfo(name = "startLatitude")
    val startLatitude: Double,

    @ColumnInfo(name = "startLongitude")
    val startLongitude: Double,

    @ColumnInfo(name = "endLatitude")
    val endLatitude: Double,

    @ColumnInfo(name = "endLongitude")
    val endLongitude: Double,

    @ColumnInfo(name = "totalDistanceMiles")
    val totalDistanceMiles: Double,

    @ColumnInfo(name = "businessMiles")
    val businessMiles: Double,

    @ColumnInfo(name = "businessEntity")
    val businessEntity: String, // "MySucculentCorner", "Haneelove", "Personal"

    @ColumnInfo(name = "purpose")
    val purpose: String,

    @ColumnInfo(name = "trackingMethod")
    val trackingMethod: String, // "AUTO_DETECTED", "MANUAL_GPS", "MANUAL_ODOMETER"

    @ColumnInfo(name = "classification")
    val classification: String, // "UNREVIEWED", "BUSINESS", "PERSONAL"

    @ColumnInfo(name = "applicableRate")
    val applicableRate: Double, // Dynamic IRS rate

    @ColumnInfo(name = "calculatedDeduction")
    val calculatedDeduction: Double, // businessMiles * applicableRate

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "waypointsJson")
    val waypointsJson: String, // JSON list of Waypoint objects

    @ColumnInfo(name = "syncStatus")
    val syncStatus: String, // "PENDING", "SYNCED", "FAILED"

    @ColumnInfo(name = "syncedAt")
    val syncedAt: Long?
)
