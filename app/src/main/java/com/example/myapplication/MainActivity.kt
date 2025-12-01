package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_activity_main)

        // Configuración de AppBar
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_profile,
                R.id.navigation_dashboard,
                R.id.navigation_rutas,
                R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // IMPORTANTE: Configurar navegación manualmente
        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    navController.navigate(R.id.navigation_home)
                    true
                }
                R.id.navigation_profile -> {
                    navController.navigate(R.id.navigation_profile)
                    true
                }
                R.id.navigation_dashboard -> {
                    navController.navigate(R.id.navigation_dashboard)
                    true
                }
                R.id.navigation_rutas -> {
                    navController.navigate(R.id.navigation_rutas)
                    true
                }
                R.id.navigation_notifications -> {
                    navController.navigate(R.id.navigation_notifications)
                    true
                }
                else -> false
            }
        }

        // También configurar el setupWithNavController para mantener consistencia
        navView.setupWithNavController(navController)

        // Manejar intent para navegar directamente al DashboardFragment
        handleDeepLink()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink()
    }

    private fun handleDeepLink() {
        val userId = intent.getStringExtra("user_id")
        val destination = intent.getStringExtra("destination")

        Log.d("MainActivity", "handleDeepLink - userId: $userId, destination: $destination")

        if (destination == "dashboard_fragment" && userId != null) {
            // Navegar al DashboardFragment
            Log.d("MainActivity", "Navigating to dashboard with user: $userId")

            // Crear bundle con los datos del usuario
            val bundle = Bundle().apply {
                putString("user_id", userId)
                putString("user_name", intent.getStringExtra("user_name"))
            }

            try {
                // IMPORTANTE: Primero seleccionar el ítem
                binding.navView.selectedItemId = R.id.navigation_dashboard

                // Luego navegar (esto puede fallar si ya está en ese fragment)
                val currentDestination = navController.currentDestination
                if (currentDestination?.id != R.id.navigation_dashboard) {
                    navController.navigate(R.id.navigation_dashboard, bundle)
                } else {
                    // Si ya está en dashboard, actualizar el fragment existente
                    val fragment = supportFragmentManager.findFragmentByTag("dashboard")
                    if (fragment is com.example.myapplication.ui.dashboard.DashboardFragment) {
                        fragment.arguments = bundle
                    }
                }

                // Limpiar los extras para que no se repita la navegación
                intent.removeExtra("destination")
                intent.removeExtra("user_id")
                intent.removeExtra("user_name")

                Log.d("MainActivity", "Navigation completed successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation error: ${e.message}", e)
            }
        }
    }
}