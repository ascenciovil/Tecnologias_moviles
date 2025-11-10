package com.example.myapplication


import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.maps.model.LatLng
import android.content.Intent

class SubirRutaActivity : AppCompatActivity(){
    private lateinit var gridLayout: GridLayout
    private lateinit var nombreRuta: EditText
    private lateinit var descripcionRuta: EditText
    private lateinit var subirBtn: Button
    private val imageUris = mutableListOf<Uri>()
    private val coordenadas = mutableListOf<LatLng>()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
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

        updateGrid()

        subirBtn.setOnClickListener {
            if (nombreRuta.text.isEmpty() || descripcionRuta.text.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            subirRuta()
        }
    }

    private fun updateGrid() {
        gridLayout.removeAllViews()
        for (uri in imageUris){
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

    private fun openGallery(){
        pickImageLauncher.launch("image/*")
    }

    private fun subirRuta() {
        val nombre = nombreRuta.text.toString()
        val descripcion = descripcionRuta.text.toString()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"

        val firestore = FirebaseFirestore.getInstance()
        val imageUrls = mutableListOf<String>()

        val rutaData = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion,
            "userId" to userId,
            "imagenes" to emptyList<String>(),
            "rating" to 0,
            "coordenadas" to emptyList<Map<String, Double>>()
        )

        // Si no hay imágenes, subir solo los datos
        if (imageUris.isEmpty()) {
            firestore.collection("Rutas")
                .add(rutaData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                    volverAMain()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al subir la ruta: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        // Subir imágenes
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
                    // Obtener la URL de descarga después de la subida exitosa
                    taskSnapshot.storage.downloadUrl
                        .addOnSuccessListener { downloadUrl ->
                            imageUrls.add(downloadUrl.toString())
                            uploadedCount++

                            // Cuando todas las imágenes estén subidas
                            if (uploadedCount == imageUris.size) {
                                rutaData["imagenes"] = imageUrls
                                firestore.collection("Rutas")
                                    .add(rutaData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Ruta subida correctamente", Toast.LENGTH_SHORT).show()
                                        volverAMain()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Error al subir la ruta: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error obteniendo URL: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error subiendo imagen: ${it.message}", Toast.LENGTH_SHORT).show()
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