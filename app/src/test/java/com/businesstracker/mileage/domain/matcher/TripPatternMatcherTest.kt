package com.businesstracker.mileage.domain.matcher

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.RoutePatternDao
import com.businesstracker.mileage.data.local.entity.RoutePatternEntity
import com.businesstracker.mileage.data.local.entity.TripEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TripPatternMatcherTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var routePatternDao: RoutePatternDao
    private lateinit var matcher: TripPatternMatcher

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Base Reference Route: Home (San Jose) -> Nursery (Morgan Hill)
    // Distance approx 10.5 miles (16,900 meters)
    private val HOME_LAT = 37.3382
    private val HOME_LON = -121.8863
    private val NURSERY_LAT = 37.1950
    private val NURSERY_LON = -121.6500

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routePatternDao = database.routePatternDao()
        matcher = TripPatternMatcher(
            routePatternDao = routePatternDao,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSamplePattern(
        id: String = UUID.randomUUID().toString(),
        startLat: Double = HOME_LAT,
        startLon: Double = HOME_LON,
        endLat: Double = NURSERY_LAT,
        endLon: Double = NURSERY_LON,
        classification: String = "BUSINESS",
        businessEntity: String = "MySucculentCorner",
        purpose: String = "Nursery Pickup",
        sampleCount: Int = 5,
        confidence: Double = 0.9,
        lastLearnedAt: Long = 1794730000000L
    ): RoutePatternEntity {
        return RoutePatternEntity(
            id = id,
            startLatitude = startLat,
            startLongitude = startLon,
            startRadiusMeters = 200.0,
            endLatitude = endLat,
            endLongitude = endLon,
            endRadiusMeters = 200.0,
            startAddress = "Home",
            endAddress = "Nursery",
            classification = classification,
            businessEntity = businessEntity,
            purpose = purpose,
            sampleCount = sampleCount,
            confidence = confidence,
            lastLearnedAt = lastLearnedAt,
            createdAt = lastLearnedAt - 86400000L
        )
    }

    private fun createSampleTrip(
        id: String = "trip-test-1",
        startLat: Double = HOME_LAT,
        startLon: Double = HOME_LON,
        endLat: Double = NURSERY_LAT,
        endLon: Double = NURSERY_LON,
        totalDistanceMiles: Double = 16.0,
        startTime: Long = 1794730000000L
    ): TripEntity {
        return TripEntity(
            id = id,
            date = "2026-11-15",
            startTime = startTime,
            endTime = startTime + 1800000L,
            startAddress = "Home",
            endAddress = "Nursery",
            startLatitude = startLat,
            startLongitude = startLon,
            endLatitude = endLat,
            endLongitude = endLon,
            totalDistanceMiles = totalDistanceMiles,
            businessMiles = totalDistanceMiles,
            businessEntity = "MySucculentCorner",
            purpose = "Nursery Pickup",
            trackingMethod = "AUTO_DETECTED",
            classification = "UNREVIEWED",
            applicableRate = 0.67,
            calculatedDeduction = totalDistanceMiles * 0.67,
            notes = null,
            waypointsJson = "[]",
            syncStatus = "PENDING",
            syncedAt = null
        )
    }

    // 1. Identical routes
    @Test
    fun test01_identicalRoutes_produceReliableMatch() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        val trip = createSampleTrip()
        val result = matcher.matchTrip(trip)

        assertTrue("Identical route must return ReliableMatch", result is RouteMatchResult.ReliableMatch)
        val match = result as RouteMatchResult.ReliableMatch
        assertEquals(pattern.id, match.patternId)
        assertEquals(0.0, match.startDistanceMeters, 1.0)
        assertEquals(0.0, match.endDistanceMeters, 1.0)
        assertTrue("Distance should be consistent", match.isDistanceConsistent)
    }

    // 2. GPS variation within 200 meters
    @Test
    fun test02_gpsVariationWithin200Meters_producesReliableMatch() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // 0.001 degree latitude is approx 111 meters (well within 200m)
        val trip = createSampleTrip(
            startLat = HOME_LAT + 0.0009, // ~100m offset
            startLon = HOME_LON,
            endLat = NURSERY_LAT - 0.0011, // ~122m offset
            endLon = NURSERY_LON
        )
        val result = matcher.matchTrip(trip)

        assertTrue("GPS variation within 200m must match", result is RouteMatchResult.ReliableMatch)
        val match = result as RouteMatchResult.ReliableMatch
        assertEquals(pattern.id, match.patternId)
        assertTrue(match.startDistanceMeters <= 200.0)
        assertTrue(match.endDistanceMeters <= 200.0)
    }

    // 3. Routes outside matching radius (> 200 meters)
    @Test
    fun test03_routesOutsideMatchingRadius_returnNoMatch() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // 0.0025 degrees latitude is ~277 meters (> 200m)
        val tripStartTooFar = createSampleTrip(startLat = HOME_LAT + 0.0025)
        val result1 = matcher.matchTrip(tripStartTooFar)
        assertTrue("Route with start > 200m must return NoMatch", result1 is RouteMatchResult.NoMatch)

        val tripEndTooFar = createSampleTrip(endLat = NURSERY_LAT + 0.0025)
        val result2 = matcher.matchTrip(tripEndTooFar)
        assertTrue("Route with end > 200m must return NoMatch", result2 is RouteMatchResult.NoMatch)
    }

    // 4. Reversed routes (Home -> Nursery vs Nursery -> Home)
    @Test
    fun test04_reversedRoutes_returnNoMatch() = testScope.runTest {
        // Pattern: Home -> Nursery
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Trip: Nursery -> Home (return trip)
        val reversedTrip = createSampleTrip(
            startLat = NURSERY_LAT,
            startLon = NURSERY_LON,
            endLat = HOME_LAT,
            endLon = HOME_LON
        )
        val result = matcher.matchTrip(reversedTrip)

        assertTrue("Reversed route must return NoMatch", result is RouteMatchResult.NoMatch)
    }

    // 5. Same origin with different destinations
    @Test
    fun test05_sameOrigin_differentDestination_returnsNoMatch() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Same start (Home), but destination is San Francisco (37.7749, -122.4194)
        val diffDestTrip = createSampleTrip(
            endLat = 37.7749,
            endLon = -122.4194,
            totalDistanceMiles = 45.0
        )
        val result = matcher.matchTrip(diffDestTrip)

        assertTrue("Same origin with different destination must return NoMatch", result is RouteMatchResult.NoMatch)
    }

    // 6. Different origins with the same destination
    @Test
    fun test06_differentOrigin_sameDestination_returnsNoMatch() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Different origin (Oakland 37.8044, -122.2712), same destination (Nursery)
        val diffOriginTrip = createSampleTrip(
            startLat = 37.8044,
            startLon = -122.2712,
            totalDistanceMiles = 55.0
        )
        val result = matcher.matchTrip(diffOriginTrip)

        assertTrue("Different origin with same destination must return NoMatch", result is RouteMatchResult.NoMatch)
    }

    // 7. Similar mileage but different routes
    @Test
    fun test07_similarMileageDifferentRoutes_returnsNoMatch() = testScope.runTest {
        // Pattern is 10.5 miles from Home -> Nursery
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Completely different route with identical mileage (10.5 miles):
        // Palo Alto (37.4419, -122.1430) -> Fremont (37.5485, -121.9886)
        val differentRouteSameMileage = createSampleTrip(
            startLat = 37.4419,
            startLon = -122.1430,
            endLat = 37.5485,
            endLon = -121.9886,
            totalDistanceMiles = 10.5
        )
        val result = matcher.matchTrip(differentRouteSameMileage)

        assertTrue("Never match trips based on mileage alone", result is RouteMatchResult.NoMatch)
    }

    // 8. Nearby distinct businesses in the same shopping center
    @Test
    fun test08_nearbyDistinctBusinesses_detectsAmbiguityWhenClose() = testScope.runTest {
        // Two businesses in same commercial plaza ~60 meters apart:
        // Store A: Garden Supply (Business delivery)
        // Store B: Coffee Shop (Personal stop)
        val storeALat = NURSERY_LAT
        val storeALon = NURSERY_LON
        val storeBLat = NURSERY_LAT + 0.0005 // ~55m north
        val storeBLon = NURSERY_LON + 0.0003

        val patternA = createSamplePattern(
            id = "store-a",
            endLat = storeALat,
            endLon = storeALon,
            classification = "BUSINESS",
            businessEntity = "MySucculentCorner",
            purpose = "Wholesale Garden Supply"
        )
        val patternB = createSamplePattern(
            id = "store-b",
            endLat = storeBLat,
            endLon = storeBLon,
            classification = "PERSONAL",
            businessEntity = "Personal",
            purpose = "Coffee Break"
        )
        routePatternDao.insertRoutePattern(patternA)
        routePatternDao.insertRoutePattern(patternB)

        // Trip parks right between Store A and Store B
        val midpointLat = (storeALat + storeBLat) / 2.0
        val midpointLon = (storeALon + storeBLon) / 2.0
        val tripInPlaza = createSampleTrip(endLat = midpointLat, endLon = midpointLon)

        val result = matcher.matchTrip(tripInPlaza)

        assertTrue("Nearby distinct businesses with conflicting classifications must be AmbiguousMatch",
            result is RouteMatchResult.AmbiguousMatch
        )
        val ambiguous = result as RouteMatchResult.AmbiguousMatch
        assertEquals("Both nearby store patterns must be identified as candidates", 2, ambiguous.candidatePatterns.size)
        assertTrue(ambiguous.candidatePatterns.any { it.id == "store-a" })
        assertTrue(ambiguous.candidatePatterns.any { it.id == "store-b" })
        assertTrue(ambiguous.reason.contains("conflicting classification", ignoreCase = true))
    }

    // 9. Multiple ambiguous matches
    @Test
    fun test09_multipleAmbiguousMatches_returnsAmbiguousMatch() = testScope.runTest {
        // Two patterns overlapping the exact same route with conflicting classifications
        val patternBiz = createSamplePattern(id = "pat-biz", classification = "BUSINESS", businessEntity = "MySucculentCorner")
        val patternPersonal = createSamplePattern(id = "pat-personal", classification = "PERSONAL", businessEntity = "Personal")
        routePatternDao.insertRoutePattern(patternBiz)
        routePatternDao.insertRoutePattern(patternPersonal)

        val trip = createSampleTrip()
        val result = matcher.matchTrip(trip)

        assertTrue("Multiple conflicting patterns must return AmbiguousMatch", result is RouteMatchResult.AmbiguousMatch)
        val ambiguous = result as RouteMatchResult.AmbiguousMatch
        assertEquals(2, ambiguous.candidatePatterns.size)
    }

    // 10. Invalid coordinates (NaN, Infinite, 0.0)
    @Test
    fun test10_invalidCoordinates_returnInvalidCoordinates() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        val tripNaN = createSampleTrip(startLat = Double.NaN)
        assertTrue("NaN latitude must return InvalidCoordinates", matcher.matchTrip(tripNaN) is RouteMatchResult.InvalidCoordinates)

        val tripInf = createSampleTrip(endLon = Double.POSITIVE_INFINITY)
        assertTrue("Infinite longitude must return InvalidCoordinates", matcher.matchTrip(tripInf) is RouteMatchResult.InvalidCoordinates)

        val tripZero = createSampleTrip(startLat = 0.0, startLon = 0.0)
        assertTrue("0.0, 0.0 must return InvalidCoordinates", matcher.matchTrip(tripZero) is RouteMatchResult.InvalidCoordinates)
    }

    // 11. Very short trips (< 200m displacement)
    @Test
    fun test11_veryShortTrips_returnInvalidCoordinates() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Trip moved only ~48 meters (under app 200m validity threshold)
        val shortTrip = createSampleTrip(
            startLat = HOME_LAT,
            startLon = HOME_LON,
            endLat = HOME_LAT + 0.0004,
            endLon = HOME_LON,
            totalDistanceMiles = 0.03
        )
        val result = matcher.matchTrip(shortTrip)

        assertTrue("Trips under application 200m validity threshold must return InvalidCoordinates", result is RouteMatchResult.InvalidCoordinates)
    }

    // 12. Stable pattern anchors
    @Test
    fun test12_stablePatternAnchors_areNeverModifiedDuringMatching() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        val tripWithOffset = createSampleTrip(
            startLat = HOME_LAT + 0.001,
            startLon = HOME_LON - 0.001,
            endLat = NURSERY_LAT + 0.001,
            endLon = NURSERY_LON - 0.001
        )
        val result = matcher.matchTrip(tripWithOffset)
        assertTrue(result is RouteMatchResult.ReliableMatch)

        // Verify pattern stored in database has NOT changed its anchor coordinates
        val stored = routePatternDao.getPatternById(pattern.id)!!
        assertEquals("Start latitude must remain strictly unchanged", HOME_LAT, stored.startLatitude, 0.0000001)
        assertEquals("Start longitude must remain strictly unchanged", HOME_LON, stored.startLongitude, 0.0000001)
        assertEquals("End latitude must remain strictly unchanged", NURSERY_LAT, stored.endLatitude, 0.0000001)
        assertEquals("End longitude must remain strictly unchanged", NURSERY_LON, stored.endLongitude, 0.0000001)
    }

    // 13. Distance consistency (within ±25% vs extreme detour)
    @Test
    fun test13_distanceConsistency_detectsDetours() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        // Normal route: 16.0 miles total vs ~15.8 miles displacement (difference ~1.4%)
        val normalTrip = createSampleTrip(totalDistanceMiles = 16.0)
        val normalResult = matcher.matchTrip(normalTrip) as RouteMatchResult.ReliableMatch
        assertTrue("Normal trip must be distance consistent", normalResult.isDistanceConsistent)
        assertTrue(normalResult.distanceDifferencePercent <= 25.0)

        // Heavy detour route: 35.0 miles total for a ~15.8 mile displacement (> 100% diff)
        val detourTrip = createSampleTrip(totalDistanceMiles = 35.0)
        val detourResult = matcher.matchTrip(detourTrip) as RouteMatchResult.ReliableMatch
        assertFalse("Extreme detour must not be distance consistent", detourResult.isDistanceConsistent)
        assertTrue(detourResult.distanceDifferencePercent > 25.0)
    }

    // 14. Repeated matching produces deterministic results
    @Test
    fun test14_repeatedMatching_producesDeterministicResults() = testScope.runTest {
        val pattern = createSamplePattern()
        routePatternDao.insertRoutePattern(pattern)

        val trip = createSampleTrip(startLat = HOME_LAT + 0.0005, endLat = NURSERY_LAT + 0.0005)

        val firstResult = matcher.matchTrip(trip) as RouteMatchResult.ReliableMatch

        for (i in 1..100) {
            val repeatResult = matcher.matchTrip(trip) as RouteMatchResult.ReliableMatch
            assertEquals(firstResult.patternId, repeatResult.patternId)
            assertEquals(firstResult.startDistanceMeters, repeatResult.startDistanceMeters, 0.000001)
            assertEquals(firstResult.endDistanceMeters, repeatResult.endDistanceMeters, 0.000001)
            assertEquals(firstResult.distanceDifferencePercent, repeatResult.distanceDifferencePercent, 0.000001)
            assertEquals(firstResult.isDistanceConsistent, repeatResult.isDistanceConsistent)
            assertEquals(firstResult.geographicSimilarityScore, repeatResult.geographicSimilarityScore, 0.000001)
        }
    }

    // 15. Matching performance with a large set of patterns
    @Test
    fun test15_matchingPerformance_withLargePatternSet() = testScope.runTest {
        // Insert 150 diverse patterns across California
        val patternList = mutableListOf<RoutePatternEntity>()
        for (i in 0 until 150) {
            val latOffset = (i % 20) * 0.05
            val lonOffset = (i / 20) * 0.05
            patternList.add(
                createSamplePattern(
                    id = "pat-perf-$i",
                    startLat = 36.0 + latOffset,
                    startLon = -120.0 - lonOffset,
                    endLat = 36.2 + latOffset,
                    endLon = -120.2 - lonOffset
                )
            )
        }
        // And the exact matching pattern
        val targetPattern = createSamplePattern(id = "target-pat-perf")
        patternList.add(targetPattern)

        for (p in patternList) {
            routePatternDao.insertRoutePattern(p)
        }
        assertEquals(151, routePatternDao.getPatternsCount())

        val targetTrip = createSampleTrip()

        val startTimeNanos = System.nanoTime()
        val result = matcher.matchTrip(targetTrip)
        val elapsedMillis = (System.nanoTime() - startTimeNanos) / 1_000_000

        assertTrue("Must match target pattern out of 151 patterns", result is RouteMatchResult.ReliableMatch)
        assertEquals("target-pat-perf", (result as RouteMatchResult.ReliableMatch).patternId)
        assertTrue("Matching 150+ patterns should complete under 100ms (took ${elapsedMillis}ms)", elapsedMillis < 100)
    }
}
