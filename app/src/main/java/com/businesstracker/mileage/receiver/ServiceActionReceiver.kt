package com.businesstracker.mileage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.NotificationHelper

class ServiceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val serviceIntent = Intent(context, LocationTrackingService::class.java)

        when (action) {
            NotificationHelper.ACTION_PAUSE -> {
                serviceIntent.action = LocationTrackingService.ACTION_PAUSE
                context.startService(serviceIntent)
            }
            NotificationHelper.ACTION_RESUME -> {
                serviceIntent.action = LocationTrackingService.ACTION_RESUME
                context.startService(serviceIntent)
            }
            NotificationHelper.ACTION_STOP -> {
                serviceIntent.action = LocationTrackingService.ACTION_STOP
                context.startService(serviceIntent)
            }
        }
    }
}
