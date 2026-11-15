package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_patterns",
    indices = [
        Index(value = ["startLatitude", "startLongitude"]),
        Index(value = ["endLatitude", "endLongitude"])
    ]
)
data class RoutePatternEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "startLatitude")
    val startLatitude: Double,

    @ColumnInfo(name = "startLongitude")
    val startLongitude: Double,

    @ColumnInfo(name = "startRadiusMeters")
    val startRadiusMeters: Double = 250.0,

    @ColumnInfo(name = "endLatitude")
    val endLatitude: Double,

    @ColumnInfo(name = "endLongitude")
    val endLongitude: Double,

    @ColumnInfo(name = "endRadiusMeters")
    val endRadiusMeters: Double = 250.0,

    @ColumnInfo(name = "startAddress")
    val startAddress: String? = null,

    @ColumnInfo(name = "endAddress")
    val endAddress: String? = null,

    @ColumnInfo(name = "classification")
    val classification: String,

    @ColumnInfo(name = "businessEntity")
    val businessEntity: String,

    @ColumnInfo(name = "purpose")
    val purpose: String,

    @ColumnInfo(name = "sampleCount")
    val sampleCount: Int,

    @ColumnInfo(name = "confidence")
    val confidence: Double,

    @ColumnInfo(name = "lastLearnedAt")
    val lastLearnedAt: Long,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long
)
