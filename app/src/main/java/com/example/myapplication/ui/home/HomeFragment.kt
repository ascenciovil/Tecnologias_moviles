package com.example.myapplication.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.SubirRutaActivity
import com.example.myapplication.databinding.FragmentHomeBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale
import android.widget.TextView
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import com.example.myapplication.FotoConCoordenada
import com.example.myapplication.VistaRuta
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomnavigation.BottomNavigationView


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
    private var photoUri: Uri? = null

    private val fotosTomadas = arrayListOf<FotoConCoordenada>()

    private var currentLocation: Location? = null
  
    private var pasosInicio = -1
    private var pasosActuales = 0

    private var distanciaTotal = 0.0
    private var velocidadPromedio = 0.0
    private var tiempoInicio: Long = 0L


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && photoUri != null) {

                val ubicacionActual = currentLocation  // ← tu variable con la ubicación

                if (ubicacionActual != null) {
                    val foto = FotoConCoordenada(
                        uri = photoUri.toString(),
                        lat = ubicacionActual.latitude,
                        lng = ubicacionActual.longitude
                    )

                    fotosTomadas.add(foto)
                }
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
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return
            }

            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    val distancia = distanciaAInicioRuta(coords.first())
                    val texto = String.format(Locale.US, "Distancia al inicio: %.2f km", distancia)
                    Toast.makeText(requireContext(), texto, Toast.LENGTH_LONG).show()
                }
            }
            arguments?.clear()
        }else{
            getCurrentLocation()
        }

    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val point = LatLng(it.latitude, it.longitude)
                mMap.addMarker(MarkerOptions().position(point).title("Mi ubicación"))
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15f))
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val name = binding.searchInput.text.toString()
                if (name.isNotEmpty()) {
                    try {
                        val geo = android.location.Geocoder(requireContext(), Locale.getDefault())
                        val list = geo.getFromLocationName(name, 1)
                        if (!list.isNullOrEmpty()) {
                            val latLng = LatLng(list[0].latitude, list[0].longitude)
                            mMap.clear()
                            mMap.addMarker(MarkerOptions().position(latLng).title(name))
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        } else {
                            Toast.makeText(requireContext(), "No se encontró la ubicación", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                    }
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
        pasosActuales = 0
        pasosInicio = -1
        distanciaTotal = 0.0
        tiempoInicio = System.currentTimeMillis()

        binding.btnRuta.text = "Detener ruta"
        Toast.makeText(requireContext(), "Grabando ruta...", Toast.LENGTH_SHORT).show()

        startStepSensor()
        startGpsTracking()
    }

    private fun startGpsTracking() {
        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        binding.btnFoto.setOnClickListener {
            openCamera()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!recording) return

                for (location in result.locations) {
                    val point = LatLng(location.latitude, location.longitude)
                    currentLocation = location

                    // Añadir coordenada
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
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
        }
    }

    private fun calcularVelocidad() {
        val tiempoHoras = (System.currentTimeMillis() - tiempoInicio) / 3600000.0
        if (tiempoHoras > 0) {
            velocidadPromedio = distanciaTotal / tiempoHoras
        }
    }

    private fun startStepSensor() {
        if (stepCounterSensor == null) {
            Toast.makeText(requireContext(), "Tu dispositivo no tiene sensor de pasos", Toast.LENGTH_SHORT).show()
            return
        }
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!recording) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            if (pasosInicio == -1) pasosInicio = event.values[0].toInt()
            pasosActuales = event.values[0].toInt() - pasosInicio
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopRecordingAndGoToSubirRuta() {
        recording = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        binding.btnRuta.text = "Empezar ruta"

        if (rutaCoords.isEmpty()) {
            Toast.makeText(requireContext(), "No se registraron coordenadas", Toast.LENGTH_SHORT).show()
            return
        }

        binding.dataLayout.visibility = View.VISIBLE
        binding.btnRuta.visibility = View.GONE

        animateNumberTextView(binding.pasosText, 0, pasosActuales, " pasos") {
            requireActivity().runOnUiThread { binding.distanciaText.visibility = View.VISIBLE }
            animateDecimalTextView(binding.distanciaText, distanciaTotal, " km") {
                requireActivity().runOnUiThread { binding.velocidadText.visibility = View.VISIBLE }
                animateDecimalTextView(binding.velocidadText, velocidadPromedio, " km/h")
            }
        }

        binding.btnContinuar.setOnClickListener {
            val intent = Intent(requireContext(), SubirRutaActivity::class.java)
            intent.putExtra("coordenadas", ArrayList(rutaCoords))
            intent.putExtra("fotos", fotosTomadas)
            startActivity(intent)
        }

    }

    private fun animateNumberTextView(textView: TextView, from: Int, to: Int, suffix: String = "", onEnd: (() -> Unit)? = null) {
        Thread {
            for (i in from..to) {
                requireActivity().runOnUiThread { textView.text = "$i$suffix" }
                Thread.sleep(10)
            }
            onEnd?.invoke()
        }.start()
    }

    private fun animateDecimalTextView(textView: TextView, to: Double, suffix: String = "", onEnd: (() -> Unit)? = null) {
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
        val imageFile = File.createTempFile("photo_", ".jpg", requireContext().cacheDir)

        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            imageFile
        )

        photoUri?.let { uri ->
            takePictureLauncher.launch(uri)
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

        // Centrar la cámara
        try {
            val boundsBuilder = LatLngBounds.Builder()
            latLngList.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            val padding = 100
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap.moveCamera(cameraUpdate)
        } catch (e: IllegalStateException) {
            // Esto pasa cuando todos los puntos son exactamente iguales
            if (latLngList.isNotEmpty()) {
                mMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latLngList.first(), 17f)
                )
            }
        }
    }

    private fun distanciaAInicioRuta(primerPunto: LatLng): Double {
        val locActual = currentLocation ?: return -1.0

        val locInicio = Location("").apply {
            latitude = primerPunto.latitude
            longitude = primerPunto.longitude
        }

        val distanciaMetros = locActual.distanceTo(locInicio)
        return distanciaMetros / 1000.0 // km
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
