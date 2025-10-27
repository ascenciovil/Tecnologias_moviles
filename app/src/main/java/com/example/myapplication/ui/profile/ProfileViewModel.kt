package com.example.myapplication.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {

    // Datos del usuario actual
    private val _nombre = MutableLiveData<String>().apply {
        value = "Juan Pérez"
    }
    val nombre: LiveData<String> = _nombre

    private val _username = MutableLiveData<String>().apply {
        value = "@juanperez"
    }
    val username: LiveData<String> = _username

    private val _email = MutableLiveData<String>().apply {
        value = "juan.perez@email.com"
    }
    val email: LiveData<String> = _email

    private val _telefono = MutableLiveData<String>().apply {
        value = "+56 9 1234 5678"
    }
    val telefono: LiveData<String> = _telefono

    private val _descripcion = MutableLiveData<String>().apply {
        value = "Amante de la naturaleza y los deportes al aire libre. Me encanta explorar nuevos senderos y compartir mis experiencias con la comunidad."
    }
    val descripcion: LiveData<String> = _descripcion

    private val _rutasPublicadas = MutableLiveData<List<Ruta>>().apply {
        value = listOf(
            Ruta("Cerro San Cristóbal", "Una caminata clásica con vistas increíbles de Santiago", "2 horas"),
            Ruta("Quebrada de Macul", "Sendero desafiante con cascadas y naturaleza", "3 horas"),
            Ruta("Parque Metropolitano", "Ruta familiar perfecta para un día de picnic", "1 hora")
        )
    }
    val rutasPublicadas: LiveData<List<Ruta>> = _rutasPublicadas

    // Funciones para el perfil propio
    fun editarPerfil() {
        // Aquí puedes implementar la lógica para editar el perfil
        // Por ahora solo es un placeholder
    }

    fun irConfiguracion() {
        // Aquí puedes implementar la navegación a configuración
        // Por ahora solo es un placeholder
    }

    fun actualizarPerfil(nuevoNombre: String, nuevaDescripcion: String) {
        _nombre.value = nuevoNombre
        _descripcion.value = nuevaDescripcion
    }
}

data class Ruta(
    val titulo: String,
    val descripcion: String,
    val duracion: String
)