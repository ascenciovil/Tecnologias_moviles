package com.example.myapplication

import java.io.Serializable

data class FotoConCoordenada(
    val uri: String,
    val lat: Double? = null,
    val lng: Double? = null
) : Serializable
