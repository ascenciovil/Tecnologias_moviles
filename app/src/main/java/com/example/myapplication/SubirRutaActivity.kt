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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import com.example.myapplication.FotoConCoordenada


private const val CLOUDINARY_CLOUD_NAME = "dof4gj5pr"
private const val CLOUDINARY_UPLOAD_PRESET = "rutas_fotos"

class SubirRutaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var gridLayout: GridLayout
    private lateinit var nombreRuta: EditText
    private lateinit var descripcionRuta: EditText
    private lateinit var subirBtn: Button
    private lateinit var cancelarBtn: Button

    private var coordenadas: ArrayList<LatLng> = arrayListOf()

    // ----------------------
    // AHORA ESTA LISTA ES LA PRINCIPAL
    // Guarda fotos cámara (con coord) y galería (null coord)
    // ----------------------
    private val fotosConCoord = mutableListOf<FotoConCoordenada>()

    private val pickImageLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                // Foto desde GALERÍA → lat/lng = null
                fotosConCoord.add(
                    FotoConCoordenada(
                        uri = it.toString(),
                        lat = null,
                        lng = null
                    )
                )
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

        // Datos recibidos desde la activity anterior
        coordenadas = intent.getSerializableExtra("coordenadas") as? ArrayList<LatLng> ?: arrayListOf()

        // Fotos que ya tenían coordenadas
        val fotosPrevias = intent.getSerializableExtra("fotos") as? ArrayList<FotoConCoordenada> ?: arrayListOf()

        fotosConCoord.addAll(fotosPrevias)

        updateGrid()

        supportFragmentManager.findFragmentById(R.id.map)?.let {
            (it as SupportMapFragment).getMapAsync(this)
        }

        subirBtn.setOnClickListener {
            if (nombreRuta.text.isEmpty() || descripcionRuta.text.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            subirRuta()
        }

        cancelarBtn.setOnClickListener {
            volverAMain()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (coordenadas.isEmpty()) return

        googleMap.addPolyline(
            PolylineOptions().addAll(coordenadas)
                .color(android.graphics.Color.BLUE)
                .width(5f)
        )

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordenadas.first(), 15f))
    }

    // ------------------------------------
    // GRILLA: muestra TODAS las fotos
    // ------------------------------------
    private fun updateGrid() {
        gridLayout.removeAllViews()

        for (foto in fotosConCoord) {
            val imageView = ImageView(this).apply {
                setImageURI(Uri.parse(foto.uri))
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


    // ------------------------------------
    // SUBIR UNA FOTO A CLOUDINARY
    // ------------------------------------
    private suspend fun uploadImageToCloudinary(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

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

                val presetBody =
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


    // ------------------------------------
    // SUBIR LA RUTA COMPLETA A FIRESTORE
    // ------------------------------------
    private fun subirRuta() {
        val nombre = nombreRuta.text.toString().trim()
        val descripcion = descripcionRuta.text.toString().trim()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"

        val firestore = FirebaseFirestore.getInstance()

        val coordList = coordenadas.map {
            mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }

        lifecycleScope.launch {

            // -------------------------
            // SUBIR TODAS LAS FOTOS (con coordenadas o null)
            // -------------------------
            val fotosFinales = mutableListOf<Map<String, Any?>>()

            for (foto in fotosConCoord) {

                val url = uploadImageToCloudinary(Uri.parse(foto.uri))

                fotosFinales.add(
                    mapOf(
                        "url" to url,
                        "lat" to foto.lat,
                        "lng" to foto.lng
                    )
                )
            }

            val rutaData = hashMapOf(
                "nombre" to nombre,
                "descripcion" to descripcion,
                "userId" to userId,
                "imagenes" to fotosFinales,
                "rating" to 0,
                "coordenadas" to coordList
            )

            firestore.collection("Rutas")
                .add(rutaData)
                .addOnSuccessListener {
                    Toast.makeText(this@SubirRutaActivity, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                    volverAMain()
                }
                .addOnFailureListener {
                    Toast.makeText(this@SubirRutaActivity, "Error al subir la ruta", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun volverAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
