package com.businesstracker.mileage.domain.model

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speed: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0L,
    val elapsedRealtimeNanos: Long = 0L,
    val bootCount: Int = 0
)
