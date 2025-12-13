package com.example.myapplication

import java.io.Serializable

data class FotoConCoordenada(
    val uri: String,          // puede ser PATH local o URL (https)
    val lat: Double?,
    val lng: Double?,
    val origen: String? = null  // "ruta" | "despues" (nullable para compatibilidad)
) : Serializable
