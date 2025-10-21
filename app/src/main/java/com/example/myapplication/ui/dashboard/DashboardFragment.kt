package com.example.myapplication.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupUI()
        return root
    }

    private fun setupUI() {
        // Observar los datos del ViewModel
        dashboardViewModel.nombre.observe(viewLifecycleOwner) { nombre ->
            binding.textNombre.text = nombre
        }

        dashboardViewModel.username.observe(viewLifecycleOwner) { username ->
            binding.textUsername.text = username
        }

        dashboardViewModel.textoBoton.observe(viewLifecycleOwner) { textoBoton ->
            binding.botonSeguir.text = textoBoton
        }

        dashboardViewModel.estaSiguiendo.observe(viewLifecycleOwner) { estaSiguiendo ->
            // Cambiar el color del botón basado en el estado
            if (estaSiguiendo) {
                binding.botonSeguir.setBackgroundColor(0xFFE0E0E0.toInt()) // Gris cuando está siguiendo
                binding.botonSeguir.setTextColor(0xFF000000.toInt()) // Texto negro
            } else {
                binding.botonSeguir.setBackgroundColor(0xFF2196F3.toInt()) // Azul cuando no está siguiendo
                binding.botonSeguir.setTextColor(0xFFFFFFFF.toInt()) // Texto blanco
            }
        }

        dashboardViewModel.descripcion.observe(viewLifecycleOwner) { descripcion ->
            binding.textDescripcion.text = descripcion
        }

        dashboardViewModel.caracteristicasRutas.observe(viewLifecycleOwner) { caracteristicas ->
            setupCaracteristicasList(caracteristicas)
        }

        dashboardViewModel.rutasPublicadas.observe(viewLifecycleOwner) { rutas ->
            setupRutasList(rutas)
        }

        // Configurar el click listener del botón
        binding.botonSeguir.setOnClickListener {
            dashboardViewModel.alternarSeguir()
        }
    }

    private fun setupCaracteristicasList(caracteristicas: List<String>) {
        val layoutCaracteristicas = binding.layoutCaracteristicas
        layoutCaracteristicas.removeAllViews()

        caracteristicas.forEach { caracteristica ->
            val textView = TextView(requireContext()).apply {
                text = "• $caracteristica"
                textSize = 14f
                setTextColor(0xFF333333.toInt())
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            }
            layoutCaracteristicas.addView(textView)
        }
    }

    private fun setupRutasList(rutas: List<Ruta>) {
        val layoutRutas = binding.layoutRutas
        layoutRutas.removeAllViews()

        rutas.forEach { ruta ->
            // Contenedor principal de la ruta
            val rutaLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 24.dpToPx())
            }

            // Fila superior con título y duración
            val filaSuperior = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Título de la ruta
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

            // Duración
            val duracionTextView = TextView(requireContext()).apply {
                text = ruta.duracion
                textSize = 14f
                setTextColor(0xFF666666.toInt())
            }

            filaSuperior.addView(tituloTextView)
            filaSuperior.addView(duracionTextView)


            rutaLayout.addView(filaSuperior)

            // Descripción de la ruta - solo si no está vacía
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