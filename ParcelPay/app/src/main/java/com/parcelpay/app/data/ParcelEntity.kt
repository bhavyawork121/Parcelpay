package com.parcelpay.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parcels")
data class ParcelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val photoPath: String,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
