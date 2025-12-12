package com.example.myapplication

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.gms.maps.model.LatLng

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_profile,
                R.id.navigation_rutas,
                R.id.navigation_notifications
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val goToHome = intent.getBooleanExtra("go_to_home", false)

        if (goToHome) {

            val coords = intent.getParcelableArrayListExtra<LatLng>("ruta_coords")
            val rutaId = intent.getStringExtra("ruta_id")

            navController.popBackStack(R.id.navigation_home, true)

            navController.navigate(R.id.navigation_home, Bundle().apply {
                putString("ruta_id", rutaId)
                putParcelableArrayList("ruta_coords", coords)
            })

            // IMPORTANTE: limpiar extras evitando que se reejecute al volver
            intent.removeExtra("go_to_home")
            intent.removeExtra("ruta_id")
            intent.removeExtra("ruta_coords")
        }

    }
}
