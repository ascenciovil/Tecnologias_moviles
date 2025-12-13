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
        cargarLogrosUsuario(auth.currentUser?.uid ?: "")
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

        // Si ya había listener, lo removemos para no duplicar
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

                // Ordenar por createdAt si existe (recomendado)
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

    fun restaurarRuta(rutaId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Rutas").document(rutaId)
                .update("visible", true)
                .addOnSuccessListener {
                    Log.d("ProfileViewModel", "✅ Ruta $rutaId restaurada exitosamente")
                    cargarRutasUsuario(currentUser.uid)
                    _mensaje.value = "Ruta restaurada exitosamente"
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileViewModel", "❌ Error al restaurar ruta: ${exception.message}")
                    _mensaje.value = "Error al restaurar la ruta"
                }
        }
    }

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
                inicializarLogrosBase(userId)
            }
    }

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
                }
                .addOnFailureListener {
                    actualizarLogroLocalmente(logroId)
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

                    desbloquearLogro("editar_perfil")
                    if (nuevoNombre.isNotEmpty() && nuevaDescripcion.isNotEmpty() && nuevoNombre != "Nuevo Usuario") {
                        desbloquearLogro("perfil_completo")
                    }

                    _isOnline.value = true
                    _showOfflineMessage.value = false
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
    override fun onCleared() {
        super.onCleared()
        rutasListener?.remove()
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
    val fechaObtencion: Long
)

