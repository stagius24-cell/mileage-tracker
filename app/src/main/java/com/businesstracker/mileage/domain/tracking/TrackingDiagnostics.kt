package com.businesstracker.mileage.domain.tracking

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.businesstracker.mileage.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thread-safe, redacted structured diagnostic recorder and logger for tracking lifecycle,
 * Bluetooth state transitions, vehicle attribution, candidate confirmation/expiry,
 * motion resumption, and debounce stops.
 */
object TrackingDiagnostics {
    private const val TAG = "DriveDiagnostics"
    private const val MAX_MEMORY_EVENTS = 400
    private const val MAX_LOG_FILE_BYTES = 262_144L // 256 KB
    private const val LOG_FILE_NAME = "tracking_diagnostics.log"

    data class DiagnosticEvent(
        val timestampMillis: Long,
        val elapsedRealtimeMillis: Long,
        val category: String,
        val code: String,
        val detail: String
    ) {
        fun formatLine(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            return "${fmt.format(Date(timestampMillis))} [el=${elapsedRealtimeMillis}ms] [$category] $code :: $detail"
        }
    }

    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>(MAX_MEMORY_EVENTS)
    @Volatile
    private var logFile: File? = null

    private val macRegex = Regex("\\b([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}\\b")
    private val latLonPairRegex = Regex("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}")

    fun initialize(context: Context) {
        runCatching {
            val dir = context.applicationContext?.filesDir ?: context.filesDir ?: return
            synchronized(lock) {
                logFile = File(dir, LOG_FILE_NAME)
            }
        }
    }

    fun redactSensitive(raw: String): String {
        var sanitized = macRegex.replace(raw) { match ->
            val hexHash = Integer.toHexString(match.value.uppercase().hashCode()).padStart(4, '0').takeLast(4)
            "bt#$hexHash"
        }
        sanitized = latLonPairRegex.replace(sanitized, "[coord-redacted]")
        return sanitized
    }

    fun redactAddress(address: String?): String {
        if (address.isNullOrBlank()) return "bt#none"
        val normalized = address.trim().uppercase()
        val hexHash = Integer.toHexString(normalized.hashCode()).padStart(4, '0').takeLast(4)
        return "bt#$hexHash"
    }

