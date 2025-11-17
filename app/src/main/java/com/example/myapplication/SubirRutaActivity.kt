package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

// 🔹 Datos de tu cuenta Cloudinary
private const val CLOUDINARY_CLOUD_NAME = "dof4gj5pr"
private const val CLOUDINARY_UPLOAD_PRESET = "rutas_fotos"

class SubirRutaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var gridLayout: GridLayout
    private lateinit var nombreRuta: EditText
    private lateinit var descripcionRuta: EditText
    private lateinit var subirBtn: Button
    private lateinit var cancelarBtn: Button

    // Coordenadas que vienen desde HomeFragment
    private var coordenadas: ArrayList<LatLng> = arrayListOf()

    private val pickImageLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                imageUris.add(it)
                updateGrid()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.subir_ruta)

        gridLayout = findViewById(R.id.layoutgrid)
        nombreRuta = findViewById(R.id.routename)
        descripcionRuta = findViewById(R.id.descripcion)
        subirBtn = findViewById(R.id.subirRuta)
        cancelarBtn = findViewById(R.id.cancelar)

        // Recuperar coordenadas enviadas desde el fragment
        coordenadas = intent.getSerializableExtra("coordenadas") as? ArrayList<LatLng> ?: arrayListOf()

        // Inicializar el mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        updateGrid()

        // SUBIR RUTA
        subirBtn.setOnClickListener {
            if (nombreRuta.text.isEmpty() || descripcionRuta.text.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            subirRuta()
        }

        // CANCELAR
        cancelarBtn.setOnClickListener {
            cancelarRuta()
        }
    }

    // --- Mostrar la ruta en el mapa ---
    override fun onMapReady(googleMap: GoogleMap) {
        if (coordenadas.isEmpty()) {
            Toast.makeText(this, "No se recibieron coordenadas", Toast.LENGTH_SHORT).show()
            return
        }

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(coordenadas)
                .color(android.graphics.Color.BLUE)
                .width(5f)
        )

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordenadas.first(), 15f))
    }

    // --- Grid de imágenes ---
    private fun updateGrid() {
        gridLayout.removeAllViews()

        for (uri in imageUris) {
            val imageView = ImageView(this).apply {
                setImageURI(uri)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 200
                    height = 200
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            gridLayout.addView(imageView)
        }

        val addButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 200
                height = 200
            }
            setOnClickListener { openGallery() }
        }
        gridLayout.addView(addButton)
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    // 🔹 Subir UNA imagen a Cloudinary y devolver la URL (o null si falla)
    private suspend fun uploadImageToCloudinary(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

                // Copiamos el contenido a un archivo temporal
                val tempFile = File.createTempFile("upload_", ".jpg", cacheDir)
                FileOutputStream(tempFile).use { out ->
                    inputStream.use { it.copyTo(out) }
                }

                val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData(
                    "file",
                    tempFile.name,
                    requestFile
                )

                val presetBody: RequestBody =
                    CLOUDINARY_UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = CloudinaryService.api.uploadImage(
                    CLOUDINARY_CLOUD_NAME,
                    body,
                    presetBody
                )

                response.secure_url
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // 🔹 Subir ruta + imágenes (Cloudinary + Firestore)
    private fun subirRuta() {
        val nombre = nombreRuta.text.toString().trim()
        val descripcion = descripcionRuta.text.toString().trim()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"

        val firestore = FirebaseFirestore.getInstance()

        // Preparar coordenadas para Firestore
        val coordList = coordenadas.map {
            mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }

        val rutaData = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "userId" to userId,
            "imagenes" to emptyList<String>(),
            "rating" to 0,
            "coordenadas" to coordList
        )

        // SIN IMÁGENES → solo guardamos la ruta
        if (imageUris.isEmpty()) {
            firestore.collection("Rutas")
                .add(rutaData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                    volverAMain()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al subir la ruta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        // CON IMÁGENES → subimos primero a Cloudinary
        lifecycleScope.launch {
            val imagenesUrls = mutableListOf<String>()

            for (uri in imageUris) {
                val url = uploadImageToCloudinary(uri)
                if (url != null) {
                    imagenesUrls.add(url)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SubirRutaActivity,
                            "Error subiendo una imagen",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            rutaData["imagenes"] = imagenesUrls

            firestore.collection("Rutas")
                .add(rutaData)
                .addOnSuccessListener {
                    Toast.makeText(this@SubirRutaActivity, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                    volverAMain()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@SubirRutaActivity, "Error al subir la ruta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun cancelarRuta() {
        Toast.makeText(this, "Ruta cancelada", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun volverAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
