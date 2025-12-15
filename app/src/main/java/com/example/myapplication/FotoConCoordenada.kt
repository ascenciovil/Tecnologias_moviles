package com.example.myapplication

import java.io.Serializable

data class FotoConCoordenada(
    val uri: String,
    val lat: Double?,
    val lng: Double?,
    val origen: String? = null
) : Serializable
