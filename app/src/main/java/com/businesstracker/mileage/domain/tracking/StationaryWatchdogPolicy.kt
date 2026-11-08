package com.businesstracker.mileage.domain.tracking

/** The service uses this policy for bounded location rechecks; silence never authorizes a stop. */
object StationaryWatchdogPolicy {
    const val RECHECK_INTERVAL_MILLIS = 60_000L
    const val MAX_FIX_AGE_MILLIS = 30_000L

    fun canRestore(
        evidenceTripId: String,
        activeTripId: String,
        evidenceBootCount: Int,
        currentBootCount: Int,
        evidenceElapsedMillis: Long,
        nowElapsedMillis: Long
    ): Boolean = evidenceTripId == activeTripId && evidenceBootCount > 0 &&
        evidenceBootCount == currentBootCount && evidenceElapsedMillis >= 0L &&
        evidenceElapsedMillis <= nowElapsedMillis

    fun shouldRecheck(
        evidence: StationaryAutoStopMonitor.Snapshot?,
        nowElapsedMillis: Long,
        lastRecheckElapsedMillis: Long,
        automaticConfirmed: Boolean,
        pausedOrStopping: Boolean
    ): Boolean {
        if (!automaticConfirmed || pausedOrStopping || evidence == null ||
            nowElapsedMillis < evidence.elapsedMillis ||
            nowElapsedMillis < lastRecheckElapsedMillis
        ) return false
        return nowElapsedMillis - evidence.elapsedMillis >= StationaryAutoStopMonitor.DEFAULT_TIMEOUT_MILLIS &&
            nowElapsedMillis - lastRecheckElapsedMillis >= RECHECK_INTERVAL_MILLIS
    }

    fun isFreshDeadlineFix(
        evidence: StationaryAutoStopMonitor.Snapshot?,
        fixElapsedMillis: Long,
        nowElapsedMillis: Long
    ): Boolean {
        if (evidence == null || fixElapsedMillis <= 0L ||
            fixElapsedMillis < evidence.elapsedMillis || nowElapsedMillis < fixElapsedMillis
        ) return false
        return nowElapsedMillis - fixElapsedMillis <= MAX_FIX_AGE_MILLIS &&
            fixElapsedMillis - evidence.elapsedMillis >= StationaryAutoStopMonitor.DEFAULT_TIMEOUT_MILLIS
    }
}
