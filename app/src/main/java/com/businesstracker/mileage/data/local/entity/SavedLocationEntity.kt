package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_locations",
    indices = [
        Index(value = ["name"]),
        Index(value = ["latitude", "longitude"])
    ]
)
data class SavedLocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID

    @ColumnInfo(name = "name")
    val name: String, // e.g. "Home", "Succulent Nursery"

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "radiusMeters")
    val radiusMeters: Int = 150, // Default 150m, range 50..500

    @ColumnInfo(name = "icon")
    val icon: String = "location", // "home", "nursery", "work", "store", "school", "warehouse", "location"

    @ColumnInfo(name = "classificationPreference")
    val classificationPreference: String? = null, // "BUSINESS", "PERSONAL", or null

    @ColumnInfo(name = "businessEntity")
    val businessEntity: String? = null, // "MySucculentCorner", "Haneelove", or null

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
