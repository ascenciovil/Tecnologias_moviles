package com.example.myapplication

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.example.myapplication.offline.PendingPhoto
import com.example.myapplication.offline.PendingUploadDatabase
import com.example.myapplication.offline.UploadScheduler
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



class PendingUploadsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var adapter: PendingUploadsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_uploads)

        toolbar = findViewById(R.id.topAppBarPending)
        recycler = findViewById(R.id.recycler_pending)
        emptyText = findViewById(R.id.tv_empty_pending)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = PendingUploadsAdapter(
            onRetry = { pendingId ->
                UploadScheduler.enqueue(this, pendingId)
                Toast.makeText(this, "Reintento encolado ✅", Toast.LENGTH_SHORT).show()
                loadPending()
            },
            onDelete = { pendingId ->
                lifecycleScope.launch {
                    val ok = deletePendingAndFiles(pendingId)
                    if (ok) Toast.makeText(this@PendingUploadsActivity, "Eliminado ✅", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this@PendingUploadsActivity, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                    loadPending()
                }
            }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadPending()
    }

    private fun loadPending() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val db = PendingUploadDatabase.getInstance(this@PendingUploadsActivity)
                db.dao().getAllPendingOrFailed()
                    .sortedByDescending { it.createdAt }
                    .map { entity ->
                        PendingUploadUi(
                            id = entity.id,
                            nombre = entity.nombre,
                            descripcion = entity.descripcion,
                            status = entity.status,
                            createdAtLabel = formatDate(entity.createdAt),
                            lastError = entity.lastError
                        )
                    }
            }

            adapter.submit(items)

            val empty = items.isEmpty()
            emptyText.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
            recycler.visibility = if (empty) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private suspend fun deletePendingAndFiles(pendingId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Cancelar worker si está encolado
                WorkManager.getInstance(this@PendingUploadsActivity)
                    .cancelUniqueWork("upload_route_$pendingId")

                val db = PendingUploadDatabase.getInstance(this@PendingUploadsActivity)
                val dao = db.dao()
                val entity = dao.getById(pendingId) ?: return@withContext false

                // Borrar archivos locales de las fotos (si existen)
                val gson = Gson()
                val photoType = object : TypeToken<List<PendingPhoto>>() {}.type
                val photos: List<PendingPhoto> = runCatching {
                    gson.fromJson<List<PendingPhoto>>(entity.fotosJson, photoType)
                }.getOrElse { emptyList<PendingPhoto>() }


                photos.forEach { p ->
                    runCatching {
                        val f = File(p.path)
                        // Seguridad: solo borramos si está dentro de la carpeta pending_photos de tu app
                        val safeDir = getExternalFilesDir("pending_photos")?.absolutePath ?: ""
                        if (safeDir.isNotBlank() && f.absolutePath.startsWith(safeDir)) {
                            f.delete()
                        }
                    }
                }

                dao.deleteById(pendingId)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun formatDate(ms: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(ms))
        } catch (_: Exception) {
            ms.toString()
        }
    }
}

data class PendingUploadUi(
    val id: Long,
    val nombre: String,
    val descripcion: String,
    val status: String,
    val createdAtLabel: String,
    val lastError: String?
)
