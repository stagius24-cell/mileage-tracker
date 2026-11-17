package com.businesstracker.mileage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.businesstracker.mileage.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE, id")
    fun observeVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE, id")
    suspend fun getVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicle(id: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE UPPER(bluetoothAddress) = UPPER(:address) LIMIT 1")
    suspend fun getVehicleForBluetoothAddress(address: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vehicle: VehicleEntity)

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM vehicles WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
