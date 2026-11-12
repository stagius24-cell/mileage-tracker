package com.businesstracker.mileage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_location_sync_queue")
data class SavedLocationSyncQueueEntity(
    @PrimaryKey
    val locationId: String,
    val operation: String,
    val updatedAt: Long
) {
    companion object {
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
    }
}
