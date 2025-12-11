package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isTemporaryProfile = false
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        // Obtener NavController usando findNavController (manteniendo el código de Joaquín)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // Configuración de AppBar (manteniendo configuración de Joaquín)
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

        // Configurar OnBackPressedCallback moderno (tuyo)
        setupOnBackPressedCallback(navController)

        // Configurar listener personalizado para el bottom navigation (combinado)
        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    if (isTemporaryProfile) {
                        // Si estamos en perfil temporal, volver a rutas
                        handleTemporaryProfileExit(navController)
                        navController.navigate(R.id.navigation_rutas)
                        navView.selectedItemId = R.id.navigation_rutas
                    } else {
                        navController.navigate(R.id.navigation_home)
                    }
                    true
                }
                R.id.navigation_profile -> {
                    if (isTemporaryProfile) {
                        // Si estamos en perfil temporal, limpiar primero
                        handleTemporaryProfileExit(navController)
                    }
                    navController.navigate(R.id.navigation_profile)
                    true
                }
                R.id.navigation_rutas -> {
                    if (isTemporaryProfile) {
                        handleTemporaryProfileExit(navController)
                    }
                    navController.navigate(R.id.navigation_rutas)
                    true
                }
                R.id.navigation_notifications -> {
                    if (isTemporaryProfile) {
                        handleTemporaryProfileExit(navController)
                    }
                    navController.navigate(R.id.navigation_notifications)
                    true
                }
                else -> false
            }
        }

        // Manejar navegación a home con coordenadas (código de Joaquín)
        handleHomeNavigationWithCoords(navController)

        // Resetear flag
        isTemporaryProfile = false

        // Manejar deep link inicial para perfiles (tuyo)
        handleDeepLink(navController)
    }

    private fun setupOnBackPressedCallback(navController: androidx.navigation.NavController) {
        // Configurar OnBackPressedCallback moderno
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTemporaryProfile) {
                    // Si estamos en perfil temporal, volver a rutas
                    handleTemporaryProfileExit(navController)
                    navController.navigate(R.id.navigation_rutas)
                    binding.navView.selectedItemId = R.id.navigation_rutas
                } else {
                    // Navegación normal
                    if (!navController.popBackStack()) {
                        // Si no hay nada en el stack, cerrar la app
                        finish()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isTemporaryProfile = false
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        handleDeepLink(navController)
        handleHomeNavigationWithCoords(navController)
    }

    private fun handleDeepLink(navController: androidx.navigation.NavController) {
        val userId = intent.getStringExtra("user_id")
        val destination = intent.getStringExtra("destination")
        val forceReload = intent.getBooleanExtra("force_reload", false)

        Log.d("MainActivity", "=== DEEP LINK DETECTADO ===")
        Log.d("MainActivity", "userId: $userId")
        Log.d("MainActivity", "destination: $destination")

        if (destination == "dashboard_fragment" && userId != null && userId.isNotEmpty()) {
            Log.d("MainActivity", "🚀 Procesando navegación al perfil de: $userId")

            try {
                // Usar post para asegurar que la UI está lista
                binding.root.post {
                    // Verificar si existe el destino navigation_dashboard
                    val destinationId = try {
                        // Intentar obtener el ID del destino dashboard
                        R.id.navigation_dashboard
                    } catch (e: Exception) {
                        // Si no existe, usar profile como fallback
                        Log.w("MainActivity", "navigation_dashboard no encontrado, usando profile")
                        R.id.navigation_profile
                    }

                    // Crear bundle con todos los datos
                    val bundle = Bundle().apply {
                        putString("user_id", userId)
                        putString("user_name", intent.getStringExtra("user_name") ?: "")
                        putBoolean("force_reload", forceReload)
                        putBoolean("from_deep_link", true)
                    }

                    Log.d("MainActivity", "📦 Bundle creado: $bundle")

                    // Limpiar el stack de navegación
                    navController.popBackStack(R.id.navigation_rutas, false)

                    // Navegar al destino (dashboard o profile)
                    navController.navigate(destinationId, bundle)

                    // Marcar como perfil temporal solo si vamos a dashboard
                    isTemporaryProfile = (destinationId == R.id.navigation_dashboard)

                    // Habilitar el botón de retroceso en la action bar si es temporal
                    if (isTemporaryProfile) {
                        supportActionBar?.setDisplayHomeAsUpEnabled(true)
                        onBackPressedCallback.isEnabled = true
                    }

                    Log.d("MainActivity", "✅ Navegación completada exitosamente")

                    // Limpiar extras para evitar procesamiento duplicado
                    clearIntentExtras()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error en navegación: ${e.message}", e)
                // Fallback: navegar a rutas
                navController.navigate(R.id.navigation_rutas)
                binding.navView.selectedItemId = R.id.navigation_rutas
            }
        }
    }

    // Función de la rama Joaquín para navegar a home con coordenadas
    private fun handleHomeNavigationWithCoords(navController: androidx.navigation.NavController) {
        val goToHome = intent.getBooleanExtra("go_to_home", false)

        if (goToHome) {
            val coords = intent.getParcelableArrayListExtra<LatLng>("ruta_coords")

            // Limpiar stack y navegar a home con las coordenadas
            navController.popBackStack(R.id.navigation_home, true)

            navController.navigate(R.id.navigation_home, Bundle().apply {
                putParcelableArrayList("ruta_coords", coords)
            })

            // Limpiar el flag para evitar procesamiento duplicado
            intent.removeExtra("go_to_home")
            intent.removeExtra("ruta_coords")
        }
    }

    private fun handleTemporaryProfileExit(navController: androidx.navigation.NavController) {
        if (isTemporaryProfile) {
            // Limpiar stack de navegación
            navController.popBackStack(R.id.navigation_rutas, false)

            // Restaurar estado normal
            isTemporaryProfile = false
            supportActionBar?.setDisplayHomeAsUpEnabled(false)

            Log.d("MainActivity", "🔙 Saliendo de perfil temporal")
        }
    }

    // Manejar el botón de retroceso de la action bar
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        if (isTemporaryProfile) {
            // Si estamos en perfil temporal, volver a rutas
            handleTemporaryProfileExit(navController)
            navController.navigate(R.id.navigation_rutas)
            binding.navView.selectedItemId = R.id.navigation_rutas
            return true
        }
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun clearIntentExtras() {
        // Crear un nuevo intent sin extras
        val newIntent = Intent(this, MainActivity::class.java)
        setIntent(newIntent)
    }

    override fun onResume() {
        super.onResume()
        // Verificar si hay que procesar un deep link al volver a la actividad
        if (!isTemporaryProfile) {
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            handleDeepLink(navController)
            handleHomeNavigationWithCoords(navController)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remover el callback para evitar memory leaks
        if (::onBackPressedCallback.isInitialized) {
            onBackPressedCallback.remove()
        }
    }
}