package com.businesstracker.mileage.domain.model

data class DashboardSummary(
    val totalMiles: Double = 0.0,
    val businessMiles: Double = 0.0,
    val personalMiles: Double = 0.0,
    val unreviewedMiles: Double = 0.0,
    val estimatedDeduction: Double = 0.0,
    val totalTripsCount: Int = 0,
    val unreviewedTripsCount: Int = 0
)
