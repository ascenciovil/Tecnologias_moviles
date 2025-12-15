package com.example.myapplication.offline

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.CloudinaryService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import android.location.Geocoder
import java.util.Locale

private const val CLOUDINARY_CLOUD_NAME = "dof4gj5pr"
private const val CLOUDINARY_UPLOAD_PRESET = "rutas_fotos"

class UploadRouteWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pendingId = inputData.getLong("pendingId", -1L)
        if (pendingId <= 0L) return@withContext Result.failure()

        val db = PendingUploadDatabase.getInstance(applicationContext)
        val dao = db.dao()

        val entity = dao.getById(pendingId) ?: run {
            Log.d("OFFLINE_UPLOAD", "No entity for pendingId=$pendingId (already deleted?)")
            return@withContext Result.success()
        }

        try {
            Log.d("OFFLINE_UPLOAD", "Worker started pendingId=$pendingId")
            dao.updateStatus(pendingId, "UPLOADING", null)

            val gson = Gson()

            // ✅ Parse coords (sin errores de inferencia)
            val coordType = object : TypeToken<List<PendingCoord>>() {}.type
            val coords: List<PendingCoord> = try {
                gson.fromJson<List<PendingCoord>>(entity.coordenadasJson, coordType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val region: String? = if (coords.isNotEmpty()) {
                obtenerRegion(coords.first().latitude, coords.first().longitude)
            } else null

            val regionSafe = (region as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Región desconocida"

            // ✅ Parse fotos (sin errores de inferencia)
            val photoType = object : TypeToken<List<PendingPhoto>>() {}.type
            val photos: List<PendingPhoto> = try {
                gson.fromJson<List<PendingPhoto>>(entity.fotosJson, photoType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            // 1) Subir fotos a Cloudinary desde PATH local
            val fotosFinales = mutableListOf<Map<String, Any?>>()

            for (p in photos) {
                val file = File(p.path)
                if (!file.exists()) throw IOException("No existe el archivo: ${p.path}")

                val url = uploadFileToCloudinary(file)
                if (url.isNullOrBlank()) throw IOException("Cloudinary devolvió url null para ${file.name}")

                fotosFinales.add(
                    mapOf(
                        "url" to url,
                        "lat" to p.lat,
                        "lng" to p.lng,
                        "origen" to p.origin
                    )
                )
            }

            // 2) Subir ruta a Firestore
            val userId = entity.userId.ifBlank {
                FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"
            }

            val coordList = coords.map { c ->
                mapOf(
                    "latitude" to c.latitude,
                    "longitude" to c.longitude
                )
            }

            val rutaData = hashMapOf(
                "nombre" to entity.nombre,
                "descripcion" to entity.descripcion,
                "userId" to userId,
                "imagenes" to fotosFinales,
                "rating" to 0, // Orden por calificación
                "coordenadas" to coordList,

                "visible" to true,
                "region" to regionSafe,

                "createdAt" to FieldValue.serverTimestamp()
            )

            FirebaseFirestore.getInstance()
                .collection("Rutas")
                .add(rutaData)
                .await()

            // 3) Limpieza
            photos.forEach { runCatching { File(it.path).delete() } }
            dao.deleteById(pendingId)

            Log.d("OFFLINE_UPLOAD", "Worker SUCCESS pendingId=$pendingId")
            Result.success()

        } catch (e: Exception) {
            Log.e("OFFLINE_UPLOAD", "Worker FAILED pendingId=$pendingId err=${e.message}", e)
            dao.updateStatus(pendingId, "FAILED", e.message ?: "Error desconocido")

            // Si es error de IO (red/archivo) → reintenta automáticamente
            if (e is IOException) Result.retry() else Result.failure()
        }
    }

    private suspend fun uploadFileToCloudinary(file: File): String? {
        return try {
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val presetBody =
                CLOUDINARY_UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())

            val resp = CloudinaryService.api.uploadImage(
                CLOUDINARY_CLOUD_NAME,
                body,
                presetBody
            )
            resp.secure_url
        } catch (e: Exception) {
            Log.e("OFFLINE_UPLOAD", "Cloudinary upload failed: ${e.message}", e)
            null
        }
    }
    private fun obtenerRegion(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lng, 1)

            if (!results.isNullOrEmpty()) {
                val address = results[0]

                listOfNotNull(
                    address.locality,      // ciudad
                    address.adminArea,     // estado / región
                    address.countryName    // país
                ).joinToString(", ")
            } else null
        } catch (e: Exception) {
            null
        }
    }

}
