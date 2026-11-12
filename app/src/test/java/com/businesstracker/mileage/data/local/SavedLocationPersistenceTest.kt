package com.businesstracker.mileage.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.businesstracker.mileage.data.local.entity.SavedLocationEntity
import com.businesstracker.mileage.data.repository.SavedLocationRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedLocationPersistenceTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var repository: SavedLocationRepositoryImpl

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = SavedLocationRepositoryImpl(database.savedLocationDao(), Dispatchers.IO)
    }

    @After fun tearDown() = database.close()

    @Test fun createEditDeletePreservesUnrelatedTripRows() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude,
                endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity,
                purpose, trackingMethod, classification, applicableRate, calculatedDeduction,
                waypointsJson, syncStatus)
            VALUES ('existing-trip', '2026-11-11', 1000, 2000, 37.33, -121.88,
                37.34, -121.89, 8.4, 0.0, 'MySucculentCorner', 'Nursery Pickup',
                'AUTO_DETECTED', 'UNREVIEWED', 0.67, 0.0, '[]', 'PENDING')
        """.trimIndent())
        val original = SavedLocationEntity(id = "home", name = "Home", latitude = 37.33,
            longitude = -121.88, address = "Original street", createdAt = 1000, updatedAt = 1000)
        repository.saveLocation(original)
        assertEquals(original, repository.getLocationById("home"))
        repository.saveLocation(original.copy(name = "New Home", updatedAt = 2000))
        assertEquals("New Home", repository.getLocationById("home")?.name)
        repository.deleteLocation("home")
        assertNull(repository.getLocationById("home"))
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT totalDistanceMiles, classification, syncStatus FROM trips WHERE id='existing-trip'")
        try {
            assertEquals(true, cursor.moveToFirst())
            assertEquals(8.4, cursor.getDouble(0), 0.0)
            assertEquals("UNREVIEWED", cursor.getString(1))
            assertEquals("PENDING", cursor.getString(2))
        } finally { cursor.close() }
    }
    @Test fun acceptingBusinessAndPersonalSuggestionsUsesNormalTripCalculations() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO trips (id, date, startTime, endTime, startLatitude, startLongitude,
                endLatitude, endLongitude, totalDistanceMiles, businessMiles, businessEntity,
                purpose, trackingMethod, classification, applicableRate, calculatedDeduction,
                waypointsJson, syncStatus)
            VALUES ('suggested-trip', '2026-11-11', 1000, 2000, 37.33, -121.88,
                37.34, -121.89, 10.0, 0.0, 'MySucculentCorner', 'Nursery Pickup',
                'AUTO_DETECTED', 'UNREVIEWED', 0.70, 0.0, '[]', 'PENDING')
        """.trimIndent())
        repository.saveLocation(SavedLocationEntity(id = "work", name = "Work",
            latitude = 37.33, longitude = -121.88, address = "Original street",
            classificationPreference = "BUSINESS", businessEntity = "MySucculentCorner"))
        var trip = database.tripDao().getTripById("suggested-trip")!!
        assertEquals("UNREVIEWED", trip.classification)
        assertEquals(0.0, trip.businessMiles, 0.0)
        assertEquals(0.0, trip.calculatedDeduction, 0.0)
        database.tripDao().updateTripDetails(trip.id, "BUSINESS", "MySucculentCorner", "Delivery", null)
        trip = database.tripDao().getTripById(trip.id)!!
        assertEquals("BUSINESS", trip.classification)
        assertEquals(10.0, trip.businessMiles, 0.0)
        assertEquals(7.0, trip.calculatedDeduction, 0.0001)
        database.tripDao().updateTripDetails(trip.id, "PERSONAL", "Personal", "Personal Drive", null)
        trip = database.tripDao().getTripById(trip.id)!!
        assertEquals("PERSONAL", trip.classification)
        assertEquals(0.0, trip.businessMiles, 0.0)
        assertEquals(0.0, trip.calculatedDeduction, 0.0)
    }

    @Test fun saveAndDeleteFailuresLeaveExistingLocationsAndTripsUntouched() = runBlocking {
        val original = SavedLocationEntity(id = "home", name = "Home", latitude = 37.33,
            longitude = -121.88, address = "Original street")
        repository.saveLocation(original)
        val failingDao = object : SavedLocationDao by database.savedLocationDao() {
            override suspend fun updateLocation(location: SavedLocationEntity) {
                throw IllegalStateException("write failed")
            }
            override suspend fun deleteLocationById(id: String) {
                throw IllegalStateException("delete failed")
            }
        }
        val failingRepository = SavedLocationRepositoryImpl(failingDao, Dispatchers.IO)
        var saveFailed = false
        try { failingRepository.saveLocation(original.copy(name = "Changed")) }
        catch (_: IllegalStateException) { saveFailed = true }
        assertEquals(true, saveFailed)
        assertEquals(original, repository.getLocationById("home"))
        var deleteFailed = false
        try { failingRepository.deleteLocation("home") }
        catch (_: IllegalStateException) { deleteFailed = true }
        assertEquals(true, deleteFailed)
        assertEquals(original, repository.getLocationById("home"))
        repository.saveLocation(original.copy(name = "Retry succeeded"))
        assertEquals("Retry succeeded", repository.getLocationById("home")?.name)
    }

}
