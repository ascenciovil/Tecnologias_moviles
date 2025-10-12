package com.example.myapplication.data

data class Route(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val etaMinutes: Int,
    val description: String
)

object FakeRoute {
    fun getRoute(id: String) = Route(
        id = id,
        name = "NombreDeRuta",
        distanceKm = 5.0,
        etaMinutes = 20,
        description = "blahblahblah"
    )
}
