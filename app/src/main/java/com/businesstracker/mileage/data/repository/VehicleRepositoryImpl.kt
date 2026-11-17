package com.businesstracker.mileage.data.repository

import com.businesstracker.mileage.data.local.VehicleDao
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import com.businesstracker.mileage.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao
) : VehicleRepository {
    override fun observeVehicles(): Flow<List<VehicleEntity>> = vehicleDao.observeVehicles()
    override suspend fun getVehicles(): List<VehicleEntity> = vehicleDao.getVehicles()
    override suspend fun getVehicle(id: String): VehicleEntity? = vehicleDao.getVehicle(id)
    override suspend fun getVehicleForBluetoothAddress(address: String): VehicleEntity? =
        vehicleDao.getVehicleForBluetoothAddress(address.trim())
    override suspend fun save(vehicle: VehicleEntity) {
        if (vehicleDao.exists(vehicle.id)) vehicleDao.update(vehicle) else vehicleDao.insert(vehicle)
    }
}
