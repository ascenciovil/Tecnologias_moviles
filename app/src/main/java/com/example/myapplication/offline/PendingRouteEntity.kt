package com.example.myapplication.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_routes")
data class PendingRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val nombre: String,
    val descripcion: String,
    val coordenadasJson: String,
    val fotosJson: String,
    val status: String = "PENDING", // PENDING, UPLOADING, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val lastError: String? = null
)
