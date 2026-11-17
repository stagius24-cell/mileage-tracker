package com.businesstracker.mileage.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class TripDetailsDisplayFormatterTest {
    @Test fun personalDefaultPurposesDisplayOnlyPersonal() {
        assertEquals("Personal", TripDetailsDisplayFormatter.tripTypeAndPurpose("PERSONAL", "Personal Drive").value)
        assertEquals("Personal", TripDetailsDisplayFormatter.tripTypeAndPurpose("PERSONAL", "Personal").value)
        assertEquals("Trip type", TripDetailsDisplayFormatter.tripTypeAndPurpose("PERSONAL", "Personal Drive").label)
    }

    @Test fun businessDefaultPurposeDisplaysOnlyBusiness() {
        val result = TripDetailsDisplayFormatter.tripTypeAndPurpose("BUSINESS", "Business Drive")
        assertEquals("Business", result.value)
        assertEquals("Trip type", result.label)
    }

    @Test fun customBusinessPurposeStaysInlineWithClassification() {
        val result = TripDetailsDisplayFormatter.tripTypeAndPurpose("BUSINESS", "Client Meeting")
        assertEquals("Trip type & Purpose", result.label)
        assertEquals("Business · Client Meeting", result.value)
    }

    @Test fun longBusinessPurposeIsPreservedForComposeEllipsisHandling() {
        val purpose = "Quarterly regional client planning meeting and project review"
        assertEquals("Business · $purpose", TripDetailsDisplayFormatter.tripTypeAndPurpose("BUSINESS", purpose).value)
    }

    @Test fun meaningfulPersonalPurposeIsPreserved() {
        assertEquals(
            "Personal · Family appointment",
            TripDetailsDisplayFormatter.tripTypeAndPurpose("PERSONAL", "Family appointment").value
        )
    }

    @Test fun missingPurposeShowsClassificationOnly() {
        assertEquals("Business", TripDetailsDisplayFormatter.tripTypeAndPurpose("BUSINESS", " ").value)
    }

    @Test fun trackingSpeedAndRateUseFriendlyAccurateValues() {
        assertEquals("Auto detected", TripDetailsDisplayFormatter.trackingMethod("AUTO_DETECTED"))
        assertEquals("Manual GPS", TripDetailsDisplayFormatter.trackingMethod("MANUAL_GPS"))
        assertEquals("Avg 21.8 mph\nMax 62.8 mph", TripDetailsDisplayFormatter.speedSummary(21.8, 62.8))
        assertEquals("$0.76 / mi", TripDetailsDisplayFormatter.irsRate(0.76))
    }
}
