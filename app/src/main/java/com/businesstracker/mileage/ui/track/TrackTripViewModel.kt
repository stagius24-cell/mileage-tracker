package com.businesstracker.mileage.ui.track

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.businesstracker.mileage.domain.model.TrackingMethod
import com.businesstracker.mileage.service.LocationTrackingService
import com.businesstracker.mileage.service.TrackingServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TrackTripViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    val serviceState: StateFlow<TrackingServiceState> = LocationTrackingService.serviceState

    fun startManualTracking(suggestedBusiness: String = "MySucculentCorner") {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRIP_ID, UUID.randomUUID().toString())
            putExtra(LocationTrackingService.EXTRA_TRACKING_METHOD, TrackingMethod.MANUAL_GPS.value)
            putExtra(LocationTrackingService.EXTRA_SUGGESTED_BUSINESS, suggestedBusiness)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseTracking() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTracking() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun endTracking() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
