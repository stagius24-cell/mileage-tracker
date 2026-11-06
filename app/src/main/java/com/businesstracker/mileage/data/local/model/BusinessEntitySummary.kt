package com.businesstracker.mileage.data.local.model

data class BusinessEntitySummary(
    val businessEntity: String,
    val totalBusinessMiles: Double,
    val totalMiles: Double,
    val totalDeduction: Double,
    val tripCount: Int
)
