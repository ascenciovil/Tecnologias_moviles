package com.example.myapplication

import android.location.Location
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.util.Locale
import android.content.Intent


class VistaRuta : AppCompatActivity(), OnMapReadyCallback {

    // 🔧 UI
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSeguir: MaterialButton
    private lateinit var btnFotos: MaterialButton
    private lateinit var btnComentarios: MaterialButton
    private lateinit var tvDescription: TextView
    private lateinit var tvDistanceLabel: TextView

    private lateinit var tvAutorRuta: TextView
    private lateinit var tvRatingRuta: TextView


    // 🔧 Firebase / mapa
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var rutaId: String? = null

    private lateinit var googleMap: GoogleMap
    private var mapReady = false
    private var coordenadasRuta: List<GeoPoint> = emptyList()
    private var imagenesRuta: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vista_ruta)

        // 🧭 Referencias a vistas
        toolbar = findViewById(R.id.topAppBar)
        btnSeguir = findViewById(R.id.btn_seguir_ruta)
        btnFotos = findViewById(R.id.btn_ver_fotos)
        btnComentarios = findViewById(R.id.btn_ver_comentarios)
        tvDescription = findViewById(R.id.tv_description)
        tvDistanceLabel = findViewById(R.id.tv_distance_label)

        tvAutorRuta = findViewById(R.id.tv_autor_ruta)
        tvRatingRuta = findViewById(R.id.tv_rating_ruta)

        // 🔹 Obtener ID de la ruta desde el Intent
        rutaId = intent.getStringExtra("ruta_id")
        if (rutaId == null) {
            Toast.makeText(this, "Error: ID de ruta no recibido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🗺️ Inicializar mapa
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapRuta) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 📥 Cargar datos reales desde Firestore
        cargarDatosRuta(rutaId!!)

        // 🔙 Botón back del toolbar
        toolbar.setNavigationOnClickListener { finish() }

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
            if (imagenesRuta.isEmpty()) {
                Toast.makeText(this, "Esta ruta no tiene fotos", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, GaleriaRutaActivity::class.java)
                intent.putStringArrayListExtra("imagenes_ruta", ArrayList(imagenesRuta))
                startActivity(intent)
            }
        }


        // 💬 Botón "Ver comentarios"
        btnComentarios.setOnClickListener {
            val id = rutaId
            if (id.isNullOrEmpty()) {
                Toast.makeText(this, "No se pudo obtener la ruta", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, ComentariosActivity::class.java)
                intent.putExtra("ruta_id", id)
                startActivity(intent)
            }
        }
    }

    // ==========================
    //     FIRESTORE
    // ==========================
    private fun cargarDatosRuta(rutaId: String) {
        db.collection("Rutas").document(rutaId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "La ruta no existe", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val nombre = doc.getString("nombre") ?: "Ruta sin nombre"
                val descripcion = doc.getString("descripcion") ?: "Sin descripción"

                // rating: puede venir como Long o Double
                val ratingNumber = doc.get("rating") as? Number
                val rating = ratingNumber?.toDouble() ?: 0.0

                // userId para buscar el autor
                val userId = doc.getString("userId") ?: ""

                // coordenadas...
                coordenadasRuta = (doc.get("coordenadas") as? List<*>)
                    ?.mapNotNull { value ->
                        when (value) {
                            is GeoPoint -> value
                            is Map<*, *> -> {
                                val lat = (value["latitude"] as? Number)?.toDouble()
                                val lng = (value["longitude"] as? Number)?.toDouble()
                                if (lat != null && lng != null) GeoPoint(lat, lng) else null
                            }
                            else -> null
                        }
                    } ?: emptyList()


                // imágenes: List<String> (si las guardaste así)
                imagenesRuta = (doc.get("imagenes") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                toolbar.title = nombre
                tvDescription.text = descripcion

                tvRatingRuta.text = "⭐ ${String.format(Locale.getDefault(), "%.1f", rating)}"

                if (userId.isNotEmpty()) {
                    db.collection("Usuarios").document(userId)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val autor = userDoc.getString("nombre_usuario") ?: "Autor desconocido"
                            tvAutorRuta.text = "Autor: $autor"
                        }
                        .addOnFailureListener {
                            tvAutorRuta.text = "Autor: desconocido"
                        }
                } else {
                    tvAutorRuta.text = "Autor: desconocido"
                }

                // Actualizar etiqueta de distancia
                actualizarDistanciaLabel()

                // Intentar dibujar la ruta si ya tenemos el mapa listo
                dibujarRutaSiLista()
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

    // ==========================
    //     MAPA
    // ==========================
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        dibujarRutaSiLista()
    }

    private fun dibujarRutaSiLista() {
        if (!mapReady || coordenadasRuta.isEmpty()) return

        val latLngList = coordenadasRuta.map { LatLng(it.latitude, it.longitude) }

        val polylineOptions = PolylineOptions().width(10f)
        polylineOptions.addAll(latLngList)
        googleMap.addPolyline(polylineOptions)

        // Centrar la cámara
        try {
            val boundsBuilder = LatLngBounds.Builder()
            latLngList.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            val padding = 100
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            googleMap.moveCamera(cameraUpdate)
        } catch (e: IllegalStateException) {
            // Esto pasa cuando todos los puntos son exactamente iguales
            if (latLngList.isNotEmpty()) {
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latLngList.first(), 17f)
                )
            }
        }
    }

    private fun actualizarDistanciaLabel() {
        if (coordenadasRuta.size < 2) {
            tvDistanceLabel.text = "Distancia de la ruta no disponible"
            return
        }

        var totalMeters = 0f

        for (i in 0 until coordenadasRuta.size - 1) {
            val p1 = coordenadasRuta[i]
            val p2 = coordenadasRuta[i + 1]

            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
                results
            )
            totalMeters += results[0]
        }

        val km = totalMeters / 1000f
        val kmRounded = String.format(java.util.Locale.getDefault(), "%.1f", km)

        tvDistanceLabel.text = "Distancia de la ruta: $kmRounded km"
    }

}
