package com.businesstracker.mileage.data.device

import android.content.Context
import android.provider.Settings
import com.businesstracker.mileage.domain.service.BootCountProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBootCountProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BootCountProvider {

    override fun getBootCount(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 1)
        } catch (_: Exception) {
            1
        }
    }
}
