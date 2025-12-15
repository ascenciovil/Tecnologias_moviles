package com.example.myapplication.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.myapplication.FotoConCoordenada
import com.example.myapplication.R
import com.example.myapplication.SubirRutaActivity
import com.example.myapplication.VistaRuta
import com.example.myapplication.databinding.FragmentHomeBinding
import com.google.android.gms.location.*
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class HomeFragment : Fragment(), OnMapReadyCallback, SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private var recording = false
    private val rutaCoords = mutableListOf<LatLng>()

    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var activityRecognitionLauncher: ActivityResultLauncher<String>

    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private val fotosTomadas = arrayListOf<FotoConCoordenada>()
    private var currentLocation: Location? = null

    private var pasosInicio = -1
    private var pasosActuales = 0

    private var distanciaTotal = 0.0
    private var velocidadPromedio = 0.0
    private var tiempoInicio: Long = 0L

    // Estado podómetro
    private var useRealSteps: Boolean = false
    private var stepBaselineSet: Boolean = false
    private var pedometerReadyToastShown: Boolean = false

    data class RutaMapa(
        val id: String,
        val nombre: String,
        val descripcion: String,
        val rating: Double,
        val lat: Double,
        val lng: Double
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && photoFile != null) {
                val ubicacionActual = currentLocation
                if (ubicacionActual != null) {
                    fotosTomadas.add(
                        FotoConCoordenada(
                            uri = photoFile!!.absolutePath,
                            lat = ubicacionActual.latitude,
                            lng = ubicacionActual.longitude,
                            origen = "ruta"
                        )
                    )
                } else {
                    Toast.makeText(requireContext(), "No se pudo obtener ubicación para la foto", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) openCamera()
                else Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }

        activityRecognitionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!recording) return@registerForActivityResult

                if (granted) {
                    useRealSteps = true
                    startStepSensor()
                    setPedometerBadge("🟢 Podómetro activado (esperando datos...)", true)
                } else {
                    useRealSteps = false
                    setPedometerBadge("⚠️ Sin permiso: pasos estimados", true)
                }
            }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        sensorManager = requireActivity().getSystemService(SensorManager::class.java)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        binding.btnRuta.setOnClickListener {
            if (!recording) startRecording() else stopRecordingAndGoToSubirRuta()
        }

        setupSearchBar()
        return binding.root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        googleMap.setOnMarkerClickListener { marker ->
            val ruta = marker.tag as? RutaMapa ?: return@setOnMarkerClickListener true
            mostrarPopupRuta(ruta.id, ruta.nombre, ruta.descripcion, ruta.rating)
            true
        }

        val coords = arguments?.getParcelableArrayList<LatLng>("ruta_coords")
        val rutaId = arguments?.getString("ruta_id")

        if (!coords.isNullOrEmpty()) {
            binding.btnSeguir.setOnClickListener {
                val intent = Intent(requireContext(), VistaRuta::class.java)
                intent.putExtra("ruta_id", rutaId)
                intent.putExtra("from_seguimiento", true)
                startActivity(intent)
            }

            dibujarRuta(coords)

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return
            }

            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                currentLocation = location
                if (location != null) {
                    val distancia = distanciaAInicioRuta(coords.first())
                    Toast.makeText(requireContext(),
                        String.format(Locale.US, "Distancia al inicio: %.2f km", distancia),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            arguments?.clear()
        } else {
            getCurrentLocation()
            cargarMarcadoresRutasRegion()
        }
    }

    private fun setPedometerBadge(text: String, visible: Boolean) {
        if (_binding == null) return
        requireActivity().runOnUiThread {
            binding.tvPedometerBadge.text = text
            binding.tvPedometerBadge.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLocation = it
                val point = LatLng(it.latitude, it.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15f))
                guardarRegion(location)
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val name = binding.searchInput.text.toString()
                if (name.isNotEmpty()) {
                    try {
                        val geo = Geocoder(requireContext(), Locale.getDefault())
                        val list = geo.getFromLocationName(name, 1)
                        if (!list.isNullOrEmpty()) {
                            val latLng = LatLng(list[0].latitude, list[0].longitude)
                            mMap.clear()
                            mMap.addMarker(MarkerOptions().position(latLng).title(name))
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        } else {
                            Toast.makeText(requireContext(), "No se encontró la ubicación", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {}
                }
                true
            } else false
        }
    }

    private fun startRecording() {
        binding.searchBar.visibility = View.GONE
        binding.btnFoto.visibility = View.VISIBLE
        val navView = requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)
        navView.visibility = View.GONE

        recording = true
        rutaCoords.clear()
        fotosTomadas.clear()

        distanciaTotal = 0.0
        velocidadPromedio = 0.0
        tiempoInicio = System.currentTimeMillis()

        pasosActuales = 0
        pasosInicio = -1
        useRealSteps = false
        stepBaselineSet = false
        pedometerReadyToastShown = false

        binding.btnRuta.text = "Detener ruta"
        Toast.makeText(requireContext(), "Grabando ruta...", Toast.LENGTH_SHORT).show()

        setPedometerBadge("Podómetro: verificando...", true)

        startGpsTracking()
        requestActivityPermissionAndStartSteps()
    }

    private fun requestActivityPermissionAndStartSteps() {
        if (stepCounterSensor == null) {
            useRealSteps = false
            setPedometerBadge("⚠️ Sin podómetro: pasos estimados", true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                useRealSteps = true
                startStepSensor()
                setPedometerBadge("🟢 Podómetro activado (esperando datos...)", true)
            } else {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            useRealSteps = true
            startStepSensor()
            setPedometerBadge("🟢 Podómetro activado (esperando datos...)", true)
        }
    }

    private fun startGpsTracking() {
        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        binding.btnFoto.setOnClickListener { requestCameraAndOpen() }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!recording) return

                for (location in result.locations) {
                    val point = LatLng(location.latitude, location.longitude)
                    currentLocation = location

                    if (rutaCoords.isNotEmpty()) {
                        val last = rutaCoords.last()

                        val loc1 = Location("").apply {
                            latitude = last.latitude
                            longitude = last.longitude
                        }
                        val loc2 = Location("").apply {
                            latitude = point.latitude
                            longitude = point.longitude
                        }

                        val distancia = loc1.distanceTo(loc2) / 1000.0
                        distanciaTotal += distancia
                    }

                    rutaCoords.add(point)
                }

                calcularVelocidad()
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
        }
    }

    private fun requestCameraAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }

    private fun calcularVelocidad() {
        val tiempoHoras = (System.currentTimeMillis() - tiempoInicio) / 3600000.0
        if (tiempoHoras > 0) {
            velocidadPromedio = distanciaTotal / tiempoHoras
        }
    }

    private fun startStepSensor() {
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!recording) return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val value = event.values[0].toInt()

        if (pasosInicio == -1) {
            pasosInicio = value
            stepBaselineSet = true

            if (!pedometerReadyToastShown) {
                pedometerReadyToastShown = true
                setPedometerBadge("✅ Podómetro listo (contando pasos reales)", true)
            }
            return
        }

        pasosActuales = value - pasosInicio
        if (pasosActuales < 0) pasosActuales = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopRecordingAndGoToSubirRuta() {
        recording = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)

        binding.btnRuta.text = "Empezar ruta"
        setPedometerBadge("", false)

        if (rutaCoords.isEmpty()) {
            Toast.makeText(requireContext(), "No se registraron coordenadas", Toast.LENGTH_SHORT).show()
            return
        }

        val usarPasosReales = (useRealSteps && stepBaselineSet)

        val pasosFinales = if (usarPasosReales) pasosActuales else estimarPasosPorDistancia(distanciaTotal)
        val suffixPasos = if (usarPasosReales) " pasos" else " pasos (estimados)"

        binding.dataLayout.visibility = View.VISIBLE
        binding.btnRuta.visibility = View.GONE

        requireActivity().runOnUiThread { binding.pasosText.visibility = View.VISIBLE }

        animateNumberTextView(binding.pasosText, 0, pasosFinales, suffixPasos) {
            requireActivity().runOnUiThread { binding.distanciaText.visibility = View.VISIBLE }
            animateDecimalTextView(binding.distanciaText, distanciaTotal, " km") {
                requireActivity().runOnUiThread { binding.velocidadText.visibility = View.VISIBLE }
                animateDecimalTextView(binding.velocidadText, velocidadPromedio, " km/h") {
                    requireActivity().runOnUiThread {
                        binding.resumenPasos.visibility = View.VISIBLE
                        binding.resumenPasos.text =
                            if (usarPasosReales)
                                "Resumen: Caminaste $pasosFinales pasos en total."
                            else
                                "Resumen: Caminaste aproximadamente $pasosFinales pasos (estimados por distancia)."
                    }
                }
            }
        }

        binding.btnContinuar.setOnClickListener {
            val intent = Intent(requireContext(), SubirRutaActivity::class.java)
            intent.putExtra("coordenadas", ArrayList(rutaCoords))
            intent.putExtra("fotos", fotosTomadas)
            startActivity(intent)
        }
    }

    private fun estimarPasosPorDistancia(distKm: Double): Int {
        val stepLengthMeters = 0.78
        val meters = distKm * 1000.0
        return (meters / stepLengthMeters).toInt().coerceAtLeast(0)
    }

    private fun animateNumberTextView(
        textView: TextView,
        from: Int,
        to: Int,
        suffix: String = "",
        onEnd: (() -> Unit)? = null
    ) {
        Thread {
            for (i in from..to) {
                requireActivity().runOnUiThread { textView.text = "$i$suffix" }
                Thread.sleep(10)
            }
            onEnd?.invoke()
        }.start()
    }

    private fun animateDecimalTextView(
        textView: TextView,
        to: Double,
        suffix: String = "",
        onEnd: (() -> Unit)? = null
    ) {
        val steps = 100
        val inc = to / steps
        Thread {
            for (i in 0..steps) {
                val value = i * inc
                requireActivity().runOnUiThread {
                    textView.text = String.format(Locale.US, "%.2f%s", value, suffix)
                }
                Thread.sleep(10)
            }
            onEnd?.invoke()
        }.start()
    }

    private fun openCamera() {
        try {
            val dir = requireContext().getExternalFilesDir("pending_photos") ?: requireContext().cacheDir
            if (!dir.exists()) dir.mkdirs()

            val imageFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            photoFile = imageFile

            photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                imageFile
            )

            photoUri?.let { uri -> takePictureLauncher.launch(uri) }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error abriendo cámara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dibujarRuta(coordenadas: List<LatLng>) {
        if (coordenadas.isEmpty()) return

        binding.searchBar.visibility = View.GONE
        binding.btnRuta.visibility = View.GONE
        val navView = requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)
        navView.visibility = View.GONE
        binding.btnSeguir.visibility = View.VISIBLE

        val latLngList = coordenadas.map { LatLng(it.latitude, it.longitude) }

        val polylineOptions = PolylineOptions().width(10f)
        polylineOptions.addAll(latLngList)
        mMap.addPolyline(polylineOptions)

        try {
            val boundsBuilder = LatLngBounds.Builder()
            latLngList.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (_: IllegalStateException) {
            if (latLngList.isNotEmpty()) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngList.first(), 17f))
            }
        }
    }

    private fun distanciaAInicioRuta(primerPunto: LatLng): Double {
        val locActual = currentLocation ?: return -1.0
        val locInicio = Location("").apply {
            latitude = primerPunto.latitude
            longitude = primerPunto.longitude
        }
        return locActual.distanceTo(locInicio) / 1000.0
    }

    private fun guardarRegion(location: Location) {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val result = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!result.isNullOrEmpty()) {
                val addr = result[0]
                val region = listOfNotNull(addr.locality, addr.adminArea, addr.countryName).joinToString(", ")
                prefs.edit().putString("user_region", region).apply()
            }
        } catch (_: Exception) {}
    }

    private fun obtenerRegionUsuario(): String? {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_region", null)
    }

    private fun cargarMarcadoresRutasRegion() {
        val regionUsuario = obtenerRegionUsuario() ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("Rutas")
            .whereEqualTo("region", regionUsuario)
            .whereEqualTo("visible", true)
            .get()
            .addOnSuccessListener { result ->
                var rutasCercanas = 0
                for (doc in result.documents) {
                    val nombre = doc.getString("nombre") ?: continue
                    val descripcion = doc.getString("descripcion") ?: ""
                    val rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0

                    val coords = doc.get("coordenadas") as? List<Map<String, Any>>
                    if (coords.isNullOrEmpty()) continue

                    val first = coords.first()
                    val lat = first["latitude"] as? Double ?: continue
                    val lng = first["longitude"] as? Double ?: continue

                    val ruta = RutaMapa(doc.id, nombre, descripcion, rating, lat, lng)
                    agregarMarcadorRuta(ruta)
                    rutasCercanas++
                }

                if (rutasCercanas == 0) {
                    Toast.makeText(requireContext(), "No se encontraron rutas cercanas a tu región", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun agregarMarcadorRuta(ruta: RutaMapa) {
        val icon = bitmapDescriptorFromDrawable(requireContext(), R.drawable.marker_nearby_route_walk, 42)

        val marker = mMap.addMarker(
            MarkerOptions()
                .position(LatLng(ruta.lat, ruta.lng))
                .title(ruta.nombre)
                .snippet("${ruta.descripcion}\n⭐ ${ruta.rating}")
                .icon(icon)
                .anchor(0.5f, 0.5f)
        )

        marker?.tag = ruta
    }

    private fun mostrarPopupRuta(rutaId: String, nombre: String, descripcion: String, rating: Double) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_ruta, null)

        view.findViewById<TextView>(R.id.tvNombre).text = nombre
        view.findViewById<TextView>(R.id.tvDescripcion).text = descripcion
        view.findViewById<TextView>(R.id.tvRating).text = "⭐ ${String.format("%.1f", rating)}"

        view.findViewById<MaterialButton>(R.id.btnVerMas).setOnClickListener {
            val intent = Intent(requireContext(), VistaRuta::class.java)
            intent.putExtra("ruta_id", rutaId)
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun bitmapDescriptorFromDrawable(
        context: Context,
        @DrawableRes resId: Int,
        sizeDp: Int
    ): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)!!
        val sizePx = (sizeDp * context.resources.displayMetrics.density).roundToInt()

        drawable.setBounds(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
