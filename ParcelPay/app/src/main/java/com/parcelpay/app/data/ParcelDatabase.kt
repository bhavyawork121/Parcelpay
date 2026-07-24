package com.parcelpay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ParcelEntity::class], version = 1, exportSchema = false)
abstract class ParcelDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao

    companion object {
        @Volatile
        private var Instance: ParcelDatabase? = null

        fun getDatabase(context: Context): ParcelDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    ParcelDatabase::class.java,
                    "parcel_database"
                )
                .build()
                .also { Instance = it }
            }
        }
    }
}
