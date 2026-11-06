package com.businesstracker.mileage.domain.service

/**
 * Abstraction for querying the system boot cycle identifier.
 * Enables deterministic testing of device reboot scenarios without requiring physical hardware reboots.
 */
interface BootCountProvider {
    /**
     * Returns an integer that monotonically increases with each device reboot.
     * On Android devices, typically backed by Settings.Global.BOOT_COUNT.
     * Returns a positive integer (e.g. >= 1) when available, or 0 / fallback if unavailable.
     */
    fun getBootCount(): Int
}