    private fun record(category: String, code: String, rawDetail: String) {
        val redacted = redactSensitive(rawDetail)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        val event = DiagnosticEvent(
            timestampMillis = nowWall,
            elapsedRealtimeMillis = nowElapsed,
            category = category,
            code = code,
            detail = redacted
        )
        runCatching { Log.i(TAG, "[$category] v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) $code :: $redacted") }
        synchronized(lock) {
            if (events.size >= MAX_MEMORY_EVENTS) {
                events.removeFirst()
            }
            events.addLast(event)
            val file = logFile
            if (file != null) {
                runCatching {
                    if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                        val existingLines = file.readLines()
                        val kept = existingLines.takeLast((existingLines.size / 2).coerceAtLeast(50))
                        file.writeText(kept.joinToString("\n") + "\n")
                    }
                    file.appendText(event.formatLine() + "\n")
                }
            }
        }
    }

    fun getRecentEvents(): List<DiagnosticEvent> = synchronized(lock) { events.toList() }

    fun clearForTesting() {
        synchronized(lock) {
            events.clear()
        }
    }

    fun exportRedactedReport(context: Context?, extraHeaderLines: List<String> = emptyList()): String {
        if (context != null && logFile == null) {
            initialize(context)
        }
        val header = buildString {
            appendLine("=== Business Mileage Tracker Diagnostic Report ===")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("Generated: ${Date()}")
            extraHeaderLines.forEach { appendLine(redactSensitive(it)) }
            appendLine("==================================================")
        }
        val lines = synchronized(lock) {
            val fileLines = logFile?.takeIf { it.exists() }?.runCatching { readLines().takeLast(MAX_MEMORY_EVENTS) }?.getOrNull()
            if (!fileLines.isNullOrEmpty()) {
                fileLines.map { redactSensitive(it) }
            } else {
                events.map { it.formatLine() }
            }
        }
        return if (lines.isEmpty()) {
            header + "(No diagnostic events recorded yet)"
        } else {
            header + lines.joinToString("\n")
        }
    }

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
        record(
            category = "Transition",
            code = if (isAccepted) "ACCEPTED" else "REJECTED",
            rawDetail = "act=$activityType trans=$transitionType age=${ageMs}ms boot=$bootCount reason=$reason"
        )
    }

    fun logLifecycleState(
        tripId: String,
        state: String,
        reason: String
    ) {
        record("Lifecycle", state, "trip=$tripId reason=$reason")
    }

    fun logCandidateTransition(
        tripId: String,
        action: String,
        reason: String
    ) {
        record("Candidate", action, "trip=$tripId reason=$reason")
    }

    fun logMotionResumption(
        tripId: String,
        status: String,
        detail: String
    ) {
        record("MotionResumption", status, "trip=$tripId detail=$detail")
    }

    fun logCandidate(
        tripId: String,
        action: String,
        elapsedMs: Long,
        deadlineMs: Long,
        distanceMeters: Double
    ) {
        record("Candidate", action, "trip=$tripId elapsed=${elapsedMs}ms deadline=${deadlineMs}ms dist=${distanceMeters}m")
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
        record(
            "MotionEvidence",
            if (resumedMotion) "RESUMED" else "CHECKED",
            "trip=$tripId speed=${speedMph}mph acc=${accuracyMeters}m age=${fixAgeMs}ms qualifyingCount=$qualifyingFixCount reason=$reason"
        )
    }

    fun logStopDeadline(
        tripId: String,
        action: String,
        deadlineMs: Long,
        reason: String
    ) {
        record("StopDeadline", action, "trip=$tripId deadline=${deadlineMs}ms reason=$reason")
    }

    fun logArmedMonitor(action: String, reason: String) {
        record("BluetoothArmedMonitor", action, reason)
    }

    fun logBluetoothEvent(
        action: String,
        redactedDevice: String,
        matchedVehicleId: String?,
        candidateCount: Int,
        detail: String
    ) {
        record(
            "BluetoothEvent",
            action,
            "device=$redactedDevice matchedVehicleId=${matchedVehicleId ?: "none"} connectedCars=$candidateCount detail=$detail"
        )
    }

    fun logVehicleResolution(
        tripId: String?,
        resultCode: String,
        vehicleId: String?,
        candidateCount: Int,
        source: String
    ) {
        record(
            "VehicleResolution",
            resultCode,
            "trip=${tripId ?: "none"} vehicleId=${vehicleId ?: "unresolved"} candidates=$candidateCount source=$source"
        )
    }

    fun logAutoStartDecision(
        source: String,
        accepted: Boolean,
        reasonCode: String,
        detail: String
    ) {
        record(
            "AutoStartDecision",
            "${source}_${if (accepted) "ACCEPTED" else "REJECTED"}_$reasonCode",
            detail
        )
    }

    fun logUiVehicleState(
        tripId: String?,
        displayedName: String,
        connected: Boolean,
        ambiguousCount: Int
    ) {
        record(
            "UiVehicleState",
            "UPDATED",
            "trip=${tripId ?: "none"} displayed=\"$displayedName\" btConnected=$connected ambiguousCount=$ambiguousCount"
        )
    }

    fun logCandidateLaunchDetail(
        tripId: String,
        source: String,
        outcome: String,
        exceptionClass: String? = null,
        exceptionMessage: String? = null,
        appInForeground: Boolean? = null,
        ignoringBatteryOptimizations: Boolean? = null,
        fgLoc: Boolean? = null,
        bgLoc: Boolean? = null,
        actPerm: Boolean? = null,
        candidateStateBefore: String,
        candidateStateAfter: String,
        acknowledged: Boolean,
        elapsedSinceRequestMs: Long,
        deadlineRemainingMs: Long
    ) {
        val sanitizedErr = exceptionMessage?.take(120)?.replace('\n', ' ') ?: "none"
        record(
            category = "CandidateLaunch",
            code = outcome,
            rawDetail = "trip=$tripId src=$source ack=$acknowledged " +
                "elapsed=${elapsedSinceRequestMs}ms deadlineRem=${deadlineRemainingMs}ms " +
                "before=$candidateStateBefore after=$candidateStateAfter " +
                "exc=${exceptionClass ?: "none"} msg=\"$sanitizedErr\" " +
                "fg=${appInForeground ?: "unk"} dozeExempt=${ignoringBatteryOptimizations ?: "unk"} " +
                "perms=[fg=${fgLoc ?: "unk"},bg=${bgLoc ?: "unk"},act=${actPerm ?: "unk"}]"
        )
    }

    fun logMovementGate(
        gate: String,
        passed: Boolean,
        rawConf: Int,
        effectiveConf: Int,
        speedMps: Float?,
        displacementMeters: Double?,
        accuracyMeters: Float,
        sampleAgeMs: Long,
        sampleGapMs: Long,
        inPostConnectWindow: Boolean,
        hasPrev: Boolean,
        detail: String
    ) {
        val speedStr = speedMps?.let { String.format(Locale.US, "%.2fm/s", it) } ?: "null"
        val dispStr = displacementMeters?.let { String.format(Locale.US, "%.1fm", it) } ?: "null"
        val accStr = String.format(Locale.US, "%.2fm", accuracyMeters)
        record(
            category = "MovementGate",
            code = "${gate}_${if (passed) "PASS" else "FAIL"}",
            rawDetail = "rawConf=$rawConf effConf=$effectiveConf speed=$speedStr disp=$dispStr acc=$accStr " +
                "age=${sampleAgeMs}ms gap=${sampleGapMs}ms postConn=$inPostConnectWindow hasPrev=$hasPrev detail=$detail"
        )
    }
}
