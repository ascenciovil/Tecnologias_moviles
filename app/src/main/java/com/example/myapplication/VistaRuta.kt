package com.example.myapplication

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.RatingBar
import android.view.View
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
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.MarkerOptions
import android.widget.ImageView


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
    private var userId: String? = null  // Variable para almacenar el userId del autor

    private lateinit var googleMap: GoogleMap
    private var mapReady = false
    private var coordenadasRuta: List<LatLng> = emptyList()
    private var imagenesRuta: List<FotoConCoordenada> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vista_ruta)

        val fromSeguimiento = intent.getBooleanExtra("from_seguimiento", false)
        val popup = findViewById<LinearLayout>(R.id.valoracion)
        val contentRoot = findViewById<View>(R.id.route_detail_root)

        if (fromSeguimiento) {
            val ratingBar = findViewById<RatingBar>(R.id.ratingBar)
            val contentRoot = findViewById<View>(R.id.route_detail_root)


            ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
                // Aquí recibes el valor seleccionado por el usuario
                println("El usuario seleccionó: $rating estrellas")

                // Si quieres mostrar un toast:
                Toast.makeText(this, "Has seleccionado $rating estrellas", Toast.LENGTH_SHORT).show()
                enviarValoracion(rating.toInt())
            }

            mostrarPopup(popup, contentRoot)
        }


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

        btnSeguir.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("go_to_home", true)
            intent.putExtra("ruta_id", rutaId)
            intent.putParcelableArrayListExtra("ruta_coords", ArrayList(coordenadasRuta)) // ← lista de LatLng
            startActivity(intent)
            finish()
        }


        // 🖼️ Botón "Ver fotos"
        btnFotos.setOnClickListener {
            if (imagenesRuta.isEmpty()) {
                Toast.makeText(this, "Esta ruta no tiene fotos", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, GaleriaRutaActivity::class.java)
                intent.putExtra("imagenes_ruta", ArrayList(imagenesRuta))
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
                userId = doc.getString("userId") ?: ""

                // coordenadas...
                coordenadasRuta = (doc.get("coordenadas") as? List<*>)
                    ?.mapNotNull { value ->

                        when (value) {

                            // Caso GeoPoint real (no es tu caso, pero sirve)
                            is com.google.firebase.firestore.GeoPoint -> {
                                LatLng(value.latitude, value.longitude)
                            }

                            // Caso Map con latitude/longitude
                            is Map<*, *> -> {
                                val lat = (value["latitude"] as? Number)?.toDouble()
                                val lng = (value["longitude"] as? Number)?.toDouble()

                                if (lat != null && lng != null) {
                                    LatLng(lat, lng)
                                } else {
                                    Log.e("RUTA_DEBUG", "Punto inválido: $value")
                                    null
                                }
                            }

                            else -> {
                                Log.e("RUTA_DEBUG", "Formato desconocido: $value")
                                null
                            }
                        }
                    }
                    ?.also {
                        Log.d("RUTA_DEBUG", "Coordenadas mapeadas (size=${it.size}): $it")
                    }
                    ?: emptyList()



                val imagenesField = doc.get("imagenes")
                imagenesRuta = when (imagenesField) {
                    is List<*> -> imagenesField.mapNotNull { item ->
                        if (item is Map<*, *>) {
                            FotoConCoordenada(
                                uri = item["url"] as? String ?: "",
                                lat = (item["lat"] as? Number)?.toDouble(),
                                lng = (item["lng"] as? Number)?.toDouble()
                            )
                        } else null
                    }
                    else -> emptyList()
                }

                toolbar.title = nombre
                tvDescription.text = descripcion
                tvRatingRuta.text = "⭐ ${String.format(Locale.getDefault(), "%.1f", rating)}"

                if (userId!!.isNotEmpty()) {
                    db.collection("Usuarios").document(userId!!)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val autor = userDoc.getString("nombre_usuario") ?: "Autor desconocido"
                            tvAutorRuta.text = "Autor: $autor"

                            // Hacer el TextView clickeable
                            tvAutorRuta.setOnClickListener {
                                navigateToUserProfile(userId!!, autor)
                            }
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
    //     NAVEGACIÓN AL PERFIL
    // ==========================
    private fun navigateToUserProfile(userId: String, userName: String) {
        Log.d("VistaRuta", "Navigating to user profile: $userName ($userId)")

        // Crear un intent directo con CLEAR_TASK para reiniciar MainActivity completamente
        val intent = Intent(this, MainActivity::class.java).apply {
            // Esto limpia completamente el stack y crea una nueva tarea
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            // Pasar los datos del usuario
            putExtra("user_id", userId)
            putExtra("user_name", userName)
            putExtra("destination", "dashboard_fragment")
            putExtra("force_reload", true) // Bandera para forzar recarga
        }

        // Iniciar la actividad
        startActivity(intent)

        // Animación de transición suave
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

    }

    // ==========================
    //     MAPA
    // ==========================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        dibujarRutaSiLista()
        googleMap.setOnMarkerClickListener { marker ->
            val url = marker.tag as? String
            if (url != null) {
                mostrarImagenEnDialog(url)
            }
            true // ← Importante para evitar que la cámara se mueva automáticamente
        }
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

        imagenesRuta
            .filter { it.lat != null && it.lng != null }
            .forEach { foto ->
                val pos = LatLng(foto.lat!!, foto.lng!!)
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("Foto")
                )
                marker?.tag = foto.uri
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



    private fun mostrarPopup(popup: View, root: View) {
        root.alpha = 0.3f     // oscurecer fondo
        popup.visibility = View.VISIBLE
        root.isEnabled = false


        popup.bringToFront()  // lo pone sobre todo
        popup.isClickable = true
        popup.isFocusable = true

        findViewById<MaterialButton>(R.id.btn_cerrar_valoracion).setOnClickListener {
            popup.visibility = View.GONE
            root.isEnabled = true
            root.alpha = 1f
        }

    }

    private fun enviarValoracion(valor: Int) {

        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val rutaId = this.rutaId

        if (userId == null || rutaId == null) {
            Toast.makeText(this, "No se pudo enviar la valoración", Toast.LENGTH_SHORT).show()
            return
        }

        val valoracionData = mapOf(
            "valor" to valor,
            "fecha" to com.google.firebase.Timestamp.now()
        )

        db.collection("Rutas")
            .document(rutaId)
            .collection("Valoraciones")
            .document(userId)
            .set(valoracionData)
            .addOnSuccessListener {
                Toast.makeText(this, "Valoración enviada ✔️", Toast.LENGTH_SHORT).show()
                actualizarPromedio(rutaId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarPromedio(rutaId: String) {

        db.collection("Rutas")
            .document(rutaId)
            .collection("Valoraciones")
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.isEmpty) return@addOnSuccessListener

                val total = snapshot.documents.sumOf { (it.getLong("valor") ?: 0L).toDouble() }
                val promedio = total / snapshot.size().toDouble()

                db.collection("Rutas")
                    .document(rutaId)
                    .update("rating", promedio)
            }
    }

    private fun mostrarImagenEnDialog(url: String) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_imagen) // lo creamos abajo
        dialog.setCancelable(true)

        val imageView = dialog.findViewById<ImageView>(R.id.dialogImage)

        Glide.with(this)
            .load(url)
            .into(imageView)

        dialog.show()
    }


}

