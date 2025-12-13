package com.example.myapplication.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myapplication.CloudinaryService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import android.util.Log

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

    private val _fotoPerfilUrl = MutableLiveData<String>().apply {
        value = "default"
    }
    val fotoPerfilUrl: LiveData<String> = _fotoPerfilUrl

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

    private val _mensaje = MutableLiveData<String?>()
    val mensaje: LiveData<String?> = _mensaje

    private var rutasListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var logrosListener: com.google.firebase.firestore.ListenerRegistration? = null

    private companion object {
        const val CLOUDINARY_CLOUD_NAME = "dof4gj5pr"
        const val CLOUDINARY_UPLOAD_PRESET = "rutas_fotos"
    }

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
                    manejarErrorOffline()
                }
        } else {
            _isLoading.value = false
        }
    }

    private fun procesarDatosUsuario(document: com.google.firebase.firestore.DocumentSnapshot) {
        val nombreUsuario = document.getString("nombre_usuario") ?: "Usuario"
        val correo = document.getString("Correo") ?: auth.currentUser?.email ?: ""
        val descripcionUsuario = document.getString("descripcion")
            ?: "Esta es tu descripción personal. Puedes editarla para contar más sobre ti."
        val fotoPerfil = document.getString("foto_perfil") ?: "default"

        _seguidoresCount.value = (document.get("seguidores_count") as? Long)?.toInt() ?: 0
        _siguiendoCount.value = (document.get("siguiendo_count") as? Long)?.toInt() ?: 0

        _nombre.value = nombreUsuario
        _username.value = "@${nombreUsuario.lowercase(Locale.ROOT).replace(" ", "")}"
        _email.value = correo
        _descripcion.value = descripcionUsuario
        _fotoPerfilUrl.value = fotoPerfil

        cargarRutasUsuario(auth.currentUser?.uid ?: "")
        cargarYVerificarLogrosUsuario(auth.currentUser?.uid ?: "")

        // Verificar logros basados en datos actuales
        verificarLogrosPerfil()
        verificarLogrosSociales()
        verificarLogrosCreacion()
    }

    suspend fun subirFotoACloudinary(uri: Uri, context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null

                val tempFile = File.createTempFile("perfil_", ".jpg", context.cacheDir)
                FileOutputStream(tempFile).use { out ->
                    inputStream.use { it.copyTo(out) }
                }

                val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData(
                    "file",
                    tempFile.name,
                    requestFile
                )

                val presetBody = CLOUDINARY_UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = CloudinaryService.api.uploadImage(
                    CLOUDINARY_CLOUD_NAME,
                    body,
                    presetBody
                )

                tempFile.delete()

                response.secure_url
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun actualizarFotoPerfil(imageUrl: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val updates = hashMapOf<String, Any>(
                "foto_perfil" to imageUrl
            )

            db.collection("Usuarios").document(currentUser.uid)
                .update(updates)
                .addOnSuccessListener {
                    _fotoPerfilUrl.value = imageUrl

                    // Desbloquear logro de foto
                    if (imageUrl != "default") {
                        desbloquearLogro("buena_cara")
                    }

                    _mensaje.value = "Foto de perfil actualizada"
                }
                .addOnFailureListener { exception ->
                    _mensaje.value = "Error al actualizar foto: ${exception.message}"
                }
        }
    }

    fun eliminarFotoPerfil() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val updates = hashMapOf<String, Any>(
                "foto_perfil" to "default"
            )

            db.collection("Usuarios").document(currentUser.uid)
                .update(updates)
                .addOnSuccessListener {
                    _fotoPerfilUrl.value = "default"
                    _mensaje.value = "Foto eliminada"
                }
                .addOnFailureListener { exception ->
                    _mensaje.value = "Error al eliminar foto: ${exception.message}"
                }
        }
    }

    private fun manejarErrorOffline() {
        _isOnline.value = false
        _showOfflineMessage.value = true
        _isLoading.value = false
    }

    private fun cargarRutasUsuario(userId: String) {
        rutasListener?.remove()

        rutasListener = db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, exception ->

                if (exception != null) {
                    _rutasPublicadas.value = emptyList()
                    return@addSnapshotListener
                }

                val rutas = mutableListOf<Ruta>()

                val docs = snapshot?.documents ?: emptyList()

                // Filtrar visible: si no existe -> se considera visible
                val visibles = docs.filter { doc ->
                    doc.getBoolean("visible") != false
                }

                // Ordenar por createdAt si existe
                val ordenadas = visibles.sortedByDescending { doc ->
                    doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                }

                for (document in ordenadas) {
                    val id = document.id
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

                // Verificar logros de creación basados en rutas
                verificarLogrosCreacion()
            }
    }

    fun ocultarRuta(rutaId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _isLoading.value = true

            db.collection("Rutas").document(rutaId)
                .update("visible", false)
                .addOnSuccessListener {
                    Log.d("ProfileViewModel", "✅ Ruta $rutaId ocultada exitosamente")

                    val rutasActuales = _rutasPublicadas.value ?: emptyList()
                    val nuevasRutas = rutasActuales.filter { it.id != rutaId }
                    _rutasPublicadas.value = nuevasRutas

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

    private fun cargarYVerificarLogrosUsuario(userId: String) {
        db.collection("Usuarios").document(userId)
            .collection("Logros")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val logrosList = mutableListOf<Logro>()
                val logrosExistentes = mutableSetOf<String>()

                for (document in querySnapshot.documents) {
                    val id = document.id
                    val nombre = document.getString("nombre") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val icono = document.getString("icono") ?: ""
                    val obtenido = document.getBoolean("obtenido") ?: false
                    val fechaObtencion = document.getLong("fechaObtencion") ?: 0L
                    val tipo = document.getString("tipo") ?: "general"
                    val progresoActual = (document.get("progreso_actual") as? Number)?.toInt() ?: 0
                    val requerido = (document.get("requerido") as? Number)?.toInt() ?: 1

                    logrosList.add(Logro(id, nombre, descripcion, icono, obtenido, fechaObtencion, tipo, progresoActual, requerido))
                    logrosExistentes.add(id)
                }

                // Lista completa de logros que deberían existir
                val logrosCompletos = listOf(
                    "editar_perfil", "perfil_completo", "buena_cara", "descripcion_larga",
                    "curioso", "explorador_activo", "bienvenido", "famoso",
                    "cuidado_ego", "conectado", "tocando_pasto", "cartografo"
                )

                val logrosFaltantes = logrosCompletos.filter { !logrosExistentes.contains(it) }

                if (logrosFaltantes.isNotEmpty()) {
                    // Crear logros faltantes
                    crearLogrosFaltantes(userId, logrosFaltantes, logrosList)
                } else {
                    _logros.value = logrosList
                    _isLoading.value = false

                    // Configurar listener para cambios en tiempo real
                    configurarLogrosListener(userId)

                    // Verificar logros retroactivos
                    verificarLogrosRetroactivos(userId)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileViewModel", "Error al cargar logros: ${exception.message}")
                // Si hay error, inicializar todos los logros
                inicializarLogrosBase(userId)
            }
    }

    private fun crearLogrosFaltantes(userId: String, faltantes: List<String>, logrosExistentes: List<Logro>) {
        Log.d("ProfileViewModel", "Creando logros faltantes: ${faltantes.size} logros")

        val logrosBase = mapOf(
            "editar_perfil" to hashMapOf(
                "nombre" to "Editor de Perfil",
                "descripcion" to "Edita tu perfil por primera vez",
                "icono" to "🎨",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "perfil_completo" to hashMapOf(
                "nombre" to "Perfil Completo",
                "descripcion" to "Completa nombre y descripción",
                "icono" to "✅",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "buena_cara" to hashMapOf(
                "nombre" to "Buena Cara",
                "descripcion" to "Cambia tu foto de perfil",
                "icono" to "📸",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "descripcion_larga" to hashMapOf(
                "nombre" to "Ni que fuera tésis",
                "descripcion" to "Escribe una descripción larga",
                "icono" to "✍️",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "curioso" to hashMapOf(
                "nombre" to "Curioso",
                "descripcion" to "Abre una ruta",
                "icono" to "👀",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "exploracion",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "explorador_activo" to hashMapOf(
                "nombre" to "Explorador Activo",
                "descripcion" to "Explora 5 rutas",
                "icono" to "🔍",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "exploracion",
                "progreso_actual" to 0,
                "requerido" to 5
            ),
            "bienvenido" to hashMapOf(
                "nombre" to "Bienvenido",
                "descripcion" to "Entra por primera vez",
                "icono" to "👋",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "famoso" to hashMapOf(
                "nombre" to "Famoso",
                "descripcion" to "Consigue tu primer seguidor",
                "icono" to "🌱",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "cuidado_ego" to hashMapOf(
                "nombre" to "Cuidado con el ego",
                "descripcion" to "Llega a 5 seguidores",
                "icono" to "🌟",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 5
            ),
            "conectado" to hashMapOf(
                "nombre" to "Conectado",
                "descripcion" to "Sigue a alguien",
                "icono" to "🤝",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "tocando_pasto" to hashMapOf(
                "nombre" to "Tocando pasto",
                "descripcion" to "Publica tu primera ruta",
                "icono" to "🗺️",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "creacion",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            "cartografo" to hashMapOf(
                "nombre" to "Cartógrafo",
                "descripcion" to "Publica 5 rutas",
                "icono" to "🧭",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "creacion",
                "progreso_actual" to 0,
                "requerido" to 5
            )
        )

        val batch = db.batch()

        faltantes.forEach { logroId ->
            logrosBase[logroId]?.let { datosLogro ->
                val logroRef = db.collection("Usuarios").document(userId)
                    .collection("Logros").document(logroId)
                batch.set(logroRef, datosLogro)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("ProfileViewModel", "✅ Logros faltantes creados: ${faltantes.size}")

                // Combinar logros existentes con los nuevos
                val todosLogros = logrosExistentes.toMutableList()

                faltantes.forEach { logroId ->
                    logrosBase[logroId]?.let { datosLogro ->
                        val nuevoLogro = Logro(
                            id = logroId,
                            nombre = datosLogro["nombre"] as String,
                            descripcion = datosLogro["descripcion"] as String,
                            icono = datosLogro["icono"] as String,
                            obtenido = false,
                            fechaObtencion = 0L,
                            tipo = datosLogro["tipo"] as String,
                            progresoActual = (datosLogro["progreso_actual"] as? Number)?.toInt() ?: 0,
                            requerido = (datosLogro["requerido"] as? Number)?.toInt() ?: 1
                        )
                        todosLogros.add(nuevoLogro)
                    }
                }

                _logros.value = todosLogros
                _isLoading.value = false

                // Configurar listener para cambios en tiempo real
                configurarLogrosListener(userId)

                // Verificar logros retroactivos
                verificarLogrosRetroactivos(userId)
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileViewModel", "❌ Error al crear logros faltantes: ${exception.message}")
                _logros.value = logrosExistentes
                _isLoading.value = false
            }
    }

    private fun verificarLogrosRetroactivos(userId: String) {
        Log.d("ProfileViewModel", "Verificando logros retroactivos...")

        // Cargar datos del usuario para verificar logros
        db.collection("Usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Verificar logro de bienvenida (siempre se desbloquea para usuarios existentes)
                    val logroBienvenido = _logros.value?.find { it.id == "bienvenido" }
                    if (logroBienvenido?.obtenido == false) {
                        // Si el usuario ya tenía cuenta, ya fue "bienvenido"
                        desbloquearLogro("bienvenido")
                    }

                    // Verificar si ya editó su perfil antes
                    val nombre = document.getString("nombre_usuario") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val fotoPerfil = document.getString("foto_perfil") ?: "default"

                    // Verificar si ya editó perfil (si el nombre no es el default)
                    if (nombre != "Nuevo Usuario" && nombre.isNotEmpty()) {
                        val logroEditor = _logros.value?.find { it.id == "editar_perfil" }
                        if (logroEditor?.obtenido == false) {
                            desbloquearLogro("editar_perfil")
                        }
                    }

                    // Verificar perfil completo
                    if (nombre.isNotEmpty() && descripcion.isNotEmpty() && nombre != "Nuevo Usuario") {
                        val logroCompleto = _logros.value?.find { it.id == "perfil_completo" }
                        if (logroCompleto?.obtenido == false) {
                            desbloquearLogro("perfil_completo")
                        }
                    }

                    // Verificar descripción larga
                    if (descripcion.length > 100) {
                        val logroDescripcion = _logros.value?.find { it.id == "descripcion_larga" }
                        if (logroDescripcion?.obtenido == false) {
                            desbloquearLogro("descripcion_larga")
                        }
                    }

                    // Verificar foto de perfil
                    if (fotoPerfil != "default") {
                        val logroFoto = _logros.value?.find { it.id == "buena_cara" }
                        if (logroFoto?.obtenido == false) {
                            desbloquearLogro("buena_cara")
                        }
                    }

                    // Verificar datos sociales
                    verificarLogrosSociales()

                    // Verificar logros de creación (rutas)
                    verificarLogrosCreacion()
                }
            }
    }

    private fun configurarLogrosListener(userId: String) {
        logrosListener?.remove()

        logrosListener = db.collection("Usuarios").document(userId)
            .collection("Logros")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e("ProfileViewModel", "Error en listener de logros: ${exception.message}")
                    return@addSnapshotListener
                }

                val logrosList = mutableListOf<Logro>()

                for (document in snapshot?.documents ?: emptyList()) {
                    val id = document.id
                    val nombre = document.getString("nombre") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val icono = document.getString("icono") ?: ""
                    val obtenido = document.getBoolean("obtenido") ?: false
                    val fechaObtencion = document.getLong("fechaObtencion") ?: 0L
                    val tipo = document.getString("tipo") ?: "general"
                    val progresoActual = (document.get("progreso_actual") as? Number)?.toInt() ?: 0
                    val requerido = (document.get("requerido") as? Number)?.toInt() ?: 1

                    logrosList.add(Logro(id, nombre, descripcion, icono, obtenido, fechaObtencion, tipo, progresoActual, requerido))
                }

                _logros.value = logrosList
            }
    }

    private fun inicializarLogrosBase(userId: String) {
        val logrosBase = listOf(
            hashMapOf(
                "nombre" to "Editor de Perfil",
                "descripcion" to "Edita tu perfil por primera vez",
                "icono" to "🎨",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Perfil Completo",
                "descripcion" to "Completa nombre y descripción",
                "icono" to "✅",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Buena Cara",
                "descripcion" to "Cambia tu foto de perfil",
                "icono" to "📸",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Ni que fuera tésis",
                "descripcion" to "Escribe una descripción larga",
                "icono" to "✍️",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "perfil",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Curioso",
                "descripcion" to "Abre una ruta",
                "icono" to "👀",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "exploracion",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Explorador Activo",
                "descripcion" to "Explora 5 rutas",
                "icono" to "🔍",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "exploracion",
                "progreso_actual" to 0,
                "requerido" to 5
            ),
            hashMapOf(
                "nombre" to "Bienvenido",
                "descripcion" to "Entra por primera vez",
                "icono" to "👋",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Famoso",
                "descripcion" to "Consigue tu primer seguidor",
                "icono" to "🌱",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Cuidado con el ego",
                "descripcion" to "Llega a 5 seguidores",
                "icono" to "🌟",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 5
            ),
            hashMapOf(
                "nombre" to "Conectado",
                "descripcion" to "Sigue a alguien",
                "icono" to "🤝",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "social",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Tocando pasto",
                "descripcion" to "Publica tu primera ruta",
                "icono" to "🗺️",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "creacion",
                "progreso_actual" to 0,
                "requerido" to 1
            ),
            hashMapOf(
                "nombre" to "Cartógrafo",
                "descripcion" to "Publica 5 rutas",
                "icono" to "🧭",
                "obtenido" to false,
                "fechaObtencion" to 0L,
                "tipo" to "creacion",
                "progreso_actual" to 0,
                "requerido" to 5
            )
        )

        val logrosIds = listOf(
            "editar_perfil", "perfil_completo", "buena_cara", "descripcion_larga",
            "curioso", "explorador_activo", "bienvenido", "famoso",
            "cuidado_ego", "conectado", "tocando_pasto", "cartografo"
        )

        val batch = db.batch()

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
                        fechaObtencion = 0L,
                        tipo = logrosBase[index]["tipo"] as String,
                        progresoActual = (logrosBase[index]["progreso_actual"] as? Number)?.toInt() ?: 0,
                        requerido = (logrosBase[index]["requerido"] as? Number)?.toInt() ?: 1
                    )
                }
                _logros.value = logrosParaLiveData
                _isLoading.value = false

                // Configurar listener
                configurarLogrosListener(userId)

                // Desbloquear logro de bienvenida automáticamente para nuevo usuario
                desbloquearLogro("bienvenido")
            }
            .addOnFailureListener {
                val logrosOffline = logrosIds.mapIndexed { index, id ->
                    Logro(
                        id = id,
                        nombre = logrosBase[index]["nombre"] as String,
                        descripcion = logrosBase[index]["descripcion"] as String,
                        icono = logrosBase[index]["icono"] as String,
                        obtenido = false,
                        fechaObtencion = 0L,
                        tipo = logrosBase[index]["tipo"] as String,
                        progresoActual = (logrosBase[index]["progreso_actual"] as? Number)?.toInt() ?: 0,
                        requerido = (logrosBase[index]["requerido"] as? Number)?.toInt() ?: 1
                    )
                }
                _logros.value = logrosOffline
                _isLoading.value = false
            }
    }

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
                    actualizarLogroLocalmente(logroId)
                    _mensaje.value = "¡Logro desbloqueado!"
                }
                .addOnFailureListener {
                    // Intentar actualizar localmente aunque falle en Firebase
                    actualizarLogroLocalmente(logroId)
                }
        }
    }

    fun incrementarProgresoLogro(logroId: String, incremento: Int = 1) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Usuarios").document(currentUser.uid)
                .collection("Logros").document(logroId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val progresoActual = (document.get("progreso_actual") as? Number)?.toInt() ?: 0
                        val requerido = (document.get("requerido") as? Number)?.toInt() ?: 1
                        val obtenido = document.getBoolean("obtenido") ?: false

                        if (!obtenido) {
                            val nuevoProgreso = progresoActual + incremento

                            db.collection("Usuarios").document(currentUser.uid)
                                .collection("Logros").document(logroId)
                                .update("progreso_actual", nuevoProgreso)
                                .addOnSuccessListener {
                                    actualizarProgresoLocalmente(logroId, nuevoProgreso)

                                    // Verificar si se alcanzó el objetivo
                                    if (nuevoProgreso >= requerido) {
                                        desbloquearLogro(logroId)
                                    }
                                }
                        }
                    }
                }
        }
    }

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

    private fun actualizarProgresoLocalmente(logroId: String, nuevoProgreso: Int) {
        val logrosActuales = _logros.value ?: emptyList()
        val nuevosLogros = logrosActuales.map { logro ->
            if (logro.id == logroId) {
                logro.copy(progresoActual = nuevoProgreso)
            } else {
                logro
            }
        }
        _logros.value = nuevosLogros
    }

    // Función para verificar logros basados en perfil
    fun verificarLogrosPerfil() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Usuarios").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val nombre = document.getString("nombre_usuario") ?: ""
                        val descripcion = document.getString("descripcion") ?: ""
                        val fotoPerfil = document.getString("foto_perfil") ?: "default"

                        // Perfil Completo
                        if (nombre.isNotEmpty() && descripcion.isNotEmpty() && nombre != "Nuevo Usuario") {
                            // Verificar si ya está desbloqueado
                            val logroCompleto = _logros.value?.find { it.id == "perfil_completo" }
                            if (logroCompleto?.obtenido == false) {
                                desbloquearLogro("perfil_completo")
                            }
                        }

                        // Descripción larga (>100 caracteres)
                        if (descripcion.length > 100) {
                            val logroDescripcion = _logros.value?.find { it.id == "descripcion_larga" }
                            if (logroDescripcion?.obtenido == false) {
                                desbloquearLogro("descripcion_larga")
                            }
                        }

                        // Foto diferente de default
                        if (fotoPerfil != "default") {
                            val logroFoto = _logros.value?.find { it.id == "buena_cara" }
                            if (logroFoto?.obtenido == false) {
                                desbloquearLogro("buena_cara")
                            }
                        }
                    }
                }
        }
    }

    // Función para verificar logros sociales
    fun verificarLogrosSociales() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Usuarios").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val seguidores = (document.get("seguidores_count") as? Long)?.toInt() ?: 0
                        val siguiendo = (document.get("siguiendo_count") as? Long)?.toInt() ?: 0

                        // Famoso (primer seguidor)
                        if (seguidores >= 1) {
                            val logroFamoso = _logros.value?.find { it.id == "famoso" }
                            if (logroFamoso?.obtenido == false) {
                                incrementarProgresoLogro("famoso", seguidores)
                            }
                        }

                        // Cuidado con el ego (5 seguidores)
                        if (seguidores >= 5) {
                            val logroEgo = _logros.value?.find { it.id == "cuidado_ego" }
                            if (logroEgo?.obtenido == false) {
                                incrementarProgresoLogro("cuidado_ego", seguidores)
                            }
                        }

                        // Conectado (seguir a alguien)
                        if (siguiendo >= 1) {
                            val logroConectado = _logros.value?.find { it.id == "conectado" }
                            if (logroConectado?.obtenido == false) {
                                incrementarProgresoLogro("conectado", siguiendo)
                            }
                        }
                    }
                }
        }
    }

    // Función para verificar logros de exploración
    fun verificarLogrosExploracion() {
        // Esta función se llamará desde VistaRuta cuando se abre una ruta
        // El contador se mantiene localmente en SharedPreferences
    }

    // Función para registrar exploración de ruta
    fun registrarExploracionRuta() {
        incrementarProgresoLogro("curioso")
        incrementarProgresoLogro("explorador_activo")
    }

    // Función para verificar logros de creación
    fun verificarLogrosCreacion() {
        val rutasCount = _rutasPublicadas.value?.size ?: 0

        if (rutasCount >= 1) {
            val logroTocandoPasto = _logros.value?.find { it.id == "tocando_pasto" }
            if (logroTocandoPasto?.obtenido == false) {
                incrementarProgresoLogro("tocando_pasto", rutasCount)
            }
        }

        if (rutasCount >= 5) {
            val logroCartografo = _logros.value?.find { it.id == "cartografo" }
            if (logroCartografo?.obtenido == false) {
                incrementarProgresoLogro("cartografo", rutasCount)
            }
        }
    }

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
                    _nombre.value = nuevoNombre
                    _username.value = "@${nuevoNombre.lowercase(Locale.ROOT).replace(" ", "")}"
                    _descripcion.value = nuevaDescripcion

                    // Desbloquear logros relacionados
                    desbloquearLogro("editar_perfil")

                    // Verificar otros logros de perfil
                    verificarLogrosPerfil()

                    _mensaje.value = "Perfil actualizado exitosamente"
                }
                .addOnFailureListener { exception ->
                    _nombre.value = nuevoNombre
                    _username.value = "@${nuevoNombre.lowercase(Locale.ROOT).replace(" ", "")}"
                    _descripcion.value = nuevaDescripcion
                    _isOnline.value = false
                    _showOfflineMessage.value = true
                    _mensaje.value = "Cambios guardados localmente. Se sincronizarán cuando haya conexión."
                }
        }
    }

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
            "foto_perfil" to "default",
            "seguidores_count" to 0,
            "siguiendo_count" to 0,
            "seguidos" to emptyList<String>(),
            "rutas_creadas" to emptyList<String>(),
            "rutas_exploradas" to 0
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

    override fun onCleared() {
        super.onCleared()
        rutasListener?.remove()
        logrosListener?.remove()
    }
}

data class Ruta(
    val id: String,
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
    val fechaObtencion: Long,
    val tipo: String = "general",
    val progresoActual: Int = 0,
    val requerido: Int = 1
)