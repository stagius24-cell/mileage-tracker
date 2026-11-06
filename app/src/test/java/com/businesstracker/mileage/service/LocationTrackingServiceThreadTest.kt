package com.businesstracker.mileage.service

import android.os.HandlerThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationTrackingServiceThreadTest {

    @Test
    fun handlerThread_startsAndProvidesLooperSafely() {
        val thread = HandlerThread("TestGpsLocationHandlerThread").apply { start() }
        assertTrue("Thread must be alive after starting", thread.isAlive)
        assertNotNull("Looper must not be null after thread start", thread.looper)

        // Clean shutdown
        thread.quitSafely()
        thread.join(1000)
        assertFalse("Thread must not be alive after quitSafely()", thread.isAlive)
    }

    @Test
    fun handlerThread_multipleCycles_doNotLeakThreads() {
        val threads = mutableListOf<HandlerThread>()
        for (i in 1..5) {
            val t = HandlerThread("GpsThread-$i").apply { start() }
            assertTrue(t.isAlive)
            threads.add(t)
            t.quitSafely()
            t.join(500)
            assertFalse(t.isAlive)
        }
        assertTrue("All threads cleanly stopped", threads.none { it.isAlive })
    }
}
