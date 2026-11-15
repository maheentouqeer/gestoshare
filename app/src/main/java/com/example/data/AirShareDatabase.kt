package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransferRecordEntity::class,
        DiagnosticEventEntity::class,
        DiagnosticLogEntity::class,
        RecentDeviceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AirShareDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        @Volatile
        private var INSTANCE: AirShareDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnostic_events` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `code` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `technicalMessage` TEXT NOT NULL,
                        `stackTrace` TEXT NOT NULL,
                        `sourceComponent` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `deviceInfoJson` TEXT NOT NULL,
                        `stateSnapshotJson` TEXT NOT NULL,
                        `relatedTransferId` TEXT,
                        `relatedEndpointId` TEXT,
                        `suggestedAction` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `appVersion` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnostic_logs` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `timestamp` INTEGER NOT NULL,
                        `level` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `tag` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `metadata` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_devices` (
                        `deviceKey` TEXT NOT NULL PRIMARY KEY,
                        `displayName` TEXT NOT NULL,
                        `lastEndpointId` TEXT NOT NULL,
                        `lastConnectedAt` INTEGER NOT NULL,
                        `connectionCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AirShareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AirShareDatabase::class.java,
                    "airshare_history.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
