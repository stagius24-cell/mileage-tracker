package com.businesstracker.mileage.domain.model

enum class BusinessEntity(val displayName: String) {
    MY_SUCCULENT_CORNER("MySucculentCorner"),
    PERSONAL("Personal");

    companion object {
        fun fromString(value: String): BusinessEntity {
            return entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) }
                ?: MY_SUCCULENT_CORNER
        }
    }
}
