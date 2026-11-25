package com.businesstracker.mileage.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.businesstracker.mileage.domain.tracking.TrackingDiagnostics
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-power passive GPS wakeup evaluator that detects driving even when vehicle Bluetooth
 * never connects and Google Play Services ActivityTransition stalls or misses IN_VEHICLE ENTER.
 *
 * Woken only after balanced-power location updates cross the 150m displacement threshold, then
 * confirms genuine driving via high-accuracy speed (>= 4.5 m/s) or corroborated displacement
 * (>= 150m over 10s..180s at >= 4.5 m/s implied speed) before triggering a candidate drive.
 */
@Singleton
class PassiveDriveMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MIN_DRIVING_SPEED_MPS = 4.5f
        const val MAX_SPEED_ACCURACY_METERS = 35.0f
        const val MIN_DISPLACEMENT_METERS = 150.0
        const val MAX_ANCHOR_ACCURACY_METERS = 50.0f
        const val MIN_DISPLACEMENT_GAP_MILLIS = 10_000L
        const val MAX_DISPLACEMENT_GAP_MILLIS = 180_000L
        const val MIN_IMPLIED_SPEED_MPS = 4.5
        const val MAX_INCOMING_FIX_AGE_MILLIS = 60_000L
        const val MAX_CONFIRMING_FIX_AGE_MILLIS = 45_000L
        const val MAX_FUTURE_CLOCK_SKEW_MILLIS = 5_000L
        const val MIN_PROBE_GAP_MILLIS = 30_000L
        const val STARTUP_TRIGGER_COOLDOWN_MILLIS = 60_000L
        const val STATIONARY_ANCHOR_SKIP_METERS = 100.0
        const val STATIONARY_SPEED_MPS = 2.0f
        private const val ANCHOR_SKIP_MAX_ACCURACY_METERS = 80.0f
        private const val PROBE_TIMEOUT_MILLIS = 10_000L
    }

    private data class HighAccuracyAnchor(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val elapsedRealtimeMillis: Long
    )

    private data class DisplacementCheck(
        val distanceMeters: Double,
        val gapMs: Long,
        val impliedSpeedMps: Double,
        val confirmed: Boolean
    )

    private val mutex = Mutex()
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var previousAnchor: HighAccuracyAnchor? = null
    private var lastProbeElapsed: Long = 0L
    private var lastTriggeredElapsed: Long = 0L

    @VisibleForTesting
    internal var elapsedClock: () -> Long = SystemClock::elapsedRealtime

    @VisibleForTesting
    internal var highAccuracyLocationProvider: (suspend () -> Location?)? = null

    fun resetTriggerCooldown() {
        lastTriggeredElapsed = 0L
        lastProbeElapsed = 0L
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        previousAnchor = null
        lastProbeElapsed = 0L
        lastTriggeredElapsed = 0L
    }

    @SuppressLint("MissingPermission")
    suspend fun evaluateWakeup(locations: List<Location>): Boolean = mutex.withLock {
        val incoming = locations.maxByOrNull { it.elapsedRealtimeNanos } ?: return@withLock false
        val now = elapsedClock()

        if (lastTriggeredElapsed > 0L && now - lastTriggeredElapsed < STARTUP_TRIGGER_COOLDOWN_MILLIS) {
            return@withLock false
        }

        val fixElapsed = incoming.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: 0L
        val futureSkewMs = fixElapsed - now
        val ageMs = (now - fixElapsed).coerceAtLeast(0L)
        if (fixElapsed <= 0L || futureSkewMs > MAX_FUTURE_CLOCK_SKEW_MILLIS || ageMs > MAX_INCOMING_FIX_AGE_MILLIS) {
            return@withLock false
        }

        val acc = if (incoming.hasAccuracy()) incoming.accuracy else Float.MAX_VALUE
        val speed = if (incoming.hasSpeed()) incoming.speed else null

        if (acc <= MAX_SPEED_ACCURACY_METERS &&
            speed != null &&
            speed >= MIN_DRIVING_SPEED_MPS &&
            ageMs <= MAX_CONFIRMING_FIX_AGE_MILLIS
        ) {
            lastTriggeredElapsed = now
            previousAnchor = null
            TrackingDiagnostics.logPassiveMonitor(
                "DRIVING_CONFIRMED",
                "via=PASSIVE_SPEED speed=${fmtFloat(speed)}m/s acc=${fmtFloat(acc)}m age=${ageMs}ms"
            )
            return@withLock true
        }

        if (acc <= MAX_ANCHOR_ACCURACY_METERS && ageMs <= MAX_CONFIRMING_FIX_AGE_MILLIS) {
            val dispResult = evaluateDisplacement(previousAnchor, incoming, fixElapsed)
            if (dispResult != null && dispResult.confirmed) {
                lastTriggeredElapsed = now
                previousAnchor = null
                TrackingDiagnostics.logPassiveMonitor(
                    "DRIVING_CONFIRMED",
                    "via=PASSIVE_DISPLACEMENT speed=${speed?.let(::fmtFloat) ?: "null"} acc=${fmtFloat(acc)}m disp=${fmtDouble(dispResult.distanceMeters)}m implied=${fmtDouble(dispResult.impliedSpeedMps)}m/s"
                )
                return@withLock true
            }
        }

        if (acc <= MAX_SPEED_ACCURACY_METERS && speed != null && speed < STATIONARY_SPEED_MPS) {
            previousAnchor = HighAccuracyAnchor(incoming.latitude, incoming.longitude, acc, fixElapsed)
            return@withLock false
        }

        val anchor = previousAnchor
        if (anchor != null && acc <= ANCHOR_SKIP_MAX_ACCURACY_METERS) {
            val distOut = FloatArray(1)
            Location.distanceBetween(
                anchor.latitude, anchor.longitude,
                incoming.latitude, incoming.longitude,
                distOut
            )
            if (distOut[0] < STATIONARY_ANCHOR_SKIP_METERS) {
                return@withLock false
            }
        }

        if (lastProbeElapsed > 0L && now - lastProbeElapsed < MIN_PROBE_GAP_MILLIS) {
            return@withLock false
        }
        lastProbeElapsed = now

        val probe = fetchSingleHighAccuracyLocation() ?: return@withLock false
        val postNow = maxOf(now, elapsedClock())
        val probeElapsed = probe.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: 0L
        val probeFutureSkewMs = probeElapsed - postNow
        val probeAgeMs = (postNow - probeElapsed).coerceAtLeast(0L)
        if (probeElapsed <= 0L ||
            probeFutureSkewMs > MAX_FUTURE_CLOCK_SKEW_MILLIS ||
            probeAgeMs > MAX_CONFIRMING_FIX_AGE_MILLIS
        ) {
            return@withLock false
        }

        val probeAcc = if (probe.hasAccuracy()) probe.accuracy else Float.MAX_VALUE
        val probeSpeed = if (probe.hasSpeed()) probe.speed else null
        val speedConfirmed = probeAcc <= MAX_SPEED_ACCURACY_METERS &&
            probeSpeed != null &&
            probeSpeed >= MIN_DRIVING_SPEED_MPS
        val dispResult = if (probeAcc <= MAX_ANCHOR_ACCURACY_METERS) {
            evaluateDisplacement(previousAnchor, probe, probeElapsed)
        } else {
            null
        }
        val dispConfirmed = dispResult?.confirmed == true

        return@withLock if (speedConfirmed || dispConfirmed) {
            lastTriggeredElapsed = postNow
            previousAnchor = null
            TrackingDiagnostics.logPassiveMonitor(
                "DRIVING_CONFIRMED",
                "via=${if (speedConfirmed) "HIGH_ACC_SPEED" else "HIGH_ACC_DISPLACEMENT"} " +
                    "speed=${probeSpeed?.let(::fmtFloat) ?: "null"} acc=${fmtFloat(probeAcc)}m " +
                    "disp=${dispResult?.let { fmtDouble(it.distanceMeters) } ?: "null"}m " +
                    "implied=${dispResult?.let { fmtDouble(it.impliedSpeedMps) } ?: "null"}m/s"
            )
            true
        } else {
            if (probeAcc <= MAX_ANCHOR_ACCURACY_METERS) {
                val existing = previousAnchor
                val gapTooLong = existing != null && (probeElapsed - existing.elapsedRealtimeMillis) > MAX_DISPLACEMENT_GAP_MILLIS
                val isStationary = probeSpeed != null && probeSpeed < STATIONARY_SPEED_MPS
                if (existing == null || isStationary || gapTooLong) {
                    previousAnchor = HighAccuracyAnchor(probe.latitude, probe.longitude, probeAcc, probeElapsed)
                }
            }
            TrackingDiagnostics.logPassiveMonitor(
                "REJECTED_NO_DRIVING_EVIDENCE",
                "speed=${probeSpeed?.let(::fmtFloat) ?: "null"} acc=${fmtFloat(probeAcc)}m " +
                    "disp=${dispResult?.let { fmtDouble(it.distanceMeters) } ?: "null"}m " +
                    "implied=${dispResult?.let { fmtDouble(it.impliedSpeedMps) } ?: "null"}m/s " +
                    "hasAnchor=${previousAnchor != null}"
            )
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchSingleHighAccuracyLocation(): Location? {
        val testProvider = highAccuracyLocationProvider
        if (testProvider != null) {
            return try {
                testProvider.invoke()
            } catch (e: Exception) {
                TrackingDiagnostics.logPassiveMonitor("PROBE_UNAVAILABLE", e.javaClass.simpleName)
                null
            }
        }
        return try {
            val cancellation = CancellationTokenSource()
            try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(5_000L)
                    .setDurationMillis(8_000L)
                    .build()
                withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
                    fusedLocationClient.getCurrentLocation(request, cancellation.token).await()
                }
            } finally {
                cancellation.cancel()
            }
        } catch (e: Exception) {
            TrackingDiagnostics.logPassiveMonitor("PROBE_UNAVAILABLE", e.javaClass.simpleName)
            null
        }
    }

    private fun evaluateDisplacement(
        anchor: HighAccuracyAnchor?,
        loc: Location,
        locElapsedMs: Long
    ): DisplacementCheck? {
        if (anchor == null) return null
        val gapMs = locElapsedMs - anchor.elapsedRealtimeMillis
        if (gapMs <= 0L) return null
        val out = FloatArray(1)
        Location.distanceBetween(
            anchor.latitude, anchor.longitude,
            loc.latitude, loc.longitude,
            out
        )
        val distanceMeters = out[0].toDouble()
        val impliedSpeedMps = distanceMeters / (gapMs / 1000.0)
        val confirmed = gapMs in MIN_DISPLACEMENT_GAP_MILLIS..MAX_DISPLACEMENT_GAP_MILLIS &&
            distanceMeters >= MIN_DISPLACEMENT_METERS &&
            impliedSpeedMps >= MIN_IMPLIED_SPEED_MPS
        return DisplacementCheck(
            distanceMeters = distanceMeters,
            gapMs = gapMs,
            impliedSpeedMps = impliedSpeedMps,
            confirmed = confirmed
        )
    }

    private fun fmtFloat(value: Float): String = String.format(Locale.US, "%.2f", value)
    private fun fmtDouble(value: Double): String = String.format(Locale.US, "%.2f", value)
}
