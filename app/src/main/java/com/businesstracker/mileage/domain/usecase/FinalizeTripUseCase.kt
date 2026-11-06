package com.businesstracker.mileage.domain.usecase

import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.coordinator.TripFinalizationCoordinator
import com.businesstracker.mileage.domain.model.TrackingMethod
import javax.inject.Inject

/**
 * UseCase for trip finalization. Delegates to the single-owner TripFinalizationCoordinator
 * to ensure atomic, idempotent finalization across service and debounce worker.
 */
class FinalizeTripUseCase @Inject constructor(
    private val coordinator: TripFinalizationCoordinator
) {
    suspend fun execute(
        tripId: String,
        trackingMethod: TrackingMethod = TrackingMethod.AUTO_DETECTED
    ): TripEntity? {
        return coordinator.finalizeTrip(tripId, trackingMethod)
    }

    suspend fun discardFalseTrip(tripId: String) {
        coordinator.discardFalseTrip(tripId)
    }
}
