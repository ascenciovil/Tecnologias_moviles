package com.example.myapplication.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var dashboardViewModel: DashboardViewModel
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Obtener datos del usuario desde los argumentos
        val userId = arguments?.getString("user_id")
        val userName = arguments?.getString("user_name")

        Log.d("DashboardFragment", "===== FRAGMENT CREADO =====")
        Log.d("DashboardFragment", "User ID recibido: $userId")
        Log.d("DashboardFragment", "User Name recibido: $userName")

        if (userId != null && userId.isNotEmpty()) {
            // Cargar datos del usuario específico
            cargarDatosUsuario(userId, userName)
        } else {
            // Cargar datos del usuario por defecto
            Log.d("DashboardFragment", "Cargando perfil por defecto (sin userId)")
            cargarDatosPorDefecto()
        }

        return root
    }

    private fun cargarDatosUsuario(userId: String, userName: String?) {
        Log.d("DashboardFragment", "Inicializando ViewModel con userId: $userId")

        // Inicializar ViewModel con el userId
        dashboardViewModel.init(userId)

        setupObservers()

        // Cargar estado de seguimiento si hay un usuario logueado
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            dashboardViewModel.cargarEstadoSeguimiento(currentUserId, userId)
        } else {
            Log.d("DashboardFragment", "No hay usuario logueado, ocultando botón de seguir")
            binding.botonSeguir.visibility = View.GONE
        }
    }

    private fun cargarDatosPorDefecto() {
        Log.d("DashboardFragment", "Cargando datos por defecto")
        dashboardViewModel.cargarDatosPorDefecto()
        setupObservers()

        // Si es el perfil por defecto, ocultar botón de seguir
        binding.botonSeguir.visibility = View.GONE
    }

    private fun setupObservers() {
        // Observar estado de carga
        dashboardViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d("DashboardFragment", "Estado de carga: $isLoading")
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.contentLayout.visibility = View.GONE
                binding.offlineIndicator.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.contentLayout.visibility = View.VISIBLE
            }
        }

        // Observar estado de conexión
        dashboardViewModel.isOnline.observe(viewLifecycleOwner) { isOnline ->
            Log.d("DashboardFragment", "Estado de conexión: $isOnline")
            updateUIForConnectionState(isOnline)
        }

        // Observar mensajes offline
        dashboardViewModel.showOfflineMessage.observe(viewLifecycleOwner) { showOffline ->
            if (showOffline) {
                showOfflineMessage()
            }
        }

        // Observar errores
        dashboardViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e("DashboardFragment", "Error: $error")
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        // Observar los datos del usuario
        dashboardViewModel.nombre.observe(viewLifecycleOwner) { nombre ->
            Log.d("DashboardFragment", "Actualizando nombre: $nombre")
            binding.textNombre.text = nombre
        }

        dashboardViewModel.username.observe(viewLifecycleOwner) { username ->
            Log.d("DashboardFragment", "Actualizando username: $username")
            binding.textUsername.text = username
        }

        dashboardViewModel.email.observe(viewLifecycleOwner) { email ->
            Log.d("DashboardFragment", "Actualizando email: $email")
            binding.textEmail.text = email
        }

        dashboardViewModel.textoBoton.observe(viewLifecycleOwner) { textoBoton ->
            Log.d("DashboardFragment", "Actualizando texto botón: $textoBoton")
            binding.botonSeguir.text = textoBoton
        }

        dashboardViewModel.estaSiguiendo.observe(viewLifecycleOwner) { estaSiguiendo ->
            Log.d("DashboardFragment", "Estado de seguimiento: $estaSiguiendo")
            // Cambiar el color del botón basado en el estado
            if (estaSiguiendo) {
                binding.botonSeguir.setBackgroundColor(0xFFE0E0E0.toInt())
                binding.botonSeguir.setTextColor(0xFF000000.toInt())
            } else {
                binding.botonSeguir.setBackgroundColor(0xFF2196F3.toInt())
                binding.botonSeguir.setTextColor(0xFFFFFFFF.toInt())
            }
        }

        dashboardViewModel.descripcion.observe(viewLifecycleOwner) { descripcion ->
            Log.d("DashboardFragment", "Actualizando descripción")
            binding.textDescripcion.text = descripcion
        }

        dashboardViewModel.rutasPublicadas.observe(viewLifecycleOwner) { rutas ->
            Log.d("DashboardFragment", "Actualizando rutas: ${rutas.size}")
            setupRutasList(rutas)
        }

        dashboardViewModel.logros.observe(viewLifecycleOwner) { logros ->
            Log.d("DashboardFragment", "Actualizando logros: ${logros.size}")
            setupLogrosList(logros)
        }

        // Configurar el click listener del botón de seguir
        binding.botonSeguir.setOnClickListener {
            val currentUserId = auth.currentUser?.uid
            val targetUserId = arguments?.getString("user_id")

            Log.d("DashboardFragment", "Botón seguir clickeado")
            Log.d("DashboardFragment", "Usuario actual: $currentUserId")
            Log.d("DashboardFragment", "Usuario objetivo: $targetUserId")

            if (currentUserId != null && targetUserId != null) {
                dashboardViewModel.alternarSeguir(currentUserId)
            } else {
                Toast.makeText(requireContext(), "Inicia sesión para seguir usuarios", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón de reintentar conexión
        binding.retryButton.setOnClickListener {
            retryConnection()
        }
    }

    private fun updateUIForConnectionState(isOnline: Boolean) {
        if (!isOnline && !(dashboardViewModel.isLoading.value ?: true)) {
            binding.offlineIndicator.visibility = View.VISIBLE
            binding.botonSeguir.alpha = 0.6f
            binding.botonSeguir.isEnabled = false
        } else {
            binding.offlineIndicator.visibility = View.GONE
            binding.botonSeguir.alpha = 1.0f
            binding.botonSeguir.isEnabled = true
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
        }
    }

    private fun retryConnection() {
        binding.retryButton.visibility = View.GONE
        binding.offlineProgressBar.visibility = View.VISIBLE

        dashboardViewModel.sincronizarDatos()

        // Ocultar el progreso después de 3 segundos
        binding.root.postDelayed({
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
        }, 3000)
    }

    private fun showOfflineMessage() {
        binding.offlineIndicator.visibility = View.VISIBLE

        if (dashboardViewModel.isOnline.value == false) {
            Toast.makeText(
                requireContext(),
                "Modo offline activado. Los datos pueden no estar actualizados.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupRutasList(rutas: List<Ruta>) {
        val layoutRutas = binding.layoutRutas
        layoutRutas.removeAllViews()

        if (rutas.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                text = if (dashboardViewModel.isOnline.value == false) {
                    "No se pueden cargar rutas sin conexión"
                } else {
                    "Este usuario no ha publicado rutas todavía"
                }
                textSize = 15f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 24.dpToPx(), 0, 0)
                gravity = android.view.Gravity.CENTER
                setLineSpacing(1.2f, 1.2f)
            }
            layoutRutas.addView(textView)
        } else {
            rutas.forEachIndexed { index, ruta ->
                // Card para cada ruta
                val rutaCard = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.background_logro_card)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) {
                            topMargin = 12.dpToPx()
                        }
                    }

                    if (dashboardViewModel.isOnline.value == false) {
                        alpha = 0.8f
                    }
                }

                // Fila superior con título y duración
                val filaSuperior = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tituloTextView = TextView(requireContext()).apply {
                    text = ruta.titulo
                    textSize = 17f
                    setTextColor(0xFF1A1A1A.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val duracionTextView = TextView(requireContext()).apply {
                    text = ruta.duracion
                    textSize = 14f
                    setTextColor(0xFF4285F4.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }

                filaSuperior.addView(tituloTextView)
                filaSuperior.addView(duracionTextView)
                rutaCard.addView(filaSuperior)

                // Descripción
                if (ruta.descripcion.isNotEmpty()) {
                    val descripcionTextView = TextView(requireContext()).apply {
                        text = ruta.descripcion
                        textSize = 14f
                        setTextColor(0xFF666666.toInt())
                        setPadding(0, 8.dpToPx(), 0, 0)
                        setLineSpacing(1.2f, 1.2f)
                    }
                    rutaCard.addView(descripcionTextView)
                }

                // Indicador de datos en cache
                if (dashboardViewModel.isOnline.value == false) {
                    val cacheIndicator = TextView(requireContext()).apply {
                        text = "📱 Datos en cache"
                        textSize = 12f
                        setTextColor(0xFF888888.toInt())
                        setPadding(0, 8.dpToPx(), 0, 0)
                    }
                    rutaCard.addView(cacheIndicator)
                }

                layoutRutas.addView(rutaCard)
            }
        }
    }

    private fun setupLogrosList(logros: List<Logro>) {
        val layoutLogros = binding.layoutLogros
        layoutLogros.removeAllViews()

        if (logros.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                text = if (dashboardViewModel.isOnline.value == false) {
                    "No se pueden cargar logros sin conexión"
                } else {
                    "Este usuario aún no tiene logros desbloqueados"
                }
                textSize = 15f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 24.dpToPx(), 0, 0)
                gravity = android.view.Gravity.CENTER
                setLineSpacing(1.2f, 1.2f)
            }
            layoutLogros.addView(textView)
        } else {
            logros.forEachIndexed { index, logro ->
                val logroCard = createLogroCard(logro, index)
                layoutLogros.addView(logroCard)
            }
        }
    }

    private fun createLogroCard(logro: Logro, index: Int): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = 8.dpToPx()
                }
            }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.background_logro_card)

            if (dashboardViewModel.isOnline.value == false && !logro.obtenido) {
                alpha = 0.7f
            }

            // Icono del logro
            val iconoTextView = TextView(requireContext()).apply {
                text = logro.icono
                textSize = 24f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16.dpToPx()
                }
            }

            // Información del logro
            val infoLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nombreTextView = TextView(requireContext()).apply {
                text = logro.nombre
                textSize = 16f
                setTextColor(if (logro.obtenido) 0xFF1A1A1A.toInt() else 0xFF888888.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val descripcionTextView = TextView(requireContext()).apply {
                text = logro.descripcion
                textSize = 14f
                setTextColor(if (logro.obtenido) 0xFF666666.toInt() else 0xFFAAAAAA.toInt())
                setPadding(0, 4.dpToPx(), 0, 0)
            }

            if (dashboardViewModel.isOnline.value == false && !logro.obtenido) {
                val offlineInfo = TextView(requireContext()).apply {
                    text = "Conecta para ver detalles"
                    textSize = 12f
                    setTextColor(0xFF4285F4.toInt())
                    setPadding(0, 4.dpToPx(), 0, 0)
                }
                infoLayout.addView(offlineInfo)
            }

            infoLayout.addView(nombreTextView)
            infoLayout.addView(descripcionTextView)

            // Estado del logro
            val estadoTextView = TextView(requireContext()).apply {
                text = if (logro.obtenido) "✓" else "🔒"
                textSize = 18f
                setTextColor(if (logro.obtenido) 0xFF4285F4.toInt() else 0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 16.dpToPx()
                }
            }

            if (!logro.obtenido) {
                alpha = 0.6f
            }

            addView(iconoTextView)
            addView(infoLayout)
            addView(estadoTextView)
        }
    }

    // Función de extensión para convertir dp a píxeles
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}