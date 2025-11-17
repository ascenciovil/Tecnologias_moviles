package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SubirRutaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var gridLayout: GridLayout
    private lateinit var nombreRuta: EditText
    private lateinit var descripcionRuta: EditText
    private lateinit var subirBtn: Button
    private lateinit var cancelarBtn: Button

    private val imageUris = mutableListOf<Uri>()
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

        // --- Recuperar coordenadas enviadas desde HomeFragment ---
        coordenadas = intent.getSerializableExtra("coordenadas") as? ArrayList<LatLng> ?: arrayListOf()

        // --- Inicializar el mapa ---
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

    // --- Subir ruta ---
    private fun subirRuta() {
        val nombre = nombreRuta.text.toString()
        val descripcion = descripcionRuta.text.toString()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"

        val firestore = FirebaseFirestore.getInstance()
        val imageUrls = mutableListOf<String>()

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

        // Si no hay imágenes: subir directamente
        if (imageUris.isEmpty()) {
            firestore.collection("Rutas")
                .add(rutaData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                    volverAMain()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        // --- Subida de imágenes ---
        var uploadedCount = 0
        for (uri in imageUris) {

            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                else -> "jpg"
            }

            val fileName = "Rutas/$userId/${System.currentTimeMillis()}_${uploadedCount}.$extension"
            val ref = FirebaseStorage.getInstance().reference.child(fileName)

            ref.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                        imageUrls.add(downloadUrl.toString())
                        uploadedCount++

                        if (uploadedCount == imageUris.size) {
                            rutaData["imagenes"] = imageUrls

                            firestore.collection("Rutas")
                                .add(rutaData)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                                    volverAMain()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error subiendo imagen: ${it.message}", Toast.LENGTH_SHORT).show()
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
