package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import com.google.android.gms.maps.model.CircleOptions


class VistaRuta : AppCompatActivity(), OnMapReadyCallback {

    // UI
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSeguir: MaterialButton
    private lateinit var btnFotos: MaterialButton
    private lateinit var btnComentarios: MaterialButton
    private lateinit var tvDescription: TextView
    private lateinit var tvDistanceLabel: TextView
    private lateinit var tvAutorRuta: TextView
    private lateinit var tvRatingRuta: TextView

    // Firebase / mapa
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var rutaId: String? = null
    private var userId: String? = null

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
            ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
                Toast.makeText(this, "Has seleccionado $rating estrellas", Toast.LENGTH_SHORT).show()
                enviarValoracion(rating.toInt())
            }
            mostrarPopup(popup, contentRoot)
        }

        // Referencias a vistas
        toolbar = findViewById(R.id.topAppBar)
        btnSeguir = findViewById(R.id.btn_seguir_ruta)
        btnFotos = findViewById(R.id.btn_ver_fotos)
        btnComentarios = findViewById(R.id.btn_ver_comentarios)
        tvDescription = findViewById(R.id.tv_description)
        tvDistanceLabel = findViewById(R.id.tv_distance_label)
        tvAutorRuta = findViewById(R.id.tv_autor_ruta)
        tvRatingRuta = findViewById(R.id.tv_rating_ruta)

        // Obtener ID de la ruta
        rutaId = intent.getStringExtra("ruta_id")
        if (rutaId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: ID de ruta no recibido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inicializar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapRuta) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Cargar datos desde Firestore
        cargarDatosRuta(rutaId!!)

        // ✅ Back: volver a lista de rutas (no al mapa/home)
        toolbar.setNavigationOnClickListener {
            val i = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("destination", "rutas_fragment") // <-- ajusta al nombre que uses en MainActivity
            }
            startActivity(i)
            finish()
        }

        btnSeguir.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra("go_to_home", true)
            i.putExtra("ruta_id", rutaId)
            i.putParcelableArrayListExtra("ruta_coords", ArrayList(coordenadasRuta))
            startActivity(i)
            finish()
        }

        btnFotos.setOnClickListener {
            if (imagenesRuta.isEmpty()) {
                Toast.makeText(this, "Esta ruta no tiene fotos", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, GaleriaRutaActivity::class.java)
                i.putExtra("imagenes_ruta", ArrayList(imagenesRuta))
                startActivity(i)
            }
        }

        btnComentarios.setOnClickListener {
            val id = rutaId
            if (id.isNullOrEmpty()) {
                Toast.makeText(this, "No se pudo obtener la ruta", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, ComentariosActivity::class.java)
                i.putExtra("ruta_id", id)
                startActivity(i)
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
                val ratingNumber = doc.get("rating") as? Number
                val rating = ratingNumber?.toDouble() ?: 0.0
                userId = doc.getString("userId") ?: ""

                coordenadasRuta = (doc.get("coordenadas") as? List<*>)
                    ?.mapNotNull { value ->
                        when (value) {
                            is com.google.firebase.firestore.GeoPoint -> LatLng(value.latitude, value.longitude)
                            is Map<*, *> -> {
                                val lat = (value["latitude"] as? Number)?.toDouble()
                                val lng = (value["longitude"] as? Number)?.toDouble()
                                if (lat != null && lng != null) LatLng(lat, lng) else null
                            }
                            else -> null
                        }
                    } ?: emptyList()

                // IMAGENES (url/lat/lng/origen)
                val imagenesField = doc.get("imagenes")
                imagenesRuta = when (imagenesField) {
                    is List<*> -> imagenesField.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val url = m["url"] as? String
                        if (url.isNullOrBlank()) return@mapNotNull null

                        val lat = (m["lat"] as? Number)?.toDouble()
                        val lng = (m["lng"] as? Number)?.toDouble()
                        val origen = m["origen"] as? String

                        FotoConCoordenada(
                            uri = url,
                            lat = lat,
                            lng = lng,
                            origen = origen
                        )
                    }
                    else -> emptyList()
                }

                toolbar.title = nombre
                tvDescription.text = descripcion
                tvRatingRuta.text = "⭐ ${String.format(Locale.getDefault(), "%.1f", rating)}"

                if (!userId.isNullOrEmpty()) {
                    db.collection("Usuarios").document(userId!!)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val autor = userDoc.getString("nombre_usuario") ?: "Autor desconocido"
                            tvAutorRuta.text = "Autor: $autor"
                            tvAutorRuta.setOnClickListener { navigateToUserProfile(userId!!, autor) }
                        }
                        .addOnFailureListener {
                            tvAutorRuta.text = "Autor: desconocido"
                        }
                } else {
                    tvAutorRuta.text = "Autor: desconocido"
                }

                actualizarDistanciaLabel()
                dibujarRutaSiLista()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar la ruta: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // ==========================
    //     PERFIL
    // ==========================
    private fun navigateToUserProfile(userId: String, userName: String) {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("user_id", userId)
            putExtra("user_name", userName)
            putExtra("destination", "dashboard_fragment")
            putExtra("force_reload", true)
        }
        startActivity(i)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // ==========================
    //     MAPA
    // ==========================
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true

        googleMap.setOnMarkerClickListener { marker ->
            val url = marker.tag as? String
            if (!url.isNullOrEmpty()) mostrarImagenEnDialog(url)
            true
        }

        dibujarRutaSiLista()
    }

    private fun dibujarRutaSiLista() {
        if (!mapReady || coordenadasRuta.isEmpty()) return

        googleMap.clear() // evita duplicados

        val latLngList = coordenadasRuta.map { LatLng(it.latitude, it.longitude) }

        // Polyline
        googleMap.addPolyline(
            PolylineOptions()
                .width(10f)
                .addAll(latLngList)
        )

        val inicio = latLngList.first()
        val fin = latLngList.last()


        // Inicio: flecha (anchor centrado, flat)
        googleMap.addCircle(
            CircleOptions()
                .center(inicio)
                .radius(9.0)
                .strokeWidth(0f)
                .fillColor(0x332ECC71) // verde transparente
                .zIndex(9f)
        )


        // Fin: bandera (anchor abajo-centro para que no quede “corrida”)
        googleMap.addMarker(
            MarkerOptions()
                .position(fin)
                .title("Fin")
                .icon(bitmapDescriptorFromVector(R.drawable.marker_finish_flag))
                .anchor(0.5f, 1.0f)
                .zIndex(6f)
        )

        // Cámara
        try {
            val boundsBuilder = LatLngBounds.Builder()
            latLngList.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (_: IllegalStateException) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(inicio, 17f))
        }

        // ✅ Fotos SOLO "ruta" con ícono de cámara
        val cameraIcon = bitmapDescriptorFromVector(R.drawable.marker_camera)

        imagenesRuta
            .filter { it.uri.isNotBlank() }
            .filter { it.lat != null && it.lng != null }
            .filter { (it.origen == "ruta") || (it.origen == null) }
            .forEach { foto ->
                val pos = LatLng(foto.lat!!, foto.lng!!)
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("Foto")
                        .icon(cameraIcon)
                        .anchor(0.5f, 0.5f)
                        .zIndex(3f)
                )
                marker?.tag = foto.uri
            }
    }


    private fun bitmapDescriptorFromVector(@DrawableRes vectorResId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, vectorResId)!!
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
            Location.distanceBetween(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
                results
            )
            totalMeters += results[0]
        }

        val km = totalMeters / 1000f
        val kmRounded = String.format(Locale.getDefault(), "%.1f", km)
        tvDistanceLabel.text = "Distancia de la ruta: $kmRounded km"
    }

    private fun mostrarPopup(popup: View, root: View) {
        root.alpha = 0.3f
        popup.visibility = View.VISIBLE
        root.isEnabled = false

        popup.bringToFront()
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
                db.collection("Rutas").document(rutaId).update("rating", promedio)
            }
    }

    private fun mostrarImagenEnDialog(url: String) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_imagen)
        dialog.setCancelable(true)

        val imageView = dialog.findViewById<ImageView>(R.id.dialogImage)
        Glide.with(this).load(url).into(imageView)

        dialog.show()
    }
}
