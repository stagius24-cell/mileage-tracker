package com.businesstracker.mileage.domain.tracking

import com.businesstracker.mileage.data.local.entity.VehicleEntity

sealed interface VehicleResolutionResult {
    data class Matched(
        val vehicle: VehicleEntity,
        val source: String,
        val candidateCount: Int = 1
    ) : VehicleResolutionResult

    data class Ambiguous(
        val candidates: List<VehicleEntity>,
        val source: String
    ) : VehicleResolutionResult

    data object None : VehicleResolutionResult
}

/** Resolves only exact stable addresses. Device display names are deliberately never used as keys. */
object VehicleBluetoothMatchPolicy {
    fun matchingVehicles(vehicles: List<VehicleEntity>, connectedAddresses: Set<String>): List<VehicleEntity> {
        val connected = connectedAddresses.mapNotNull {
            it.trim().takeIf(String::isNotEmpty)?.let(::normalizeAddress)
        }.toSet()
        if (connected.isEmpty()) return emptyList()
        return vehicles.filter { vehicle ->
            vehicle.bluetoothAddress?.let(::normalizeAddress)?.let(connected::contains) == true
        }.distinctBy { it.id }
    }

    fun match(vehicles: List<VehicleEntity>, connectedAddresses: Set<String>): VehicleEntity? {
        return matchingVehicles(vehicles, connectedAddresses).singleOrNull()
    }

    /**
     * Two-tier vehicle attribution:
     * 1. Tier 1 (Active Cabin Audio / Hands-Free): A2DP / HEADSET connected devices take priority
     *    over background proximity/ACL connections when two parked vehicles are nearby.
     * 2. Tier 2 (ACL / GATT fallback): used only if no Tier 1 saved vehicle audio/headset profile is active.
     * If more than one saved vehicle appears connected in the resolving tier, returns Ambiguous
     * rather than guessing an arbitrary vehicle.
     */
    fun resolveTiered(
        vehicles: List<VehicleEntity>,
        activeProfileAddresses: Set<String>,
        fallbackAddresses: Set<String> = emptySet()
    ): VehicleResolutionResult {
        val tier1Matches = matchingVehicles(vehicles, activeProfileAddresses)
        when {
            tier1Matches.size == 1 -> return VehicleResolutionResult.Matched(
                vehicle = tier1Matches.single(),
                source = "ACTIVE_PROFILE_A2DP_HEADSET",
                candidateCount = 1
            )
            tier1Matches.size > 1 -> return VehicleResolutionResult.Ambiguous(
                candidates = tier1Matches,
                source = "ACTIVE_PROFILE_A2DP_HEADSET"
            )
        }

        val tier2Matches = matchingVehicles(vehicles, fallbackAddresses)
        return when {
            tier2Matches.size == 1 -> VehicleResolutionResult.Matched(
                vehicle = tier2Matches.single(),
                source = "ACL_OR_GATT",
                candidateCount = 1
            )
            tier2Matches.size > 1 -> VehicleResolutionResult.Ambiguous(
                candidates = tier2Matches,
                source = "ACL_OR_GATT"
            )
            else -> VehicleResolutionResult.None
        }
    }

    fun normalizeAddress(address: String): String = address.trim().uppercase()
}

/** Resolves the current friendly name by persisted trip vehicle ID, never by a name snapshot. */
object VehicleFriendlyNameResolver {
    const val UNKNOWN_VEHICLE = "Unknown vehicle"

    fun resolve(vehicleId: String?, vehicles: List<VehicleEntity>): String =
        vehicleId?.let { id -> vehicles.firstOrNull { it.id == id }?.name }
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: UNKNOWN_VEHICLE
}
