package com.example.myapplication.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class DashboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userId: String? = null
    private var listenerRegistration: ListenerRegistration? = null

    // Variables para almacenar datos del usuario
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

    private val _estaSiguiendo = MutableLiveData<Boolean>().apply {
        value = false
    }
    val estaSiguiendo: LiveData<Boolean> = _estaSiguiendo

    private val _textoBoton = MutableLiveData<String>().apply {
        value = "Seguir"
    }
    val textoBoton: LiveData<String> = _textoBoton

    private val _descripcion = MutableLiveData<String>().apply {
        value = "Cargando descripción..."
    }
    val descripcion: LiveData<String> = _descripcion

    private val _rutasPublicadas = MutableLiveData<List<Ruta>>().apply {
        value = emptyList()
    }
    val rutasPublicadas: LiveData<List<Ruta>> = _rutasPublicadas

    private val _logros = MutableLiveData<List<Logro>>().apply {
        value = emptyList()
    }
    val logros: LiveData<List<Logro>> = _logros

    private val _isLoading = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isOnline = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isOnline: LiveData<Boolean> = _isOnline

    private val _showOfflineMessage = MutableLiveData<Boolean>().apply {
        value = false
    }
    val showOfflineMessage: LiveData<Boolean> = _showOfflineMessage

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Inicializar ViewModel con un usuario específico
    fun init(userId: String) {
        this.userId = userId
        cargarDatosUsuario(userId)
        cargarRutasUsuario(userId)
        cargarLogrosUsuario(userId)
        setupRealtimeListener(userId)
    }

    // Cargar datos del usuario desde Firestore
    private fun cargarDatosUsuario(userId: String) {
        _isLoading.value = true
        _error.value = null
        _isOnline.value = true

        db.collection("Usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    procesarDatosUsuario(document)
                    _isOnline.value = true
                    _showOfflineMessage.value = false
                } else {
                    _error.value = "Usuario no encontrado"
                    _nombre.value = "Usuario no disponible"
                    _username.value = "@usuario_desconocido"
                    _descripcion.value = "Este usuario no tiene información disponible"
                    _email.value = "No disponible"
                }
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                _error.value = "Error al cargar datos: ${exception.message}"
                _nombre.value = "Error de conexión"
                _username.value = "@error"
                _descripcion.value = "No se pudieron cargar los datos del usuario"
                _email.value = "No disponible"
                _isOnline.value = false
                _showOfflineMessage.value = true
                _isLoading.value = false
            }
    }

    private fun procesarDatosUsuario(document: com.google.firebase.firestore.DocumentSnapshot) {
        val nombreUsuario = document.getString("nombre_usuario") ?: "Usuario"
        val correo = document.getString("Correo") ?: "Sin correo"
        val descripcionUsuario = document.getString("descripcion") ?:
        "Este usuario no ha agregado una descripción todavía."

        _nombre.value = nombreUsuario
        _username.value = "@${nombreUsuario.lowercase(Locale.ROOT).replace(" ", "")}"
        _email.value = correo
        _descripcion.value = descripcionUsuario
    }

    // Cargar rutas del usuario desde Firestore
    private fun cargarRutasUsuario(userId: String) {
        db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val rutas = mutableListOf<Ruta>()

                for (document in querySnapshot.documents) {
                    val titulo = document.getString("nombre")
                        ?: document.getString("titulo")
                        ?: "Ruta sin título"

                    val descripcion = document.getString("descripcion") ?: ""

                    val duracion = document.get("duracion")?.toString()
                        ?: document.get("tiempo_estimado")?.toString()
                        ?: calcularDuracionEstimada(document)
                        ?: "Tiempo no especificado"

                    rutas.add(Ruta(titulo, descripcion, duracion))
                }

                _rutasPublicadas.value = rutas
            }
            .addOnFailureListener { exception ->
                _rutasPublicadas.value = emptyList()
            }
    }

    // Cargar logros del usuario
    private fun cargarLogrosUsuario(userId: String) {
        db.collection("Usuarios").document(userId)
            .collection("Logros")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val logrosList = mutableListOf<Logro>()

                for (document in querySnapshot.documents) {
                    val id = document.id
                    val nombre = document.getString("nombre") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val icono = document.getString("icono") ?: ""
                    val obtenido = document.getBoolean("obtenido") ?: false
                    val fechaObtencion = document.getLong("fechaObtencion") ?: 0L

                    logrosList.add(Logro(id, nombre, descripcion, icono, obtenido, fechaObtencion))
                }

                _logros.value = logrosList
            }
            .addOnFailureListener { exception ->
                _logros.value = emptyList()
            }
    }

    private fun calcularDuracionEstimada(document: com.google.firebase.firestore.DocumentSnapshot): String? {
        val coordenadas = document.get("coordenadas") as? List<*>
        if (coordenadas != null && coordenadas.size >= 2) {
            val distanciaKm = coordenadas.size * 0.1
            val minutos = (distanciaKm * 15).toInt()

            return when {
                minutos < 60 -> "${minutos} min"
                else -> "${minutos / 60}h ${minutos % 60}min"
            }
        }
        return null
    }

    // Configurar listener en tiempo real
    private fun setupRealtimeListener(userId: String) {
        listenerRegistration?.remove()

        listenerRegistration = db.collection("Usuarios").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    procesarDatosUsuario(snapshot)
                }
            }
    }

    // Cargar datos de seguimiento
    fun cargarEstadoSeguimiento(currentUserId: String, targetUserId: String) {
        if (currentUserId == targetUserId) {
            _estaSiguiendo.value = false
            _textoBoton.value = "Mi perfil"
            return
        }

        db.collection("Usuarios").document(currentUserId)
            .collection("siguiendo").document(targetUserId)
            .get()
            .addOnSuccessListener { document ->
                _estaSiguiendo.value = document.exists()
                actualizarTextoBoton()
            }
            .addOnFailureListener {
                _estaSiguiendo.value = false
                actualizarTextoBoton()
            }
    }

    private fun actualizarTextoBoton() {
        _textoBoton.value = if (_estaSiguiendo.value == true) "Siguiendo" else "Seguir"
    }

    // Función para alternar el estado de seguir
    fun alternarSeguir(currentUserId: String) {
        val targetUserId = userId ?: return

        if (currentUserId == targetUserId) {
            return
        }

        val estaSiguiendoActual = _estaSiguiendo.value ?: false

        if (estaSiguiendoActual) {
            // Dejar de seguir
            db.collection("Usuarios").document(currentUserId)
                .collection("siguiendo").document(targetUserId)
                .delete()
                .addOnSuccessListener {
                    _estaSiguiendo.value = false
                    actualizarTextoBoton()
                }
                .addOnFailureListener {
                    _error.value = "Error al dejar de seguir"
                }
        } else {
            // Seguir
            val followData = mapOf<String, Any>(
                "userId" to targetUserId,
                "timestamp" to System.currentTimeMillis(),
                "nombre" to (_nombre.value ?: "")
            )

            db.collection("Usuarios").document(currentUserId)
                .collection("siguiendo").document(targetUserId)
                .set(followData)
                .addOnSuccessListener {
                    _estaSiguiendo.value = true
                    actualizarTextoBoton()
                }
                .addOnFailureListener {
                    _error.value = "Error al seguir usuario"
                }
        }
    }

    // Función para forzar sincronización
    fun sincronizarDatos() {
        val targetUserId = userId ?: return
        _showOfflineMessage.value = false
        _isOnline.value = true
        cargarDatosUsuario(targetUserId)
        cargarRutasUsuario(targetUserId)
        cargarLogrosUsuario(targetUserId)
    }

    // Cargar datos por defecto (para cuando no hay userId)
    fun cargarDatosPorDefecto() {
        _nombre.value = "Usuario Ejemplo"
        _username.value = "@usuario_ejemplo"
        _email.value = "ejemplo@email.com"
        _descripcion.value = "Este es un perfil de ejemplo. Haz clic en un autor de ruta para ver su perfil real."
        _rutasPublicadas.value = emptyList()
        _logros.value = emptyList()
        _isLoading.value = false
        _isOnline.value = true
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}

data class Ruta(
    val titulo: String,
    val descripcion: String,
    val duracion: String
)

data class Logro(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val icono: String,
    val obtenido: Boolean,
    val fechaObtencion: Long
)