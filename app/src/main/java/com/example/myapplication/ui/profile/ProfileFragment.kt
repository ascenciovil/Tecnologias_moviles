package com.example.myapplication.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.PendingUploadsActivity
import com.example.myapplication.R
import com.example.myapplication.VistaRuta
import com.example.myapplication.databinding.FragmentProfileBinding
import com.example.myapplication.offline.PendingUploadDatabase
import com.example.myapplication.offline.UploadScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        }

        profileViewModel.siguiendoCount.observe(viewLifecycleOwner) { count ->
            binding.textSiguiendoCount.text = count.toString()
        }

        profileViewModel.mensaje.observe(viewLifecycleOwner) { mensaje ->
            mensaje?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

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

        profileViewModel.fotoPerfilUrl.observe(viewLifecycleOwner) { url ->
            cargarFotoPerfil(url)
        }

        profileViewModel.rutasPublicadas.observe(viewLifecycleOwner) { rutas ->
            setupRutasList(rutas)
        }

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

        profileViewModel.isOnline.observe(viewLifecycleOwner) { isOnline ->
            updateUIForConnectionState(isOnline)
        }

        profileViewModel.showOfflineMessage.observe(viewLifecycleOwner) { showOffline ->
            if (showOffline) {
                showOfflineMessage()
            }
        }

        binding.botonEditar.setOnClickListener {
            mostrarDialogoEditarPerfil()
        }

        binding.retryButton.setOnClickListener {
            retryConnection()
        }

        // Configurar clic en la imagen de perfil
        binding.imageFotoPerfil.setOnClickListener {
            mostrarOpcionesFotoPerfil()
        }

        // ✅ NUEVO: abrir pantalla de pendientes
        binding.cardPendingUploads.setOnClickListener {
            startActivity(Intent(requireContext(), PendingUploadsActivity::class.java))
        }

        profileViewModel.cargarDatosUsuario()

        // ✅ NUEVO: cargar contador al entrar
        refreshPendingCount()
    }

    override fun onResume() {
        super.onResume()
        // ✅ refresca contador al volver desde PendingUploadsActivity
        refreshPendingCount()
    }

    // ✅ NUEVO: contador de pendientes (PENDING/FAILED)
    private fun refreshPendingCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val db = PendingUploadDatabase.getInstance(requireContext())
                db.dao().getAllPendingOrFailed().size
            }

            binding.tvPendingUploadsCount.text = count.toString()
            binding.cardPendingUploads.visibility = if (count == 0) View.GONE else View.VISIBLE
        }
    }

    // ✅ NUEVO: al reintentar conexión, también re-encolar subidas pendientes
    private fun enqueueAllPendingUploads() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val db = PendingUploadDatabase.getInstance(requireContext())
                val pendientes = db.dao().getAllPendingOrFailed()
                pendientes.forEach { UploadScheduler.enqueue(requireContext(), it.id) }
            }
        }
    }

    private fun updateUIForConnectionState(isOnline: Boolean) {
        if (!isOnline) {
            val isLoading = profileViewModel.isLoading.value ?: true
            if (!isLoading) {
                binding.offlineIndicator.visibility = View.VISIBLE
                binding.botonEditar.alpha = 0.6f
                binding.botonEditar.isEnabled = false
                binding.imageFotoPerfil.alpha = 0.6f
                binding.imageFotoPerfil.isEnabled = false
            }
        } else {
            binding.offlineIndicator.visibility = View.GONE
            binding.botonEditar.alpha = 1.0f
            binding.botonEditar.isEnabled = true
            binding.imageFotoPerfil.alpha = 1.0f
            binding.imageFotoPerfil.isEnabled = true
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
        }
    }

    private fun retryConnection() {
        binding.retryButton.visibility = View.GONE
        binding.offlineProgressBar.visibility = View.VISIBLE

        profileViewModel.sincronizarDatos()

        // ✅ NUEVO: reintenta también las subidas pendientes
        enqueueAllPendingUploads()

        binding.root.postDelayed({
            binding.offlineProgressBar.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
            refreshPendingCount()
        }, 3000)
    }

    private fun showOfflineMessage() {
        binding.offlineIndicator.visibility = View.VISIBLE

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
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_perfil, null)
        val editNombre = dialogView.findViewById<EditText>(R.id.edit_nombre)
        val editDescripcion = dialogView.findViewById<EditText>(R.id.edit_descripcion)
        val btnCambiarFoto = dialogView.findViewById<TextView>(R.id.btn_cambiar_foto)
        val imageFotoPerfil = dialogView.findViewById<ImageView>(R.id.image_foto_perfil)

        val nombreActual = profileViewModel.nombre.value ?: ""
        val descripcionActual = profileViewModel.descripcion.value ?: ""
        val fotoActual = profileViewModel.fotoPerfilUrl.value

        editNombre.setText(nombreActual)
        editDescripcion.setText(descripcionActual)

        if (!fotoActual.isNullOrEmpty() && fotoActual != "default") {
            Glide.with(this)
                .load(fotoActual)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(imageFotoPerfil)
        }

        btnCambiarFoto.setOnClickListener {
            mostrarOpcionesFotoPerfil()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Editar Perfil")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = editNombre.text.toString().trim()
                val nuevaDescripcion = editDescripcion.text.toString().trim()

                if (nuevoNombre.isNotEmpty()) {
                    profileViewModel.actualizarPerfil(nuevoNombre, nuevaDescripcion)

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
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val isOnline = profileViewModel.isOnline.value ?: true
            if (!isOnline) {
                positiveButton.text = "Guardar (Offline)"
                positiveButton.setTextColor(0xFFFFA000.toInt())
            }
        }
    }

    private fun mostrarOpcionesFotoPerfil() {
        val opciones = arrayOf("Elegir de galería", "Eliminar foto actual")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Foto de perfil")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> seleccionarDeGaleria()
                    1 -> eliminarFotoActual()

                }
            }

            .show()
    }

    private fun seleccionarDeGaleria() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_GALLERY)
    }

    private fun tomarFoto() {
        Toast.makeText(requireContext(), "Funcionalidad de cámara por implementar", Toast.LENGTH_SHORT).show()
        seleccionarDeGaleria()
    }

    private fun eliminarFotoActual() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar foto")
            .setMessage("¿Estás seguro de que quieres eliminar tu foto de perfil?")
            .setPositiveButton("Eliminar") { _, _ ->
                profileViewModel.eliminarFotoPerfil()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun subirNuevaFotoPerfil(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            try {
                val imageUrl = profileViewModel.subirFotoACloudinary(uri, requireContext())

                if (imageUrl != null) {
                    profileViewModel.actualizarFotoPerfil(imageUrl)
                    cargarFotoPerfil(imageUrl)
                    Toast.makeText(requireContext(), "Foto actualizada correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error al subir la imagen", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun cargarFotoPerfil(url: String?) {
        if (!url.isNullOrEmpty() && url != "default") {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.imageFotoPerfil)
        } else {
            binding.imageFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_GALLERY && data != null) {
            val uri = data.data
            if (uri != null) {
                subirNuevaFotoPerfil(uri)
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
                val rutaCard = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.background_logro_card)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = 12.dpToPx()
                    }

                    val isOnline = profileViewModel.isOnline.value ?: true
                    if (!isOnline) alpha = 0.8f
                }

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

                val filaInferior = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 12.dpToPx()
                    }
                }

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
                    ).apply { marginEnd = 8.dpToPx() }
                    isClickable = true

                    setOnClickListener {
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

    private fun mostrarDialogoConfirmacionOcultar(ruta: com.example.myapplication.ui.profile.Ruta) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ocultar ruta")
            .setMessage(
                "¿Estás seguro de que quieres ocultar la ruta '${ruta.titulo}'?\n\n" +
                        "La ruta no se eliminará permanentemente, solo dejará de ser visible en tu perfil."
            )
            .setPositiveButton("Ocultar") { _, _ ->
                profileViewModel.ocultarRuta(ruta.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

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

    private fun createLogroCard(logro: Logro, index: Int): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = 8.dpToPx()
            }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.background_logro_card)

            val isOnline = profileViewModel.isOnline.value ?: true
            if (!isOnline && !logro.obtenido) alpha = 0.7f

            val iconoTextView = TextView(requireContext()).apply {
                text = logro.icono
                textSize = 24f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 16.dpToPx() }
            }

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

            val estadoTextView = TextView(requireContext()).apply {
                text = if (logro.obtenido) "✓" else "🔒"
                textSize = 18f
                setTextColor(if (logro.obtenido) 0xFF4285F4.toInt() else 0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 16.dpToPx() }
            }

            if (!logro.obtenido) alpha = 0.6f

            addView(iconoTextView)
            addView(infoLayout)
            addView(estadoTextView)
        }
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_GALLERY = 1001
    }
}
