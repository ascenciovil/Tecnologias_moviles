package com.example.myapplication.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentProfileBinding

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

        // Nuevo: Observar logros
        profileViewModel.logros.observe(viewLifecycleOwner) { logros ->
            setupLogrosList(logros)
        }

        profileViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.contentLayout.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.contentLayout.visibility = View.VISIBLE
            }
        }

        // Configurar botones
        binding.botonEditar.setOnClickListener {
            mostrarDialogoEditarPerfil()
        }

        // Cargar datos del usuario
        profileViewModel.cargarDatosUsuario()
    }

    private fun mostrarDialogoEditarPerfil() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_perfil, null)
        val editNombre = dialogView.findViewById<EditText>(R.id.edit_nombre)
        val editDescripcion = dialogView.findViewById<EditText>(R.id.edit_descripcion)

        // Establecer valores actuales
        editNombre.setText(profileViewModel.nombre.value)
        editDescripcion.setText(profileViewModel.descripcion.value)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.editar_perfil))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.guardar)) { dialogInterface, which ->
                val nuevoNombre = editNombre.text.toString().trim()
                val nuevaDescripcion = editDescripcion.text.toString().trim()

                if (nuevoNombre.isNotEmpty()) {
                    profileViewModel.actualizarPerfil(nuevoNombre, nuevaDescripcion)
                }
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .create()

        dialog.show()
    }

    private fun setupRutasList(rutas: List<Ruta>) {
        val layoutRutas = binding.layoutRutas
        layoutRutas.removeAllViews()

        if (rutas.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                text = "No tienes ninguna ruta publicada"
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

                layoutRutas.addView(rutaCard)
            }
        }
    }

    // Nuevo: Configurar la lista de logros
    private fun setupLogrosList(logros: List<Logro>) {
        val layoutLogros = binding.layoutLogros
        layoutLogros.removeAllViews()

        if (logros.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                text = "Aún no tienes logros desbloqueados"
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

    // Nuevo: Crear tarjeta de logro individual
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