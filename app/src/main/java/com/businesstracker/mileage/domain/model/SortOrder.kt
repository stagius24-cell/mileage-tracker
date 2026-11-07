package com.businesstracker.mileage.domain.model

enum class SortOrder(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_MILEAGE("Highest Mileage"),
    LOWEST_MILEAGE("Lowest Mileage")
}
