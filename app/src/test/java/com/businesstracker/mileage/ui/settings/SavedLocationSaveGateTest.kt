package com.businesstracker.mileage.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedLocationSaveGateTest {
    @Test fun duplicateTapIsRejectedUntilPersistenceCompletes() {
        val gate = SavedLocationSaveGate()
        assertTrue(gate.tryBegin())
        assertFalse(gate.tryBegin())
        gate.end()
        assertTrue(gate.tryBegin())
    }

    @Test fun failedSaveCanBeRetriedAfterCleanup() {
        val gate = SavedLocationSaveGate()
        assertTrue(gate.tryBegin())
        try {
            throw IllegalStateException("database unavailable")
        } catch (_: IllegalStateException) {
            gate.end()
        }
        assertTrue(gate.tryBegin())
    }
}
