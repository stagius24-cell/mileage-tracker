package com.businesstracker.mileage.domain.model

enum class ClassificationSource(val value: String) {
    MANUAL("MANUAL"),
    SUGGESTED_CONFIRMED("SUGGESTED_CONFIRMED"),
    AUTO_CLASSIFIED("AUTO_CLASSIFIED"),
    UNCLASSIFIED("UNCLASSIFIED");

    companion object {
        fun fromValue(value: String?): ClassificationSource {
            return values().firstOrNull { it.value.equals(value, ignoreCase = true) } ?: UNCLASSIFIED
        }
    }
}
