package com.example.myapplication.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        dashboardViewModel.titulo.observe(viewLifecycleOwner) { titulo ->
            binding.textTitulo.text = titulo
        }

        dashboardViewModel.nombre.observe(viewLifecycleOwner) { nombre ->
            binding.textNombre.text = nombre
        }

        dashboardViewModel.descripcion.observe(viewLifecycleOwner) { descripcion ->
            binding.textDescripcion.text = descripcion
        }

        dashboardViewModel.habilidades.observe(viewLifecycleOwner) { habilidades ->
            setupHabilidadesList(habilidades)
        }
    }

    private fun setupHabilidadesList(habilidades: List<String>) {
        val layoutHabilidades = binding.layoutHabilidades
        layoutHabilidades.removeAllViews() // Limpiar vistas anteriores

        habilidades.forEach { habilidad ->
            val textView = TextView(requireContext()).apply {
                text = "• $habilidad"
                textSize = 16f
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            }
            layoutHabilidades.addView(textView)
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