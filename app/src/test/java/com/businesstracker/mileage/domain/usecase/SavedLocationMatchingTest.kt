package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SavedLocationMatchingTest {

    private val matchLocationUseCase = MatchLocationUseCase()
    private val resolveSmartTripNamesUseCase = ResolveSmartTripNamesUseCase(matchLocationUseCase)

    @Test
    fun conflictingBusinessEntitiesDoNotSuggestClassification() {
        val suggestion = resolveSmartTripNamesUseCase.evaluateClassificationSuggestion(
            nursery, nursery.copy(id = "other", businessEntity = "Other business")
        )
        assertNull(suggestion.suggestedClassification)
        assertNull(suggestion.suggestedBusinessEntity)
    }

    // Base location: San Jose Succulent Nursery (37.3382, -121.8863)
    private val nursery = SavedLocationEntity(
        id = "loc-nursery",
        name = "Succulent Nursery",
        latitude = 37.33820,
        longitude = -121.88630,
        address = "100 Blossom Hill Rd, San Jose, CA",
        radiusMeters = 150,
        icon = "nursery",
        classificationPreference = "BUSINESS",
        businessEntity = "MySucculentCorner"
    )

    // Base location: Home (37.34000, -121.88000) ~700 meters away
    private val home = SavedLocationEntity(
        id = "loc-home",
        name = "Home",
        latitude = 37.34000,
        longitude = -121.88000,
        address = "500 Home Way, San Jose, CA",
        radiusMeters = 200,
        icon = "home",
        classificationPreference = "PERSONAL",
        businessEntity = null
    )

    // Overlapping location near Nursery (e.g. 50 meters away, radius 150)
    private val plantSupplier = SavedLocationEntity(
        id = "loc-supplier",
        name = "Plant Supplier",
        latitude = 37.33850,
        longitude = -121.88630,
        address = "120 Blossom Hill Rd, San Jose, CA",
        radiusMeters = 150,
        icon = "store",
        classificationPreference = "BUSINESS",
        businessEntity = "MySucculentCorner"
    )

    // Requirement 3: Matching within the configured radius
    @Test
    fun matchesLocation_withinConfiguredRadius() {
        // Point 40 meters from nursery
        val lat = 37.33850
        val lng = -121.88630
        val matched = matchLocationUseCase(lat, lng, listOf(nursery))
        assertNotNull(matched)
        assertEquals("Succulent Nursery", matched?.name)
    }

    // Requirement 4: Not matching outside the radius
    @Test
    fun doesNotMatch_outsideConfiguredRadius() {
        // Point 600 meters from nursery
        val lat = 37.34300
        val lng = -121.88630
        val matched = matchLocationUseCase(lat, lng, listOf(nursery))
        assertNull(matched)
    }

    // Requirement 5: Choosing closest match when locations overlap
    @Test
    fun choosesClosestMatch_whenLocationsOverlap() {
        // Query coordinate at (37.33825, -121.88630) -> closer to nursery (distance ~5.5m) than plantSupplier (~27m)
        val lat = 37.33825
        val lng = -121.88630
        val matched = matchLocationUseCase(lat, lng, listOf(nursery, plantSupplier))
        assertNotNull(matched)
        assertEquals("Succulent Nursery", matched?.name)

        // Query coordinate at (37.33845, -121.88630) -> closer to plantSupplier
        val matchedSupplier = matchLocationUseCase(37.33845, -121.88630, listOf(nursery, plantSupplier))
        assertNotNull(matchedSupplier)
        assertEquals("Plant Supplier", matchedSupplier?.name)
    }

    // Requirement 6: Matching start and destination independently
    @Test
    fun matchesStartAndDestination_independently() {
        val trip = TripEntity(
            id = "trip-1",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.34000, // Home
            startLongitude = -121.88000,
            endLatitude = 37.33820, // Nursery
            endLongitude = -121.88630,
            totalDistanceMiles = 5.0,
            businessMiles = 5.0,
            businessEntity = "MySucculentCorner",
            purpose = "Plant run",
            trackingMethod = "AUTO_DETECTED",
            classification = "UNREVIEWED",
            applicableRate = 0.67,
            calculatedDeduction = 3.35,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            startAddress = "500 Home Way, San Jose, CA",
            endAddress = "100 Blossom Hill Rd, San Jose, CA",
            notes = null,
            syncedAt = null
        )

        val names = resolveSmartTripNamesUseCase.resolve(trip, listOf(home, nursery))
        assertEquals("Home", names.startDisplayName)
        assertEquals("Succulent Nursery", names.endDisplayName)
        assertEquals("Home → Succulent Nursery", names.formattedDisplayRoute)
    }

    // Requirement 7 & 8: Applying names to historical trips and falling back to original addresses
    @Test
    fun fallsBackToOriginalAddress_whenEndpointDoesNotMatch() {
        val tripOnlyStartMatched = TripEntity(
            id = "trip-2",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.34000, // Home
            startLongitude = -121.88000,
            endLatitude = 37.99999, // Unmatched remote destination
            endLongitude = -122.99999,
            totalDistanceMiles = 25.0,
            businessMiles = 0.0,
            businessEntity = "Personal",
            purpose = "",
            trackingMethod = "AUTO_DETECTED",
            classification = "PERSONAL",
            applicableRate = 0.67,
            calculatedDeduction = 0.0,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            startAddress = "500 Home Way, San Jose, CA",
            endAddress = "999 Remote Blvd, Oakland, CA",
            notes = null,
            syncedAt = null
        )

        val names = resolveSmartTripNamesUseCase.resolve(tripOnlyStartMatched, listOf(home, nursery))
        assertEquals("Home", names.startDisplayName)
        assertEquals("999 Remote Blvd, Oakland, CA", names.endDisplayName)
        assertEquals("Home → 999 Remote Blvd, Oakland, CA", names.formattedDisplayRoute)
    }

    // Requirement 9: Preserving raw trip data (smart names do not mutate trip addresses or coordinates)
    @Test
    fun preservesRawTripData_asPresentationAliases() {
        val rawStart = "2370 Example Street"
        val rawEnd = "123 Nursery Road"
        val trip = TripEntity(
            id = "trip-3",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.34000,
            startLongitude = -121.88000,
            endLatitude = 37.33820,
            endLongitude = -121.88630,
            totalDistanceMiles = 5.0,
            businessMiles = 5.0,
            businessEntity = "MySucculentCorner",
            purpose = "Pickup",
            trackingMethod = "AUTO_DETECTED",
            classification = "BUSINESS",
            applicableRate = 0.67,
            calculatedDeduction = 3.35,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            startAddress = rawStart,
            endAddress = rawEnd,
            notes = null,
            syncedAt = null
        )

        val names = resolveSmartTripNamesUseCase.resolve(trip, listOf(home, nursery))
        assertEquals("Home", names.startDisplayName)
        assertEquals("Succulent Nursery", names.endDisplayName)
        // Verify underlying entity was untouched
        assertEquals(rawStart, trip.startAddress)
        assertEquals(rawEnd, trip.endAddress)
    }

    // Requirement 10: Classification suggestions (single match or unanimous agreement)
    @Test
    fun classificationSuggestion_unanimousOrSingle_suggestsClassification() {
        // Start is Business Nursery, end is neutral (no preference)
        val neutralLocation = SavedLocationEntity(
            id = "loc-neutral",
            name = "Coffee Shop",
            latitude = 37.35000,
            longitude = -121.89000,
            address = "77 Main St",
            radiusMeters = 150,
            icon = "store",
            classificationPreference = null,
            businessEntity = null
        )

        val trip = TripEntity(
            id = "trip-4",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.33820, // Nursery (BUSINESS)
            startLongitude = -121.88630,
            endLatitude = 37.35000, // Coffee Shop (NONE)
            endLongitude = -121.89000,
            totalDistanceMiles = 2.0,
            businessMiles = 0.0,
            businessEntity = "MySucculentCorner",
            purpose = "",
            trackingMethod = "AUTO_DETECTED",
            classification = "UNREVIEWED",
            applicableRate = 0.67,
            calculatedDeduction = 0.0,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            startAddress = "100 Blossom Hill Rd, San Jose, CA",
            endAddress = "77 Main St",
            notes = null,
            syncedAt = null
        )

        val names = resolveSmartTripNamesUseCase.resolve(trip, listOf(nursery, neutralLocation))
        assertEquals("BUSINESS", names.suggestedClassification)
        assertEquals("MySucculentCorner", names.suggestedBusinessEntity)
    }

    // Requirement 11: Conflicting classification preferences (leaves suggested null / unreviewed)
    @Test
    fun classificationSuggestion_conflictBetweenEndpoints_leavesSuggestionNull() {
        // Start is Nursery (BUSINESS), End is Home (PERSONAL) -> CONFLICT
        val trip = TripEntity(
            id = "trip-5",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.33820, // Nursery (BUSINESS)
            startLongitude = -121.88630,
            endLatitude = 37.34000, // Home (PERSONAL)
            endLongitude = -121.88000,
            totalDistanceMiles = 5.0,
            businessMiles = 0.0,
            businessEntity = "MySucculentCorner",
            purpose = "",
            trackingMethod = "AUTO_DETECTED",
            classification = "UNREVIEWED",
            applicableRate = 0.67,
            calculatedDeduction = 0.0,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            startAddress = "100 Blossom Hill Rd, San Jose, CA",
            endAddress = "500 Home Way, San Jose, CA",
            notes = null,
            syncedAt = null
        )

        val names = resolveSmartTripNamesUseCase.resolve(trip, listOf(nursery, home))
        // Conflicting preferences must NOT provide a silent suggestion
        assertNull(names.suggestedClassification)
        assertNull(names.suggestedBusinessEntity)
    }

    // Requirement 12: Preserving manually classified trips
    @Test
    fun manuallyClassifiedTrips_areNeverOverwritten() {
        val tripAlreadyReviewed = TripEntity(
            id = "trip-6",
            date = "2026-11-11",
            startTime = 1000L,
            endTime = 2000L,
            startLatitude = 37.33820,
            startLongitude = -121.88630,
            endLatitude = 37.34000,
            endLongitude = -121.88000,
            totalDistanceMiles = 5.0,
            businessMiles = 5.0,
            businessEntity = "MySucculentCorner",
            purpose = "Reviewed client delivery",
            trackingMethod = "AUTO_DETECTED",
            classification = "BUSINESS", // Already manually reviewed as BUSINESS
            applicableRate = 0.67,
            calculatedDeduction = 3.35,
            waypointsJson = "[]",
            syncStatus = "SYNCED",
            startAddress = "100 Blossom Hill Rd, San Jose, CA",
            endAddress = "500 Home Way, San Jose, CA",
            notes = null,
            syncedAt = 123456L
        )

        // Matching locations should resolve smart names for display, but classification remains unchanged
        val names = resolveSmartTripNamesUseCase.resolve(tripAlreadyReviewed, listOf(nursery, home))
        assertEquals("Succulent Nursery", names.startDisplayName)
        assertEquals("Home", names.endDisplayName)
        assertEquals("BUSINESS", tripAlreadyReviewed.classification)
        assertEquals(3.35, tripAlreadyReviewed.calculatedDeduction, 0.001)
    }
}
