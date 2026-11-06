package com.businesstracker.mileage.domain.model

enum class TrackingMethod(val value: String) {
    AUTO_DETECTED("AUTO_DETECTED"),
    MANUAL_GPS("MANUAL_GPS"),
    MANUAL_ODOMETER("MANUAL_ODOMETER");

    companion object {
        fun fromString(value: String): TrackingMethod {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: AUTO_DETECTED
        }
    }
}
