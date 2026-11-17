package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-defined vehicle identified from a paired device by its stable Bluetooth address. */
@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["bluetoothAddress"], unique = true)]
)
data class VehicleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "bluetoothAddress") val bluetoothAddress: String? = null,
    @ColumnInfo(name = "bluetoothDeviceName") val bluetoothDeviceName: String? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long
)
