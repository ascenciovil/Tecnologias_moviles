package com.example.myapplication.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance() // Ya está configurado offline desde LoginActivity

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

    // Logros del usuario
    private val _logros = MutableLiveData<List<Logro>>().apply {
        value = emptyList()
    }
    val logros: LiveData<List<Logro>> = _logros

    private val _isLoading = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isLoading: LiveData<Boolean> = _isLoading

    // Nuevo: Estados para manejar conexión
    private val _isOnline = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isOnline: LiveData<Boolean> = _isOnline

    private val _showOfflineMessage = MutableLiveData<Boolean>().apply {
        value = false
    }
    val showOfflineMessage: LiveData<Boolean> = _showOfflineMessage

    // Cargar datos del usuario desde Firestore (con soporte offline mejorado)
    fun cargarDatosUsuario() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _isLoading.value = true

            db.collection("Usuarios").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        procesarDatosUsuario(document)
                        _isOnline.value = true
                        _showOfflineMessage.value = false
                    } else {
                        crearUsuarioEnFirestore(currentUser.uid, currentUser.email ?: "")
                    }
                    _isLoading.value = false
                }
                .addOnFailureListener { exception ->
                    // En caso de error, intentamos cargar desde cache offline
                    manejarErrorOffline()
                }
        } else {
            _isLoading.value = false
        }
    }

    // Función auxiliar para procesar datos del usuario
    private fun procesarDatosUsuario(document: com.google.firebase.firestore.DocumentSnapshot) {
        val nombreUsuario = document.getString("nombre_usuario") ?: "Usuario"
        val correo = document.getString("Correo") ?: auth.currentUser?.email ?: ""
        val descripcionUsuario = document.getString("descripcion") ?: "Esta es tu descripción personal. Puedes editarla para contar más sobre ti."

        _nombre.value = nombreUsuario
        _username.value = "@${nombreUsuario.lowercase(Locale.ROOT).replace(" ", "")}"
        _email.value = correo
        _descripcion.value = descripcionUsuario

        cargarRutasUsuario(auth.currentUser?.uid ?: "")
        cargarLogrosUsuario(auth.currentUser?.uid ?: "")
    }

    // Manejar estado offline
    private fun manejarErrorOffline() {
        _isOnline.value = false
        _showOfflineMessage.value = true
        _isLoading.value = false

        // Los datos se cargarán automáticamente desde la cache offline
        // si el usuario ya había cargado datos previamente
    }

    // Cargar rutas reales del usuario desde Firestore (mejorado para offline)
    private fun cargarRutasUsuario(userId: String) {
        db.collection("Rutas")
            .whereEqualTo("userId", userId)
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
            }
            .addOnFailureListener { exception ->
                // En modo offline, mostrar lista vacía o datos cacheados
                _rutasPublicadas.value = emptyList()
            }
    }

    // Cargar logros del usuario desde la subcolección (mejorado para offline)
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

                if (logrosList.isEmpty()) {
                    inicializarLogrosBase(userId)
                } else {
                    _logros.value = logrosList
                    _isLoading.value = false
                }
            }
            .addOnFailureListener { exception ->
                // En modo offline, intentar inicializar logros base
                inicializarLogrosBase(userId)
            }
    }

    // Inicializar logros base para un usuario (mejorado para offline)
    private fun inicializarLogrosBase(userId: String) {
        val logrosBase = listOf(
            hashMapOf(
                "nombre" to "Editor de Perfil",
                "descripcion" to "Edita tu perfil por primera vez",
                "icono" to "🎨",
                "obtenido" to false,
                "fechaObtencion" to 0L
            ),
            hashMapOf(
                "nombre" to "Explorador Navegante",
                "descripcion" to "Cambia entre pestañas de la app",
                "icono" to "🧭",
                "obtenido" to false,
                "fechaObtencion" to 0L
            ),
            hashMapOf(
                "nombre" to "Pionero de Rutas",
                "descripcion" to "Publica tu primera ruta",
                "icono" to "🗺️",
                "obtenido" to false,
                "fechaObtencion" to 0L
            ),
            hashMapOf(
                "nombre" to "Perfil Completo",
                "descripcion" to "Completa toda la información de tu perfil",
                "icono" to "✅",
                "obtenido" to false,
                "fechaObtencion" to 0L
            )
        )

        val batch = db.batch()
        val logrosIds = listOf("editar_perfil", "cambiar_pestania", "primera_ruta", "perfil_completo")

        logrosIds.forEachIndexed { index, logroId ->
            val logroRef = db.collection("Usuarios").document(userId)
                .collection("Logros").document(logroId)
            batch.set(logroRef, logrosBase[index])
        }

        batch.commit()
            .addOnSuccessListener {
                val logrosParaLiveData = logrosIds.mapIndexed { index, id ->
                    Logro(
                        id = id,
                        nombre = logrosBase[index]["nombre"] as String,
                        descripcion = logrosBase[index]["descripcion"] as String,
                        icono = logrosBase[index]["icono"] as String,
                        obtenido = false,
                        fechaObtencion = 0L
                    )
                }
                _logros.value = logrosParaLiveData
                _isLoading.value = false
            }
            .addOnFailureListener {
                // En modo offline, crear logros localmente
                val logrosOffline = logrosIds.mapIndexed { index, id ->
                    Logro(
                        id = id,
                        nombre = logrosBase[index]["nombre"] as String,
                        descripcion = logrosBase[index]["descripcion"] as String,
                        icono = logrosBase[index]["icono"] as String,
                        obtenido = false,
                        fechaObtencion = 0L
                    )
                }
                _logros.value = logrosOffline
                _isLoading.value = false
            }
    }

    // Desbloquear un logro (mejorado para offline)
    fun desbloquearLogro(logroId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val updateData = hashMapOf<String, Any>(
                "obtenido" to true,
                "fechaObtencion" to System.currentTimeMillis()
            )

            db.collection("Usuarios").document(currentUser.uid)
                .collection("Logros").document(logroId)
                .update(updateData)
                .addOnSuccessListener {
                    // Actualizar la lista local de logros
                    actualizarLogroLocalmente(logroId)
                }
                .addOnFailureListener {
                    // En modo offline, actualizar localmente igualmente
                    // Se sincronizará cuando haya conexión
                    actualizarLogroLocalmente(logroId)
                }
        }
    }

    // Actualizar logro localmente (para modo offline)
    private fun actualizarLogroLocalmente(logroId: String) {
        val logrosActuales = _logros.value ?: emptyList()
        val nuevosLogros = logrosActuales.map { logro ->
            if (logro.id == logroId) {
                logro.copy(obtenido = true, fechaObtencion = System.currentTimeMillis())
            } else {
                logro
            }
        }
        _logros.value = nuevosLogros
    }

    // Actualizar perfil (mejorado para offline)
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

                    // Desbloquear logros
                    desbloquearLogro("editar_perfil")
                    if (nuevoNombre.isNotEmpty() && nuevaDescripcion.isNotEmpty() && nuevoNombre != "Nuevo Usuario") {
                        desbloquearLogro("perfil_completo")
                    }

                    _isOnline.value = true
                    _showOfflineMessage.value = false
                }
                .addOnFailureListener { exception ->
                    // En modo offline, actualizar localmente igualmente
                    _nombre.value = nuevoNombre
                    _username.value = "@${nuevoNombre.lowercase(Locale.ROOT).replace(" ", "")}"
                    _descripcion.value = nuevaDescripcion
                    _isOnline.value = false
                    _showOfflineMessage.value = true

                    // Los cambios se sincronizarán automáticamente cuando haya conexión
                }
        }
    }

    // Función para forzar sincronización
    fun sincronizarDatos() {
        _showOfflineMessage.value = false
        cargarDatosUsuario()
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
                cargarDatosUsuario()
            }
            .addOnFailureListener {
                _isLoading.value = false
            }
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