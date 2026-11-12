package com.businesstracker.mileage.ui.settings

import java.util.concurrent.atomic.AtomicBoolean

/** Keeps a second Save tap from scheduling a second write while the first is pending. */
internal class SavedLocationSaveGate {
    private val saving = AtomicBoolean(false)

    fun tryBegin(): Boolean = saving.compareAndSet(false, true)

    fun end() { saving.set(false) }
}
