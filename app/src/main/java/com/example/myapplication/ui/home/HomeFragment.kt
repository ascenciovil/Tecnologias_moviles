package com.example.myapplication.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var searchInput: EditText
    private lateinit var btnRuta: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Inicializamos vistas
        searchInput = view.findViewById(R.id.search_input)
        btnRuta = view.findViewById(R.id.btn_ruta)

        // Inicializar mapa
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar barra de búsqueda
        setupSearchBar()

        // Ejemplo de acción para el botón "Empezar ruta"
        btnRuta.setOnClickListener {
            Toast.makeText(requireContext(), "Funcionalidad en desarrollo 🚶‍♂️", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getCurrentLocation()
    }

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

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                mMap.addMarker(MarkerOptions().position(currentLatLng).title("Mi ubicación"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            }
        }
    }

    private fun setupSearchBar() {
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val locationName = searchInput.text.toString()
                if (locationName.isNotEmpty()) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocationName(locationName, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            mMap.clear()
                            mMap.addMarker(MarkerOptions().position(latLng).title(locationName))
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        } else {
                            Toast.makeText(requireContext(), "No se encontró la ubicación", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Error al buscar ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            } else {
                false
            }
        }
    }
}
