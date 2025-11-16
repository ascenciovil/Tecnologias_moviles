package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore

class VistaRuta : AppCompatActivity() {

    // 🔧 Referencias que necesitamos después en la carga de datos
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSeguir: MaterialButton
    private lateinit var btnFotos: MaterialButton
    private lateinit var btnComentarios: MaterialButton
    private lateinit var tvDescription: TextView
    private lateinit var imgMap: ImageView

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vista_ruta)

        // 🧭 Referencias a vistas
        toolbar = findViewById(R.id.topAppBar)
        btnSeguir = findViewById(R.id.btn_seguir_ruta)
        btnFotos = findViewById(R.id.btn_ver_fotos)
        btnComentarios = findViewById(R.id.btn_ver_comentarios)
        tvDescription = findViewById(R.id.tv_description)
        imgMap = findViewById(R.id.img_map_placeholder)

        // 🔹 Obtener ID de la ruta desde el Intent
        val rutaId = intent.getStringExtra("ruta_id")
        if (rutaId == null) {
            Toast.makeText(this, "Error: ID de ruta no recibido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 📥 Cargar datos reales desde Firestore
        cargarDatosRuta(rutaId)

        // 🔙 Acción del botón de retroceso (flecha en el top bar)
        toolbar.setNavigationOnClickListener {
            finish() // vuelve a la activity anterior
        }

        // ▶️ Botón "Seguir ruta" (placeholder)
        btnSeguir.setOnClickListener { view ->
            Snackbar.make(
                view,
                "Iniciando seguimiento (no implementado)...",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        // 🖼️ Botón "Ver fotos"
        btnFotos.setOnClickListener {
            Toast.makeText(this, "Abriendo galería (no implementado)", Toast.LENGTH_SHORT).show()
        }

        // 💬 Botón "Ver comentarios"
        btnComentarios.setOnClickListener {
            Toast.makeText(this, "Mostrando comentarios (no implementado)", Toast.LENGTH_SHORT)
                .show()
        }

        // 🗺️ Click en el mapa (por ahora muestra un Toast)
        imgMap.setOnClickListener {
            Toast.makeText(this, "Mapa ampliado (en desarrollo)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarDatosRuta(rutaId: String) {
        db.collection("Rutas").document(rutaId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "La ruta no existe", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Campos según tu esquema de Firestore
                val nombre = doc.getString("nombre") ?: "Ruta sin nombre"
                val descripcion = doc.getString("descripcion") ?: "Sin descripción"

                // Si quieres luego puedes sacar rating, coordenadas, imágenes, etc.
                // val ratingNumber = doc.get("rating") as? Number
                // val rating = ratingNumber?.toDouble() ?: 0.0
                // val coords = doc.get("coordenadas") as? List<GeoPoint> ?: emptyList()

                toolbar.title = nombre
                tvDescription.text = descripcion
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error al cargar la ruta: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
    }
}
