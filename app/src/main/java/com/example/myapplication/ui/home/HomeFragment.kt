package com.example.myapplication.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
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

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var recording = false
    private val rutaCoords = mutableListOf<LatLng>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Botón de iniciar/detener ruta
        binding.btnRuta.setOnClickListener {
            if (!recording) startRecording() else stopRecordingAndGoToSubirRuta()
        }

        // Configuración de la barra de búsqueda
        setupSearchBar()

        return binding.root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getCurrentLocation()
    }

    // Ubicación actual
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                mMap.addMarker(MarkerOptions().position(currentLatLng).title("Mi ubicación"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            }
        }
    }

    // Buscar ubicación por nombre
    private fun setupSearchBar() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val locationName = binding.searchInput.text.toString()
                if (locationName.isNotEmpty()) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocationName(locationName, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val latLng = LatLng(addresses[0].latitude, addresses[0].longitude)
                            mMap.clear()
                            mMap.addMarker(MarkerOptions().position(latLng).title(locationName))
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        } else {
                            Toast.makeText(requireContext(), "No se encontró la ubicación", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error al buscar ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            } else {
                false
            }
        }
    }

    // Iniciar grabación de ruta
    private fun startRecording() {
        binding.searchBar.visibility = View.GONE
        recording = true
        rutaCoords.clear()
        binding.btnRuta.text = "Detener ruta"
        Toast.makeText(requireContext(), "Grabando ruta...", Toast.LENGTH_SHORT).show()

        val locationRequest = LocationRequest.create().apply {
            interval = 4000
            fastestInterval = 2000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (recording) {
                    for (location in result.locations) {
                        val point = LatLng(location.latitude, location.longitude)
                        rutaCoords.add(point)

                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    // Detener y enviar a SubirRutaActivity
    private fun stopRecordingAndGoToSubirRuta() {
        binding.searchBar.visibility = View.VISIBLE
        recording = false
        binding.btnRuta.text = "Empezar ruta"
        fusedLocationClient.removeLocationUpdates(locationCallback)

        if (rutaCoords.isEmpty()) {
            Toast.makeText(requireContext(), "No se registraron coordenadas", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Ruta finalizada", Toast.LENGTH_SHORT).show()
        val intent = Intent(requireContext(), SubirRutaActivity::class.java)
        intent.putExtra("coordenadas", ArrayList(rutaCoords))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
