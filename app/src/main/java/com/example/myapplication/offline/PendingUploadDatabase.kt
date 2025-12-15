package com.example.myapplication.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingRouteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PendingUploadDatabase : RoomDatabase() {

    abstract fun dao(): PendingRouteDao

    companion object {
        @Volatile private var INSTANCE: PendingUploadDatabase? = null

        fun getInstance(context: Context): PendingUploadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PendingUploadDatabase::class.java,
                    "pending_uploads.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
