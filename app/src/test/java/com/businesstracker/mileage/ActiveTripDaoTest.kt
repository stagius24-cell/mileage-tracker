package com.businesstracker.mileage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.entity.ActiveTripEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ActiveTripDaoTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var activeTripDao: ActiveTripDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeTripDao = database.activeTripDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun twoConcurrentActiveTripCreationRequests_resultInOneActiveTrip() = runBlocking {
        val trip1 = ActiveTripEntity(
            id = "trip-first-111",
            startTime = 1000L,
            startLatitude = 37.3382,
            startLongitude = -121.8863,
            lastLatitude = 37.3382,
            lastLongitude = -121.8863,
            lastTimestamp = 1000L,
            totalDistanceMeters = 0.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )

        val trip2 = ActiveTripEntity(
            id = "trip-second-222",
            startTime = 1005L,
            startLatitude = 37.3385,
            startLongitude = -121.8865,
            lastLatitude = 37.3385,
            lastLongitude = -121.8865,
            lastTimestamp = 1005L,
            totalDistanceMeters = 0.0,
            trackingMethod = "AUTO_DETECTED",
            suggestedBusiness = "MySucculentCorner"
        )

        // Execute concurrent requests to getOrCreateActiveTrip
        val deferred1 = async { activeTripDao.getOrCreateActiveTrip(trip1) }
        val deferred2 = async { activeTripDao.getOrCreateActiveTrip(trip2) }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        // Both concurrent requests must resolve to the identical active trip
        assertEquals("Concurrent requests must return the identical active trip", result1.id, result2.id)

        // Only one active trip exists in the Room database
        val activeInDb = activeTripDao.getActiveTrip()
        assertNotNull("An active trip must exist in DB", activeInDb)
        assertEquals("DB must contain the first active trip committed", result1.id, activeInDb?.id)
    }
}
