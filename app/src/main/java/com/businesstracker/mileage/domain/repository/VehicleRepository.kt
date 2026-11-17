package com.businesstracker.mileage.domain.repository

import com.businesstracker.mileage.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeVehicles(): Flow<List<VehicleEntity>>
    suspend fun getVehicles(): List<VehicleEntity>
    suspend fun getVehicle(id: String): VehicleEntity?
    suspend fun getVehicleForBluetoothAddress(address: String): VehicleEntity?
    suspend fun save(vehicle: VehicleEntity)
}
