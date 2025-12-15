package com.example.myapplication.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingRouteDao {

    @Insert
    suspend fun insert(route: PendingRouteEntity): Long

    @Query("SELECT * FROM pending_routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingRouteEntity?

    @Query("SELECT * FROM pending_routes WHERE status IN ('PENDING','FAILED')")
    suspend fun getAllPendingOrFailed(): List<PendingRouteEntity>

    @Query("UPDATE pending_routes SET status = :status, lastError = :lastError WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, lastError: String? = null)

    @Query("DELETE FROM pending_routes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
