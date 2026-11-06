package com.businesstracker.mileage.data.device

import android.content.Context
import android.provider.Settings
import com.businesstracker.mileage.domain.service.BootCountProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android system provider for the device boot-session count (Settings.Global.BOOT_COUNT).
 * Returns a positive integer on supported devices running Android 7.0+.
 * Returns 0 if the setting is absent, unreadable, or when an exception occurs.
 *
 * Limitation note:
 * On environments or custom ROMs where Settings.Global.BOOT_COUNT is unavailable (bootCount == 0),
 * reboot detection falls back to monotonic elapsedRealtimeNanos reset (new < old).
 * If a device reboots and the new uptime already exceeds the old uptime in such an environment,
 * cross-boot transition cannot be detected via hardware counters alone; physical speed limits
 * (MAX_SPEED_MPH) and stationary displacement thresholds continue to protect against false mileage accumulation.
 */
@Singleton
class AndroidBootCountProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BootCountProvider {

    override fun getBootCount(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        } catch (_: Exception) {
            0
        }
    }
}
