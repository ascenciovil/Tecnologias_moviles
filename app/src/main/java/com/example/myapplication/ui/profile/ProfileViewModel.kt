package com.example.myapplication.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Datos del usuario actual
    private val _nombre = MutableLiveData<String>().apply {
        value = "Cargando..."
    }
    val nombre: LiveData<String> = _nombre

    private val _username = MutableLiveData<String>().apply {
        value = "@cargando"
    }
    val username: LiveData<String> = _username

    private val _email = MutableLiveData<String>().apply {
        value = "cargando@email.com"
    }
    val email: LiveData<String> = _email

    private val _descripcion = MutableLiveData<String>().apply {
        value = "Esta es tu descripción personal. Puedes editarla para contar más sobre ti."
    }
    val descripcion: LiveData<String> = _descripcion

    private val _rutasPublicadas = MutableLiveData<List<Ruta>>().apply {
        value = emptyList()
    }
    val rutasPublicadas: LiveData<List<Ruta>> = _rutasPublicadas

    private val _isLoading = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isLoading: LiveData<Boolean> = _isLoading

    // Cargar datos del usuario desde Firestore
    fun cargarDatosUsuario() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _isLoading.value = true

            db.collection("Usuarios").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Obtener datos de Firestore
                        val nombreUsuario = document.getString("nombre_usuario") ?: "Usuario"
                        val correo = document.getString("Correo") ?: currentUser.email ?: ""
                        val descripcionUsuario = document.getString("descripcion") ?: "Esta es tu descripción personal. Puedes editarla para contar más sobre ti."

                        // Actualizar LiveData
                        _nombre.value = nombreUsuario
                        _username.value = "@${nombreUsuario.lowercase(Locale.ROOT).replace(" ", "")}"
                        _email.value = correo
                        _descripcion.value = descripcionUsuario

                        // Cargar rutas reales del usuario
                        cargarRutasUsuario(currentUser.uid)
                    } else {
                        // Si no existe el documento, crear uno básico
                        crearUsuarioEnFirestore(currentUser.uid, currentUser.email ?: "")
                    }
                }
                .addOnFailureListener { exception ->
                    _isLoading.value = false
                }
        } else {
            _isLoading.value = false
        }
    }

    // Cargar rutas reales del usuario desde Firestore
    private fun cargarRutasUsuario(userId: String) {
        db.collection("Rutas")
            .whereEqualTo("user_id", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val rutas = mutableListOf<Ruta>()

                for (document in querySnapshot.documents) {
                    val titulo = document.getString("titulo") ?: "Ruta sin título"
                    val descripcion = document.getString("descripcion") ?: ""
                    val duracion = document.getString("duracion") ?: "Tiempo no especificado"

                    rutas.add(Ruta(titulo, descripcion, duracion))
                }

                _rutasPublicadas.value = rutas
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                // Si hay error o no hay rutas, mostrar lista vacía
                _rutasPublicadas.value = emptyList()
                _isLoading.value = false
            }
    }

    private fun crearUsuarioEnFirestore(userId: String, email: String) {
        val usuarioData = hashMapOf(
            "user_id" to userId,
            "Correo" to email,
            "nombre_usuario" to "Nuevo Usuario",
            "descripcion" to "Esta es tu descripción personal. Puedes editarla para contar más sobre ti.",
            "foto_perf" to "",
            "seguidos" to emptyList<String>(),
            "rutas_creadas" to emptyList<String>()
        )

        db.collection("Usuarios").document(userId)
            .set(usuarioData)
            .addOnSuccessListener {
                // Recargar datos después de crear el usuario
                cargarDatosUsuario()
            }
            .addOnFailureListener {
                _isLoading.value = false
            }
    }

    // Función para actualizar el perfil
    fun actualizarPerfil(nuevoNombre: String, nuevaDescripcion: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val updates = hashMapOf<String, Any>(
                "nombre_usuario" to nuevoNombre,
                "descripcion" to nuevaDescripcion
            )

            db.collection("Usuarios").document(currentUser.uid)
                .update(updates)
                .addOnSuccessListener {
                    // Actualizar LiveData localmente
                    _nombre.value = nuevoNombre
                    _username.value = "@${nuevoNombre.lowercase(Locale.ROOT).replace(" ", "")}"
                    _descripcion.value = nuevaDescripcion
                }
                .addOnFailureListener { exception ->
                    // Manejar error de actualización
                }
        }
    }

}

data class Ruta(
    val titulo: String,
    val descripcion: String,
    val duracion: String
)