package com.example.myapplication

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.offline.PendingUploadDatabase
import com.example.myapplication.offline.UploadScheduler
import com.example.myapplication.ui.profile.ProfileViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isTemporaryProfile = false
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var prefs: SharedPreferences
    private lateinit var profileViewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar SharedPreferences y ViewModel
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

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

        setupOnBackPressedCallback(navController)

        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    if (isTemporaryProfile) {
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

        handleHomeNavigationWithCoords(navController)
        isTemporaryProfile = false
        handleDeepLink(navController)

        // Verificar y registrar primer inicio para logros
        verificarPrimerInicio()

        // al abrir la app, re-encola cualquier subida pendiente/failed
        kickPendingUploads()
    }

    // Verificar si es el primer inicio para logro de bienvenida
    private fun verificarPrimerInicio() {
        val firstLaunch = prefs.getBoolean("first_launch", true)

        Log.d("MainActivity", "Primer inicio: $firstLaunch")

        if (firstLaunch) {
            // Marcar que ya no es el primer inicio
            prefs.edit().putBoolean("first_launch", false).apply()

            Log.d("MainActivity", " Es el primer inicio de la app")

            // El logro "Bienvenido" se desbloqueará automáticamente cuando el usuario
            // cargue su perfil por primera vez en ProfileViewModel
            // No necesitamos hacer nada más aquí, ya que el ViewModel maneja esto
        }
    }

    //re-encolar pendientes guardados en Room
    private fun kickPendingUploads() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = PendingUploadDatabase.getInstance(this@MainActivity)
                val pendientes = db.dao().getAllPendingOrFailed()
                pendientes.forEach { UploadScheduler.enqueue(this@MainActivity, it.id) }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error re-encolando pendientes: ${e.message}", e)
            }
        }
    }

    private fun setupOnBackPressedCallback(navController: androidx.navigation.NavController) {
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTemporaryProfile) {
                    handleTemporaryProfileExit(navController)
                    navController.navigate(R.id.navigation_rutas)
                    binding.navView.selectedItemId = R.id.navigation_rutas
                } else {
                    if (!navController.popBackStack()) {
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

        //por si llega un intent nuevo, también re-encolamos pendientes
        kickPendingUploads()
    }

    private fun handleDeepLink(navController: androidx.navigation.NavController) {
        val userId = intent.getStringExtra("user_id")
        val destination = intent.getStringExtra("destination")
        val forceReload = intent.getBooleanExtra("force_reload", false)

        Log.d("MainActivity", "=== DEEP LINK DETECTADO ===")
        Log.d("MainActivity", "userId: $userId")
        Log.d("MainActivity", "destination: $destination")

        if (destination == "dashboard_fragment" && userId != null && userId.isNotEmpty()) {
            Log.d("MainActivity", " Procesando navegación al perfil de: $userId")

            try {
                binding.root.post {
                    val destinationId = try {
                        R.id.navigation_dashboard
                    } catch (e: Exception) {
                        Log.w("MainActivity", "navigation_dashboard no encontrado, usando profile")
                        R.id.navigation_profile
                    }

                    val bundle = Bundle().apply {
                        putString("user_id", userId)
                        putString("user_name", intent.getStringExtra("user_name") ?: "")
                        putBoolean("force_reload", forceReload)
                        putBoolean("from_deep_link", true)
                    }

                    Log.d("MainActivity", "📦 Bundle creado: $bundle")

                    navController.popBackStack(R.id.navigation_rutas, false)
                    navController.navigate(destinationId, bundle)

                    isTemporaryProfile = (destinationId == R.id.navigation_dashboard)

                    if (isTemporaryProfile) {
                        supportActionBar?.setDisplayHomeAsUpEnabled(true)
                        onBackPressedCallback.isEnabled = true
                    }

                    Log.d("MainActivity", "Navegación completada exitosamente")

                    clearIntentExtras()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error en navegación: ${e.message}", e)
                navController.navigate(R.id.navigation_rutas)
                binding.navView.selectedItemId = R.id.navigation_rutas
            }
        }
    }

    private fun handleHomeNavigationWithCoords(navController: androidx.navigation.NavController) {
        val goToHome = intent.getBooleanExtra("go_to_home", false)

        if (goToHome) {
            val coords = intent.getParcelableArrayListExtra<LatLng>("ruta_coords")
            val rutaId = intent.getStringExtra("ruta_id")

            navController.popBackStack(R.id.navigation_home, true)

            navController.navigate(R.id.navigation_home, Bundle().apply {
                putString("ruta_id", rutaId)
                putParcelableArrayList("ruta_coords", coords)
            })

            intent.removeExtra("go_to_home")
            intent.removeExtra("ruta_id")
            intent.removeExtra("ruta_coords")
        }
    }

    private fun handleTemporaryProfileExit(navController: androidx.navigation.NavController) {
        if (isTemporaryProfile) {
            navController.popBackStack(R.id.navigation_rutas, false)
            isTemporaryProfile = false
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            Log.d("MainActivity", " Saliendo de perfil temporal")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        if (isTemporaryProfile) {
            handleTemporaryProfileExit(navController)
            navController.navigate(R.id.navigation_rutas)
            binding.navView.selectedItemId = R.id.navigation_rutas
            return true
        }
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun clearIntentExtras() {
        val newIntent = Intent(this, MainActivity::class.java)
        setIntent(newIntent)
    }

    override fun onResume() {
        super.onResume()
        if (!isTemporaryProfile) {
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            handleDeepLink(navController)
            handleHomeNavigationWithCoords(navController)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::onBackPressedCallback.isInitialized) {
            onBackPressedCallback.remove()
        }
    }
}