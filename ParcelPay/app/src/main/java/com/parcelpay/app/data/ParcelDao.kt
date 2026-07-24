package com.parcelpay.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Query("SELECT * FROM parcels ORDER BY timestamp DESC")
    fun getAllParcels(): Flow<List<ParcelEntity>>

    @Insert
    suspend fun insertParcel(parcel: ParcelEntity)
}
