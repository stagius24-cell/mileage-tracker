package com.businesstracker.mileage.domain.model

enum class AppThemeMode(val storedValue: String) {
    AUTO("AUTO"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}
