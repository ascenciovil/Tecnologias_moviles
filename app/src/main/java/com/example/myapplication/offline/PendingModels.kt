package com.example.myapplication.offline

data class PendingCoord(val latitude: Double, val longitude: Double)

data class PendingPhoto(
    val path: String,
    val lat: Double?,
    val lng: Double?,
    val origin: String // "ruta" o "despues"
)
