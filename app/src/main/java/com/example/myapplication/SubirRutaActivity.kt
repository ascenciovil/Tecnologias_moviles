package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.offline.PendingCoord
import com.example.myapplication.offline.PendingPhoto
import com.example.myapplication.offline.PendingRouteEntity
import com.example.myapplication.offline.PendingUploadDatabase
import com.example.myapplication.offline.UploadScheduler
import com.example.myapplication.ui.profile.ProfileViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SubirRutaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var gridLayout: GridLayout
    private lateinit var nombreRuta: EditText
    private lateinit var descripcionRuta: EditText
    private lateinit var subirBtn: Button
    private lateinit var cancelarBtn: Button
    private lateinit var profileViewModel: ProfileViewModel

    private var coordenadas: ArrayList<LatLng> = arrayListOf()

    // Lista principal: fotos cámara (con coord) y galería (null coord)
    private val fotosConCoord = mutableListOf<FotoConCoordenada>()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // ✅ Offline pro: copiamos a almacenamiento propio (path real)
                lifecycleScope.launch {
                    val localPath = withContext(Dispatchers.IO) { copyToPendingPhotos(it) }
                    fotosConCoord.add(
                        FotoConCoordenada(
                            uri = localPath,
                            lat = null,
                            lng = null,
                            origen = "despues"
                        )
                    )
                    updateGrid()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.subir_ruta)

        // Inicializar ViewModel para logros
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

        gridLayout = findViewById(R.id.layoutgrid)
        nombreRuta = findViewById(R.id.routename)
        descripcionRuta = findViewById(R.id.descripcion)
        subirBtn = findViewById(R.id.subirRuta)
        cancelarBtn = findViewById(R.id.cancelar)

        // ✅ Coordenadas: soporta Parcelable (ideal) y fallback Serializable (por cómo lo mandas hoy)
        coordenadas = intent.getParcelableArrayListExtra("coordenadas")
            ?: (intent.getSerializableExtra("coordenadas") as? ArrayList<LatLng> ?: arrayListOf())

        // Fotos previas (desde HomeFragment)
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
            guardarPendienteYEncolar()
        }

        cancelarBtn.setOnClickListener { volverAMain() }
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
    // (soporta http, content:// y path local)
    // ------------------------------------
    private fun updateGrid() {
        gridLayout.removeAllViews()

        for (foto in fotosConCoord) {
            val imageView = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 200
                    height = 200
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val model: Any = when {
                foto.uri.startsWith("http") -> foto.uri
                foto.uri.startsWith("content://") || foto.uri.startsWith("file://") -> Uri.parse(foto.uri)
                else -> File(foto.uri) // ✅ path local
            }

            Glide.with(this).load(model).into(imageView)
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
    // OFFLINE PRO: Copiar galería a path persistente
    // externalFilesDir/pending_photos
    // ------------------------------------
    private fun copyToPendingPhotos(src: Uri): String {
        val dir = getExternalFilesDir("pending_photos")!!
        dir.mkdirs()

        val dst = File(dir, "gal_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(src)!!.use { input ->
            dst.outputStream().use { out -> input.copyTo(out) }
        }
        return dst.absolutePath
    }

    // ------------------------------------
    // OFFLINE PRO: Guardar en Room + Encolar Worker
    // ------------------------------------
    private fun guardarPendienteYEncolar() {
        val nombre = nombreRuta.text.toString().trim()
        val descripcion = descripcionRuta.text.toString().trim()

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val coords = coordenadas.map { PendingCoord(it.latitude, it.longitude) }

        // Aseguramos que cada foto sea un path local (si quedara alguna content:// por versiones viejas)
        lifecycleScope.launch {
            val photos = withContext(Dispatchers.IO) {
                fotosConCoord.map { f ->
                    val pathLocal = when {
                        f.uri.startsWith("content://") -> copyToPendingPhotos(Uri.parse(f.uri))
                        f.uri.startsWith("file://") -> File(Uri.parse(f.uri).path ?: "").absolutePath
                        else -> f.uri
                    }

                    val origin = if (f.lat != null && f.lng != null) "ruta" else "despues"
                    PendingPhoto(
                        path = pathLocal,
                        lat = f.lat,
                        lng = f.lng,
                        origin = origin
                    )
                }
            }

            val gson = Gson()
            val entity = PendingRouteEntity(
                userId = userId,
                nombre = nombre,
                descripcion = descripcion,
                coordenadasJson = gson.toJson(coords),
                fotosJson = gson.toJson(photos),
                status = "PENDING"
            )

            withContext(Dispatchers.IO) {
                val db = PendingUploadDatabase.getInstance(this@SubirRutaActivity)
                val id = db.dao().insert(entity)

                // Encola el worker: se ejecuta cuando haya internet
                UploadScheduler.enqueue(this@SubirRutaActivity, id)
            }

            Toast.makeText(
                this@SubirRutaActivity,
                "Ruta guardada. Se subirá automáticamente cuando haya Internet ✅",
                Toast.LENGTH_LONG
            ).show()

            // Registrar logro de creación de ruta
            registrarCreacionRuta()

            volverAMain()
        }
    }

    private fun registrarCreacionRuta() {
        // Incrementar progreso de logros de creación
        profileViewModel.incrementarProgresoLogro("tocando_pasto")
        profileViewModel.incrementarProgresoLogro("cartografo")
    }

    private fun volverAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}