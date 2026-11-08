package com.businesstracker.mileage.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class TripDebounceScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun scheduleForBluetoothDisconnect(tripId: String) {
        TripDebounceWorker.scheduleBluetoothDebounce(context, tripId)
    }

    open fun cancel() {
        TripDebounceWorker.cancelDebounce(context)
    }
}
