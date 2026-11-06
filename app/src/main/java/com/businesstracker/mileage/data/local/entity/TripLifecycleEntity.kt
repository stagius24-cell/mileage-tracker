package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TripLifecycleState(val value: String) {
    STARTING("STARTING"),
    TRACKING("TRACKING"),
    DRAINING("DRAINING"),
    READY_TO_FINALIZE("READY_TO_FINALIZE"),
    FINALIZED("FINALIZED"),
    DISCARDED("DISCARDED"),
    RECOVERY_REQUIRED("RECOVERY_REQUIRED");

    companion object {
        fun fromString(value: String?): TripLifecycleState? {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Authoritative Room-backed trip lifecycle state.
 * Prevents race conditions between LocationTrackingService and TripDebounceWorker,
 * ensures exact-ID terminal outcome verification, and survives process death/reboots.
 */
@Entity(tableName = "trip_lifecycle")
data class TripLifecycleEntity(
    @PrimaryKey
    @ColumnInfo(name = "tripId")
    val tripId: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "reason")
    val reason: String? = null
)
