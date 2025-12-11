package com.example.myapplication.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale
import android.util.Log

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

    // NUEVOS: Contadores de seguidores y siguiendo
    private val _seguidoresCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val seguidoresCount: LiveData<Int> = _seguidoresCount

    private val _siguiendoCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val siguiendoCount: LiveData<Int> = _siguiendoCount

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

                    // Inicializar contadores si no existen (solo la primera vez)
                    inicializarContadoresSiEsNecesario(document)

                    _isOnline.value = true
                    _showOfflineMessage.value = false
                } else {
                    _error.value = "Usuario no encontrado"
                    _nombre.value = "Usuario no disponible"
                    _username.value = "@usuario_desconocido"
                    _descripcion.value = "Este usuario no tiene información disponible"
                    _email.value = "No disponible"
                    _seguidoresCount.value = 0
                    _siguiendoCount.value = 0
                }
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                _error.value = "Error al cargar datos: ${exception.message}"
                _nombre.value = "Error de conexión"
                _username.value = "@error"
                _descripcion.value = "No se pudieron cargar los datos del usuario"
                _email.value = "No disponible"
                _seguidoresCount.value = 0
                _siguiendoCount.value = 0
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

        // Cargar contadores (si existen)
        _seguidoresCount.value = (document.get("seguidores_count") as? Long)?.toInt() ?: 0
        _siguiendoCount.value = (document.get("siguiendo_count") as? Long)?.toInt() ?: 0

        _nombre.value = nombreUsuario
        _username.value = "@${nombreUsuario.lowercase(Locale.ROOT).replace(" ", "")}"
        _email.value = correo
        _descripcion.value = descripcionUsuario
    }

    // Función para inicializar contadores si no existen
    private fun inicializarContadoresSiEsNecesario(document: com.google.firebase.firestore.DocumentSnapshot) {
        val userId = document.id

        // Verificar si los campos de contadores existen
        val tieneSeguidoresCount = document.contains("seguidores_count")
        val tieneSiguiendoCount = document.contains("siguiendo_count")

        if (!tieneSeguidoresCount || !tieneSiguiendoCount) {
            Log.d("DashboardViewModel", "⚠️ Inicializando contadores para usuario: $userId")

            val updates = hashMapOf<String, Any>()

            if (!tieneSeguidoresCount) {
                updates["seguidores_count"] = 0
            }

            if (!tieneSiguiendoCount) {
                updates["siguiendo_count"] = 0
            }

            // Actualizar el documento con los campos faltantes
            db.collection("Usuarios").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    Log.d("DashboardViewModel", "✅ Contadores inicializados para: $userId")
                }
                .addOnFailureListener { e ->
                    Log.e("DashboardViewModel", "❌ Error al inicializar contadores: ${e.message}")
                }
        } else {
            Log.d("DashboardViewModel", "✅ Contadores ya existen para usuario: $userId")
        }
    }

    // Cargar rutas del usuario desde Firestore
    private fun cargarRutasUsuario(userId: String) {
        db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val rutas = mutableListOf<Ruta>()

                for (document in querySnapshot.documents) {
                    val id = document.id  // Obtener el ID del documento
                    val titulo = document.getString("nombre")
                        ?: document.getString("titulo")
                        ?: "Ruta sin título"

                    val descripcion = document.getString("descripcion") ?: ""

                    val duracion = document.get("duracion")?.toString()
                        ?: document.get("tiempo_estimado")?.toString()
                        ?: calcularDuracionEstimada(document)
                        ?: "Tiempo no especificado"

                    // Obtener userId del autor (puede ser diferente si estás en otro perfil)
                    val autorId = document.getString("userId") ?: userId

                    rutas.add(Ruta(id, titulo, descripcion, duracion, autorId))
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

    // Función para alternar el estado de seguir (BIDIRECCIONAL mejorada)
    fun alternarSeguir(currentUserId: String) {
        val targetUserId = userId ?: return

        Log.d("DashboardViewModel", "=== ALTERNAR SEGUIR BIDIRECCIONAL ===")
        Log.d("DashboardViewModel", "currentUserId: $currentUserId")
        Log.d("DashboardViewModel", "targetUserId: $targetUserId")

        if (currentUserId == targetUserId) {
            Log.d("DashboardViewModel", "⚠️ No puedes seguirte a ti mismo")
            return
        }

        val estaSiguiendoActual = _estaSiguiendo.value ?: false

        if (estaSiguiendoActual) {
            // DEJAR DE SEGUIR (eliminar de ambas partes)
            dejarDeSeguir(currentUserId, targetUserId)
        } else {
            // SEGUIR (añadir a ambas partes)
            seguirUsuario(currentUserId, targetUserId)
        }
    }

    private fun seguirUsuario(currentUserId: String, targetUserId: String) {
        Log.d("DashboardViewModel", "❤️ Siguiendo usuario (bidireccional)...")

        val timestamp = System.currentTimeMillis()

        // Obtener nombre del usuario actual
        db.collection("Usuarios").document(currentUserId).get()
            .addOnSuccessListener { currentUserDoc ->
                val nombreUsuarioActual = currentUserDoc.getString("nombre_usuario") ?: "Usuario"

                // Obtener nombre del usuario objetivo
                db.collection("Usuarios").document(targetUserId).get()
                    .addOnSuccessListener { targetUserDoc ->
                        val nombreUsuarioObjetivo = targetUserDoc.getString("nombre_usuario") ?: "Usuario"

                        val batch = db.batch()

                        // 1. Añadir a "siguiendo" del usuario actual
                        val seguirData = hashMapOf<String, Any>(
                            "userId" to targetUserId,
                            "timestamp" to timestamp,
                            "nombre" to nombreUsuarioObjetivo,
                            "fotoPerfil" to (targetUserDoc.getString("foto_perf") ?: "")

                        )

                        val seguirRef = db.collection("Usuarios").document(currentUserId)
                            .collection("siguiendo").document(targetUserId)
                        batch.set(seguirRef, seguirData)

                        // 2. Añadir a "seguidores" del usuario objetivo
                        val seguidorData = hashMapOf<String, Any>(
                            "userId" to currentUserId,
                            "timestamp" to timestamp,
                            "nombre" to nombreUsuarioActual,
                            "fotoPerfil" to (currentUserDoc.getString("foto_perf") ?: "")

                        )

                        val seguidorRef = db.collection("Usuarios").document(targetUserId)
                            .collection("seguidores").document(currentUserId)
                        batch.set(seguidorRef, seguidorData)

                        batch.commit()
                            .addOnSuccessListener {
                                Log.d("DashboardViewModel", "✅ Seguido exitosamente (ambas partes)")

                                // Actualizar contadores
                                actualizarContadores(currentUserId, targetUserId, true)

                                // Actualizar UI
                                _estaSiguiendo.value = true
                                actualizarTextoBoton()
                            }
                            .addOnFailureListener { e ->
                                Log.e("DashboardViewModel", "❌ Error al seguir: ${e.message}")
                                _error.value = "Error al seguir usuario: ${e.message}"
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("DashboardViewModel", "❌ Error al obtener datos del usuario objetivo: ${e.message}")
                        _error.value = "Error al seguir usuario"
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "❌ Error al obtener datos del usuario actual: ${e.message}")
                _error.value = "Error al seguir usuario"
            }
    }

    private fun dejarDeSeguir(currentUserId: String, targetUserId: String) {
        Log.d("DashboardViewModel", "🗑️ Dejando de seguir (bidireccional)...")

        val batch = db.batch()

        // 1. Eliminar de "siguiendo" del usuario actual
        val seguirRef = db.collection("Usuarios").document(currentUserId)
            .collection("siguiendo").document(targetUserId)
        batch.delete(seguirRef)

        // 2. Eliminar de "seguidores" del usuario objetivo
        val seguidorRef = db.collection("Usuarios").document(targetUserId)
            .collection("seguidores").document(currentUserId)
        batch.delete(seguidorRef)

        batch.commit()
            .addOnSuccessListener {
                Log.d("DashboardViewModel", "✅ Dejado de seguir exitosamente (ambas partes)")

                // Actualizar contadores
                actualizarContadores(currentUserId, targetUserId, false)

                // Actualizar UI
                _estaSiguiendo.value = false
                actualizarTextoBoton()
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "❌ Error al dejar de seguir: ${e.message}")
                _error.value = "Error al dejar de seguir: ${e.message}"
            }
    }

    // Función para actualizar contadores - CORREGIDA
    private fun actualizarContadores(currentUserId: String, targetUserId: String, incrementar: Boolean) {
        val incremento = if (incrementar) 1 else -1

        db.collection("Usuarios").document(currentUserId)
            .update(
                mapOf(
                    "siguiendo_count" to FieldValue.increment(incremento.toLong())
                )
            )

        db.collection("Usuarios").document(targetUserId)
            .update(
                mapOf(
                    "seguidores_count" to FieldValue.increment(incremento.toLong())
                )
            )
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
        _seguidoresCount.value = 0
        _siguiendoCount.value = 0
        _isLoading.value = false
        _isOnline.value = true
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}

data class Ruta(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val duracion: String,
    val autorId: String = ""
)

data class Logro(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val icono: String,
    val obtenido: Boolean,
    val fechaObtencion: Long
)