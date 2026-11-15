package com.businesstracker.mileage.domain.tracking

import android.util.Log
import com.businesstracker.mileage.BuildConfig

/**
 * Structured diagnostic logger for tracking lifecycle, transition delivery,
 * candidate confirmation/expiry, motion resumption, and debounce stops.
 */
object TrackingDiagnostics {
    private const val TAG = "DriveDiagnostics"

    fun logTransition(
        eventTimeNanos: Long,
        receiptTimeNanos: Long,
        activityType: Int,
        transitionType: Int,
        bootCount: Int,
        isAccepted: Boolean,
        reason: String
    ) {
        val ageMs = (receiptTimeNanos - eventTimeNanos) / 1_000_000L
        Log.i(
            TAG,
            "[Transition] v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "act=$activityType trans=$transitionType age=${ageMs}ms boot=$bootCount " +
                "accepted=$isAccepted reason=$reason"
        )
    }

    fun logLifecycleState(
        tripId: String,
        state: String,
        reason: String
    ) {
        Log.i(
            TAG,
            "[Lifecycle] v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "trip=$tripId: state=$state ($reason)"
        )
    }

    fun logCandidateTransition(
        tripId: String,
        action: String,
        reason: String
    ) {
        Log.i(
            TAG,
            "[Candidate] trip=$tripId action=$action reason=$reason"
        )
    }

    fun logMotionResumption(
        tripId: String,
        status: String,
        detail: String
    ) {
        Log.i(
            TAG,
            "[MotionResumption] trip=$tripId status=$status detail=$detail"
        )
    }

    fun logCandidate(
        tripId: String,
        action: String,
        elapsedMs: Long,
        deadlineMs: Long,
        distanceMeters: Double
    ) {
        Log.i(
            TAG,
            "[Candidate] trip=$tripId action=$action elapsed=${elapsedMs}ms " +
                "deadline=${deadlineMs}ms dist=${distanceMeters}m"
        )
    }

    fun logMotionEvidence(
        tripId: String,
        speedMph: Double,
        accuracyMeters: Float,
        fixAgeMs: Long,
        qualifyingFixCount: Int,
        resumedMotion: Boolean,
        reason: String
    ) {
        Log.i(
            TAG,
            "[MotionEvidence] trip=$tripId speed=${speedMph}mph acc=${accuracyMeters}m " +
                "age=${fixAgeMs}ms qualifyingCount=$qualifyingFixCount " +
                "resumed=$resumedMotion reason=$reason"
        )
    }

    fun logStopDeadline(
        tripId: String,
        action: String,
        deadlineMs: Long,
        reason: String
    ) {
        Log.i(
            TAG,
            "[StopDeadline] trip=$tripId action=$action deadline=${deadlineMs}ms reason=$reason"
        )
    }
}
