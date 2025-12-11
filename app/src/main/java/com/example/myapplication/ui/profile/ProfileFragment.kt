package com.example.myapplication.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.R
import com.example.myapplication.VistaRuta
import com.example.myapplication.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var profileViewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupUI()
        return root
    }

    private fun setupUI() {
        // Observar contadores de seguidores y siguiendo
        profileViewModel.seguidoresCount.observe(viewLifecycleOwner) { count ->
            binding.textSeguidoresCount.text = count.toString()

            // Hacer clickeable para ver lista de seguidores
            binding.textSeguidoresCount.setOnClickListener {
                mostrarListaSeguidores()
            }
        }

        profileViewModel.siguiendoCount.observe(viewLifecycleOwner) { count ->
            binding.textSiguiendoCount.text = count.toString()

            // Hacer clickeable para ver lista de siguiendo
            binding.textSiguiendoCount.setOnClickListener {
                mostrarListaSiguiendo()
            }
        }

        // Observar mensajes del ViewModel (CORRECCIÓN: usar value directamente)
        profileViewModel.mensaje.observe(viewLifecycleOwner) { mensaje ->
            mensaje?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                // No podemos limpiar el mensaje así porque es LiveData
                // En su lugar, manejamos el mensaje una sola vez
            }
        }

        // Observar los datos del ViewModel
        profileViewModel.nombre.observe(viewLifecycleOwner) { nombre ->
            binding.textNombre.text = nombre
        }

        profileViewModel.username.observe(viewLifecycleOwner) { username ->
            binding.textUsername.text = username
        }

        profileViewModel.email.observe(viewLifecycleOwner) { email ->
            binding.textEmail.text = email
        }

        profileViewModel.descripcion.observe(viewLifecycleOwner) { descripcion ->
            binding.textDescripcion.text = descripcion
        }

        profileViewModel.rutasPublicadas.observe(viewLifecycleOwner) { rutas ->
            setupRutasList(rutas)
        }

        // Observar logros
        profileViewModel.logros.observe(viewLifecycleOwner) { logros ->
            setupLogrosList(logros)
        }

        profileViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
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
        profileViewModel.isOnline.observe(viewLifecycleOwner) { isOnline ->
            updateUIForConnectionState(isOnline)
        }

        profileViewModel.showOfflineMessage.observe(viewLifecycleOwner) { showOffline ->
            if (showOffline) {
                showOfflineMessage()
            }
        }

        // Configurar botones
        binding.botonEditar.setOnClickListener {
            mostrarDialogoEditarPerfil()
        }

        // Botón de reintentar conexión
        binding.retryButton.setOnClickListener {
            retryConnection()
        }

        // Hacer que los textos "Seguidores" y "Siguiendo" sean clickeables
        val seguidoresContainer = binding.textSeguidoresCount.parent as? LinearLayout
        seguidoresContainer?.let { container ->
            // También hacer clickeable el label "Seguidores" (índice 1 es el TextView del label)
            if (container.childCount > 1) {
                val seguidoresLabel = container.getChildAt(1) as? TextView
                seguidoresLabel?.setOnClickListener {
                    mostrarListaSeguidores()
                }
            }
        }

        val siguiendoContainer = binding.textSiguiendoCount.parent as? LinearLayout
        siguiendoContainer?.let { container ->
            // También hacer clickeable el label "Siguiendo" (índice 1 es el TextView del label)
            if (container.childCount > 1) {
                val siguiendoLabel = container.getChildAt(1) as? TextView
                siguiendoLabel?.setOnClickListener {
                    mostrarListaSiguiendo()
                }
            }
        }

        // Cargar datos del usuario
        profileViewModel.cargarDatosUsuario()
    }

    // Funciones para mostrar listas de seguidores/siguiendo
    private fun mostrarListaSeguidores() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            Log.d("ProfileFragment", "Mostrando lista de seguidores para: $userId")
            Toast.makeText(requireContext(), "Lista de seguidores (implementar)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarListaSiguiendo() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            Log.d("ProfileFragment", "Mostrando lista de siguiendo para: $userId")
            Toast.makeText(requireContext(), "Lista de siguiendo (implementar)", Toast.LENGTH_SHORT).show()
        }
    }

    // Actualizar UI según estado de conexión
    private fun updateUIForConnectionState(isOnline: Boolean) {
        if (!isOnline) {
            // Verificar si no está cargando
            val isLoading = profileViewModel.isLoading.value ?: true
            if (!isLoading) {
                binding.offlineIndicator.visibility = View.VISIBLE
                binding.botonEditar.alpha = 0.6f
                binding.botonEditar.isEnabled = false
            }
        } else {
            binding.offlineIndicator.visibility = View.GONE
            binding.botonEditar.alpha = 1.0f
            binding.botonEditar.isEnabled = true
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
        }
    }

    // Reintentar conexión
    private fun retryConnection() {
        binding.retryButton.visibility = View.GONE
        binding.offlineProgressBar.visibility = View.VISIBLE

        profileViewModel.sincronizarDatos()

        // Ocultar el progreso después de 3 segundos si aún no hay respuesta
        binding.root.postDelayed({
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
        }, 3000)
    }

    // Mostrar mensaje offline
    private fun showOfflineMessage() {
        binding.offlineIndicator.visibility = View.VISIBLE

        // Mostrar toast informativo solo la primera vez
        val isOnline = profileViewModel.isOnline.value ?: true
        if (!isOnline) {
            Toast.makeText(
                requireContext(),
                "Modo offline activado. Los datos pueden no estar actualizados.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarDialogoEditarPerfil() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_perfil, null)
        val editNombre = dialogView.findViewById<EditText>(R.id.edit_nombre)
        val editDescripcion = dialogView.findViewById<EditText>(R.id.edit_descripcion)

        // Establecer valores actuales
        val nombreActual = profileViewModel.nombre.value ?: ""
        val descripcionActual = profileViewModel.descripcion.value ?: ""

        editNombre.setText(nombreActual)
        editDescripcion.setText(descripcionActual)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.editar_perfil))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.guardar)) { dialogInterface, which ->
                val nuevoNombre = editNombre.text.toString().trim()
                val nuevaDescripcion = editDescripcion.text.toString().trim()

                if (nuevoNombre.isNotEmpty()) {
                    profileViewModel.actualizarPerfil(nuevoNombre, nuevaDescripcion)

                    // Mostrar mensaje según el estado de conexión
                    val isOnline = profileViewModel.isOnline.value ?: true
                    if (!isOnline) {
                        Toast.makeText(
                            requireContext(),
                            "Cambios guardados localmente. Se sincronizarán cuando haya conexión.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .create()

        dialog.show()

        // Personalizar el botón positivo si estamos offline
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val isOnline = profileViewModel.isOnline.value ?: true
            if (!isOnline) {
                positiveButton.text = "Guardar (Offline)"
                positiveButton.setTextColor(0xFFFFA000.toInt()) // Color naranja
            }
        }
    }

    private fun setupRutasList(rutas: List<com.example.myapplication.ui.profile.Ruta>) {
        val layoutRutas = binding.layoutRutas
        layoutRutas.removeAllViews()

        if (rutas.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                val isOnline = profileViewModel.isOnline.value ?: true
                text = if (!isOnline) {
                    "No se pueden cargar rutas sin conexión"
                } else {
                    "No tienes ninguna ruta publicada"
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
                // Card para cada ruta - AHORA CON BOTÓN DE ELIMINAR
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

                    val isOnline = profileViewModel.isOnline.value ?: true
                    if (!isOnline) {
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

                // Fila inferior con botones de acción
                val filaInferior = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 12.dpToPx()
                    }
                }

                // Botón para ver la ruta
                val botonVer = TextView(requireContext()).apply {
                    text = "👁️ Ver ruta"
                    textSize = 14f
                    setTextColor(0xFF4285F4.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.button_outline)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginEnd = 8.dpToPx()
                    }
                    isClickable = true

                    setOnClickListener {
                        // Navegar a la VistaRuta
                        val intent = Intent(requireContext(), VistaRuta::class.java)
                        intent.putExtra("ruta_id", ruta.id)
                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        intent.putExtra("autor_id", userId)
                        startActivity(intent)
                        requireActivity().overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                    }
                }

                // Botón para ocultar la ruta
                val botonOcultar = TextView(requireContext()).apply {
                    text = "🗑️ Ocultar"
                    textSize = 14f
                    setTextColor(0xFFF44336.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.button_outline_red)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    isClickable = true

                    setOnClickListener {
                        mostrarDialogoConfirmacionOcultar(ruta)
                    }
                }

                filaInferior.addView(botonVer)
                filaInferior.addView(botonOcultar)
                rutaCard.addView(filaInferior)

                // Indicador de datos en cache
                val isOnline = profileViewModel.isOnline.value ?: true
                if (!isOnline) {
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

    // NUEVO: Mostrar diálogo de confirmación para ocultar ruta
    private fun mostrarDialogoConfirmacionOcultar(ruta: com.example.myapplication.ui.profile.Ruta) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ocultar ruta")
            .setMessage("¿Estás seguro de que quieres ocultar la ruta '${ruta.titulo}'?\n\nLa ruta no se eliminará permanentemente, solo dejará de ser visible en tu perfil.")
            .setPositiveButton("Ocultar") { dialog, which ->
                profileViewModel.ocultarRuta(ruta.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Configurar la lista de logros
    private fun setupLogrosList(logros: List<Logro>) {
        val layoutLogros = binding.layoutLogros
        layoutLogros.removeAllViews()

        if (logros.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                val isOnline = profileViewModel.isOnline.value ?: true
                text = if (!isOnline) {
                    "No se pueden cargar logros sin conexión"
                } else {
                    "Aún no tienes logros desbloqueados"
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

    // Crear tarjeta de logro individual
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

            // Indicador visual para datos offline
            val isOnline = profileViewModel.isOnline.value ?: true
            if (!isOnline && !logro.obtenido) {
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

            // Indicador de progreso offline para logros no obtenidos
            if (!isOnline && !logro.obtenido) {
                val offlineInfo = TextView(requireContext()).apply {
                    text = "Conecta para desbloquear"
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

            // Aplicar efecto de deshabilitado si no está obtenido
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