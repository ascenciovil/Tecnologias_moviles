package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var isTemporaryProfile = false
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        // Obtener NavHostFragment y NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

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

        // Configurar el back button en la action bar para navegación
        setupActionBarWithNavController(navController)

        // Configurar navegación del bottom navigation
        navView.setupWithNavController(navController)

        // Configurar OnBackPressedCallback moderno
        setupOnBackPressedCallback()

        // Configurar listener personalizado para el bottom navigation
        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    if (isTemporaryProfile) {
                        // Si estamos en perfil temporal, limpiar stack y navegar a rutas
                        handleTemporaryProfileExit()
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
                        handleTemporaryProfileExit()
                    }
                    navController.navigate(R.id.navigation_profile)
                    true
                }
                R.id.navigation_dashboard -> {
                    if (!isTemporaryProfile) {
                        navController.navigate(R.id.navigation_dashboard)
                    } else {
                        // Mantener seleccionado el ítem actual
                        return@setOnItemSelectedListener false
                    }
                    true
                }
                R.id.navigation_rutas -> {
                    if (isTemporaryProfile) {
                        handleTemporaryProfileExit()
                    }
                    navController.navigate(R.id.navigation_rutas)
                    true
                }
                R.id.navigation_notifications -> {
                    if (isTemporaryProfile) {
                        handleTemporaryProfileExit()
                    }
                    navController.navigate(R.id.navigation_notifications)
                    true
                }
                else -> false
            }
        }

        // Resetear flag
        isTemporaryProfile = false

        // Manejar deep link inicial
        handleDeepLink()
    }

    private fun setupOnBackPressedCallback() {
        // Configurar OnBackPressedCallback moderno
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTemporaryProfile) {
                    // Si estamos en perfil temporal, volver a rutas
                    handleTemporaryProfileExit()
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
        handleDeepLink()
    }

    private fun handleDeepLink() {
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

                    // Navegar al dashboard con los argumentos
                    navController.navigate(R.id.navigation_dashboard, bundle)

                    // Marcar como perfil temporal
                    isTemporaryProfile = true

                    // Habilitar el botón de retroceso en la action bar
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)

                    // Actualizar el callback de back pressed
                    onBackPressedCallback.isEnabled = true

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

    private fun handleTemporaryProfileExit() {
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
        if (isTemporaryProfile) {
            // Si estamos en perfil temporal, volver a rutas
            handleTemporaryProfileExit()
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
            handleDeepLink()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remover el callback para evitar memory leaks
        onBackPressedCallback.remove()
    }
}