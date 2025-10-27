package com.example.myapplication.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _nombre = MutableLiveData<String>().apply {
        value = "Pablo"
    }
    val nombre: LiveData<String> = _nombre

    private val _username = MutableLiveData<String>().apply {
        value = "@XxPablo2002xX"
    }
    val username: LiveData<String> = _username

    private val _estaSiguiendo = MutableLiveData<Boolean>().apply {
        value = false // Inicialmente no está siguiendo
    }
    val estaSiguiendo: LiveData<Boolean> = _estaSiguiendo

    // Texto del botón basado en el estado
    val textoBoton: LiveData<String> = MutableLiveData<String>().apply {
        _estaSiguiendo.observeForever { siguiendo ->
            (this as MutableLiveData).value = if (siguiendo) "Siguiendo" else "Seguir"
        }
    }

    private val _descripcion = MutableLiveData<String>().apply {
        value = "Amante de la naturaleza y de descubrir nuevos caminos. Aquí comparto mis rutas favoritas para explorar desde nuestros últimos meses que los ganamos rodeados de paisajes naturales. Me gusta detallar cada recorrido con datos, fotos y consejos para que cualquiera pueda disfrutarlo."
    }
    val descripcion: LiveData<String> = _descripcion

    private val _caracteristicasRutas = MutableLiveData<List<String>>().apply {
        value = listOf(
            "Necesario y utilidad estimado",
            "Puntos de interés (miradores, plazas, rincones, monumentos, áreas verdes)",
            "Recomendaciones personales (mejor hora del día, qué llevar, lugares para descansar)",
            "Las encantas relativas generan y descubrir rutas nuevas compartiendo la comunidad"
        )
    }
    val caracteristicasRutas: LiveData<List<String>> = _caracteristicasRutas

    private val _rutasPublicadas = MutableLiveData<List<Ruta>>().apply {
        value = listOf(
            Ruta("Cerro Condell – Curicó", "Sendero urbano que conduce al mirador Cerro Condell. Perfecto para...", "23 min"),
            Ruta("Peor es nada", "Peor es nada", "23 min"),
            Ruta("Aurelia", "Silson", "25 min"),
            Ruta("aaaaaa", "Silson", "25 min"),
            Ruta("Mercedes", "descripcion", "25 min")
        )
    }
    val rutasPublicadas: LiveData<List<Ruta>> = _rutasPublicadas

    // Función para alternar el estado de seguir
    fun alternarSeguir() {
        _estaSiguiendo.value = !(_estaSiguiendo.value ?: false)
    }
}

data class Ruta(
    val titulo: String,
    val descripcion: String,
    val duracion: String
)