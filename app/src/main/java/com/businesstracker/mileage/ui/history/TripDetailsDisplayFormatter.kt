package com.businesstracker.mileage.ui.history

import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.domain.model.TripClassification
import java.util.Locale

/** Presentation-only formatting for Trip Details. Never writes back to trip data. */
internal object TripDetailsDisplayFormatter {
    data class TripTypePurpose(val label: String, val value: String)

    fun tripTypeAndPurpose(classificationValue: String, purpose: String): TripTypePurpose {
        val classification = TripClassification.fromString(classificationValue)
        val type = when (classification) {
            TripClassification.BUSINESS -> "Business"
            TripClassification.PERSONAL -> "Personal"
            TripClassification.UNREVIEWED -> "Unreviewed"
        }
        val normalizedPurpose = purpose.trim()
        val isDefault = when (classification) {
            TripClassification.PERSONAL -> normalizedPurpose.equals("Personal Drive", true) ||
                normalizedPurpose.equals("Personal", true)
            TripClassification.BUSINESS -> normalizedPurpose.isBlank() ||
                normalizedPurpose.equals("Business Drive", true) ||
                normalizedPurpose.equals("Business", true) ||
                normalizedPurpose.equals("General Business", true)
            TripClassification.UNREVIEWED -> true
        }

        return if (normalizedPurpose.isNotEmpty() && !isDefault) {
            TripTypePurpose("Trip type & Purpose", "$type · $normalizedPurpose")
        } else {
            TripTypePurpose("Trip type", type)
        }
    }

    fun trackingMethod(value: String): String = when (TrackingMethod.fromString(value)) {
        TrackingMethod.AUTO_DETECTED -> "Auto detected"
        TrackingMethod.MANUAL_GPS -> "Manual GPS"
        TrackingMethod.MANUAL_ODOMETER -> "Manual odometer"
    }

    fun speedSummary(avgMph: Double, maxMph: Double): String {
        val average = avgMph.takeIf { it.isFinite() && it > 0.0 }
            ?.let { String.format(Locale.US, "%.1f mph", it) } ?: "--"
        val maximum = maxMph.takeIf { it.isFinite() && it > 0.0 }
            ?.let { String.format(Locale.US, "%.1f mph", it) } ?: "--"
        return "Avg $average\nMax $maximum"
    }

    fun irsRate(rate: Double): String = String.format(Locale.US, "$%.2f / mi", rate)
}
