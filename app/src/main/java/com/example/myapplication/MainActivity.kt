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
    private var deepLinkProcessed = false

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

        // Configurar navegación manualmente
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
                    // Navegar al dashboard sin argumentos (perfil por defecto)
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

        // Resetear flag
        deepLinkProcessed = false

        // Manejar deep link SIEMPRE al crear la actividad
        handleDeepLink()
    }

    // CORRECCIÓN: Cambiar el parámetro de Intent? a Intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkProcessed = false
        handleDeepLink()
    }

    private fun handleDeepLink() {
        // Evitar procesar múltiples veces
        if (deepLinkProcessed) {
            Log.d("MainActivity", "Deep link ya procesado, ignorando")
            return
        }

        val userId = intent.getStringExtra("user_id")
        val destination = intent.getStringExtra("destination")
        val forceReload = intent.getBooleanExtra("force_reload", false)

        Log.d("MainActivity", "=== DEEP LINK DETECTADO ===")
        Log.d("MainActivity", "userId: $userId")
        Log.d("MainActivity", "destination: $destination")
        Log.d("MainActivity", "forceReload: $forceReload")
        Log.d("MainActivity", "=== ===")

        if (destination == "dashboard_fragment" && userId != null && userId.isNotEmpty()) {
            Log.d("MainActivity", "Procesando navegación al perfil de: $userId")

            try {
                // Esperar a que el navController esté listo
                binding.root.post {
                    // Crear bundle con todos los datos
                    val bundle = Bundle().apply {
                        putString("user_id", userId)
                        putString("user_name", intent.getStringExtra("user_name") ?: "")
                        putBoolean("force_reload", forceReload)
                        putBoolean("from_deep_link", true) // Para identificar que viene de deep link
                    }

                    Log.d("MainActivity", "Bundle creado: $bundle")

                    // Navegar al dashboard con los argumentos
                    // Usar popBackStack para limpiar si ya está en dashboard
                    navController.popBackStack(R.id.navigation_dashboard, false)

                    // Navegar con los argumentos
                    navController.navigate(R.id.navigation_dashboard, bundle)

                    // Seleccionar el ítem en el bottom nav
                    binding.navView.selectedItemId = R.id.navigation_dashboard

                    // Marcar como procesado
                    deepLinkProcessed = true

                    Log.d("MainActivity", "✅ Navegación completada exitosamente")

                    // Limpiar extras después de procesar (opcional)
                    clearIntentExtras()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error en navegación: ${e.message}", e)
                // Intentar de nuevo después de un delay
                binding.root.postDelayed({
                    handleDeepLink()
                }, 500)
            }
        } else {
            Log.d("MainActivity", "No hay deep link válido o userId vacío")
        }
    }

    private fun clearIntentExtras() {
        // No podemos modificar directamente los extras, pero podemos crear un nuevo intent
        val newIntent = Intent(this, MainActivity::class.java)
        setIntent(newIntent)
    }

    override fun onResume() {
        super.onResume()
        // Verificar si hay que procesar un deep link al volver a la actividad
        if (!deepLinkProcessed) {
            handleDeepLink()
        }
    }
}