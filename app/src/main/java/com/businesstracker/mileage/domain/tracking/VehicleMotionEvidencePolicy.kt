package com.businesstracker.mileage.domain.tracking

/** Movement gate for low-power, Bluetooth-armed Activity Recognition samples. */
object VehicleMotionEvidencePolicy {
    const val MIN_IN_VEHICLE_CONFIDENCE = 75
    const val MAX_ACCURACY_METERS = 50f
    const val MAX_SPEED_ACCURACY_METERS = 100f
    const val MIN_SPEED_METERS_PER_SECOND = 2.0f
    const val MIN_DISPLACEMENT_METERS = 45f
    const val MIN_SAMPLE_GAP_MILLIS = 10_000L
    const val MAX_SAMPLE_GAP_MILLIS = 180_000L

    data class Sample(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val speedMetersPerSecond: Float?,
        val elapsedRealtimeMillis: Long,
        val inVehicleConfidence: Int
    )

    /** A single reliable speed fix or corroborated displacement is required. */
    fun confirmsMovement(previous: Sample?, current: Sample): Boolean {
        if (current.inVehicleConfidence < MIN_IN_VEHICLE_CONFIDENCE ||
            !current.latitude.isFinite() || !current.longitude.isFinite() ||
            current.latitude !in -90.0..90.0 || current.longitude !in -180.0..180.0 ||
            current.elapsedRealtimeMillis <= 0L
        ) return false

        val speed = current.speedMetersPerSecond
        if (current.accuracyMeters in 0f..MAX_SPEED_ACCURACY_METERS &&
            speed != null && speed.isFinite() && speed >= MIN_SPEED_METERS_PER_SECOND
        ) return true

        if (current.accuracyMeters !in 0f..MAX_ACCURACY_METERS) return false

        if (previous == null || previous.inVehicleConfidence < MIN_IN_VEHICLE_CONFIDENCE ||
            previous.accuracyMeters !in 0f..MAX_ACCURACY_METERS ||
            current.elapsedRealtimeMillis <= previous.elapsedRealtimeMillis
        ) return false

        val gap = current.elapsedRealtimeMillis - previous.elapsedRealtimeMillis
        if (gap !in MIN_SAMPLE_GAP_MILLIS..MAX_SAMPLE_GAP_MILLIS) return false

        val displacementMeters = distanceMeters(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude
        )
        return displacementMeters.isFinite() && displacementMeters >= MIN_DISPLACEMENT_METERS
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(lat2 - lat1)
        val longitudeDelta = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(longitudeDelta / 2).let { it * it }
        return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
