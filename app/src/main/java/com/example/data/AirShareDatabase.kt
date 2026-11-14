package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransferRecordEntity::class], version = 1, exportSchema = false)
abstract class AirShareDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var INSTANCE: AirShareDatabase? = null

        fun getDatabase(context: Context): AirShareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AirShareDatabase::class.java,
                    "airshare_history.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
