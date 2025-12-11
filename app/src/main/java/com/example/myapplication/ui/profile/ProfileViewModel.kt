package com.example.myapplication.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import android.util.Log

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

    // NUEVO: Contadores de seguidores y siguiendo
    private val _seguidoresCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val seguidoresCount: LiveData<Int> = _seguidoresCount

    private val _siguiendoCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val siguiendoCount: LiveData<Int> = _siguiendoCount

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

    // Estados para manejar conexión
    private val _isOnline = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isOnline: LiveData<Boolean> = _isOnline

    private val _showOfflineMessage = MutableLiveData<Boolean>().apply {
        value = false
    }
    val showOfflineMessage: LiveData<Boolean> = _showOfflineMessage

    // NUEVO: Para mostrar mensajes de éxito/error
    private val _mensaje = MutableLiveData<String?>()
    val mensaje: LiveData<String?> = _mensaje

    // Cargar datos del usuario desde Firestore
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
        val descripcionUsuario = document.getString("descripcion")
            ?: "Esta es tu descripción personal. Puedes editarla para contar más sobre ti."

        // Cargar contadores
        _seguidoresCount.value = (document.get("seguidores_count") as? Long)?.toInt() ?: 0
        _siguiendoCount.value = (document.get("siguiendo_count") as? Long)?.toInt() ?: 0

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
    }

    // Cargar rutas reales del usuario desde Firestore
    private fun cargarRutasUsuario(userId: String) {






        db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .whereEqualTo("visible", true) // NUEVO: Solo cargar rutas visibles
            .get()
            .addOnSuccessListener { querySnapshot ->
                val rutas = mutableListOf<Ruta>()

                for (document in querySnapshot.documents) {
                    val id = document.id  // Obtener ID del documento
                    val titulo = document.getString("nombre")
                        ?: document.getString("titulo")
                        ?: "Ruta sin título"

                    val descripcion = document.getString("descripcion") ?: ""
                    val duracion = document.get("duracion")?.toString()
                        ?: document.get("tiempo_estimado")?.toString()
                        ?: "Tiempo no especificado"

                    rutas.add(Ruta(id, titulo, descripcion, duracion))
                }

                _rutasPublicadas.value = rutas
            }
            .addOnFailureListener { exception ->
                // En modo offline, mostrar lista vacía o datos cacheados
                _rutasPublicadas.value = emptyList()
            }
    }

    // NUEVO: Función para ocultar/archivar una ruta
    fun ocultarRuta(rutaId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _isLoading.value = true

            db.collection("Rutas").document(rutaId)
                .update("visible", false) // Cambiar a false para ocultar
                .addOnSuccessListener {
                    Log.d("ProfileViewModel", "✅ Ruta $rutaId ocultada exitosamente")

                    // Actualizar la lista localmente
                    val rutasActuales = _rutasPublicadas.value ?: emptyList()
                    val nuevasRutas = rutasActuales.filter { it.id != rutaId }
                    _rutasPublicadas.value = nuevasRutas

                    // Mostrar mensaje de éxito
                    _mensaje.value = "Ruta ocultada exitosamente"

                    _isLoading.value = false
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileViewModel", "❌ Error al ocultar ruta: ${exception.message}")
                    _mensaje.value = "Error al ocultar la ruta: ${exception.message}"
                    _isLoading.value = false
                }
        }
    }

    // NUEVO: Función para obtener rutas ocultas (si se quiere implementar recuperación)
    fun cargarRutasOcultas(userId: String) {
        db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .whereEqualTo("visible", false)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val rutasOcultas = mutableListOf<Ruta>()

                for (document in querySnapshot.documents) {
                    val id = document.id
                    val titulo = document.getString("nombre") ?: "Ruta sin título"
                    val descripcion = document.getString("descripcion") ?: ""
                    val duracion = document.get("duracion")?.toString() ?: "Tiempo no especificado"

                    rutasOcultas.add(Ruta(id, titulo, descripcion, duracion))
                }

                Log.d("ProfileViewModel", "📂 Rutas ocultas encontradas: ${rutasOcultas.size}")
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileViewModel", "Error al cargar rutas ocultas: ${exception.message}")
            }
    }

    // NUEVO: Función para restaurar una ruta oculta
    fun restaurarRuta(rutaId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Rutas").document(rutaId)
                .update("visible", true)
                .addOnSuccessListener {
                    Log.d("ProfileViewModel", "✅ Ruta $rutaId restaurada exitosamente")
                    // Recargar rutas para incluir la restaurada
                    cargarRutasUsuario(currentUser.uid)
                    _mensaje.value = "Ruta restaurada exitosamente"
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileViewModel", "❌ Error al restaurar ruta: ${exception.message}")
                    _mensaje.value = "Error al restaurar la ruta"
                }
        }
    }

    // Cargar logros del usuario desde la subcolección
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

    // Inicializar logros base para un usuario
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

    // Desbloquear un logro
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

    // Actualizar perfil
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
                    _mensaje.value = "Perfil actualizado exitosamente"
                }
                .addOnFailureListener { exception ->
                    // En modo offline, actualizar localmente igualmente
                    _nombre.value = nuevoNombre
                    _username.value = "@${nuevoNombre.lowercase(Locale.ROOT).replace(" ", "")}"
                    _descripcion.value = nuevaDescripcion
                    _isOnline.value = false
                    _showOfflineMessage.value = true
                    _mensaje.value = "Cambios guardados localmente. Se sincronizarán cuando haya conexión."
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
            "seguidores_count" to 0,    // Añadir
            "siguiendo_count" to 0,     // Añadir
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

// MODIFICADO: Clase Ruta con ID
data class Ruta(
    val id: String,           // Añadir ID
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