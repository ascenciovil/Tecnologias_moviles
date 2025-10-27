package com.example.myapplication.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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

        profileViewModel.telefono.observe(viewLifecycleOwner) { telefono ->
            binding.textTelefono.text = telefono
        }

        profileViewModel.descripcion.observe(viewLifecycleOwner) { descripcion ->
            binding.textDescripcion.text = descripcion
        }

        profileViewModel.rutasPublicadas.observe(viewLifecycleOwner) { rutas ->
            setupRutasList(rutas)
        }

        // Configurar botones
        binding.botonEditar.setOnClickListener {
            // Aquí puedes implementar la navegación para editar perfil
            profileViewModel.editarPerfil()
        }

        binding.botonConfiguracion.setOnClickListener {
            // Aquí puedes implementar la navegación a configuración
            profileViewModel.irConfiguracion()
        }
    }

    private fun setupRutasList(rutas: List<Ruta>) {
        val layoutRutas = binding.layoutRutas
        layoutRutas.removeAllViews()

        rutas.forEach { ruta ->
            val rutaLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 24.dpToPx())
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
                textSize = 16f
                setTextColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val duracionTextView = TextView(requireContext()).apply {
                text = ruta.duracion
                textSize = 14f
                setTextColor(0xFF666666.toInt())
            }

            filaSuperior.addView(tituloTextView)
            filaSuperior.addView(duracionTextView)
            rutaLayout.addView(filaSuperior)

            if (ruta.descripcion.isNotEmpty()) {
                val descripcionTextView = TextView(requireContext()).apply {
                    text = ruta.descripcion
                    textSize = 14f
                    setTextColor(0xFF666666.toInt())
                    setPadding(0, 4.dpToPx(), 0, 0)
                }
                rutaLayout.addView(descripcionTextView)
            }

            layoutRutas.addView(rutaLayout)
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