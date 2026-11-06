package com.businesstracker.mileage.domain.model

enum class TripClassification(val value: String) {
    UNREVIEWED("UNREVIEWED"),
    BUSINESS("BUSINESS"),
    PERSONAL("PERSONAL");

    companion object {
        fun fromString(value: String): TripClassification {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: UNREVIEWED
        }
    }
}
