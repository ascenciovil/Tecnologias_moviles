package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.VistaRuta
import com.google.android.material.card.MaterialCardView
import android.widget.TextView

class RutasFragment : Fragment() {

    private val rutas = listOf(
        Ruta("Ruta del Parque Central", "5 km", "Ideal para caminar o trotar."),
        Ruta("Ruta del Río", "7 km", "Paisajes hermosos junto al río."),
        Ruta("Ruta Urbana", "3 km", "Recorrido rápido por el centro.")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rutas, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_rutas)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = RutasAdapter(rutas) { ruta ->
            // Cuando se toca una ruta, abre la activity de detalle
            val intent = Intent(requireContext(), VistaRuta::class.java)
            intent.putExtra("nombreRuta", ruta.nombre)
            startActivity(intent)
        }

        return view
    }

    data class Ruta(val nombre: String, val distancia: String, val descripcion: String)

    class RutasAdapter(
        private val rutas: List<Ruta>,
        private val onClick: (Ruta) -> Unit
    ) : RecyclerView.Adapter<RutasAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_ruta)
            val nombre: TextView = view.findViewById(R.id.tv_nombre_ruta)
            val distancia: TextView = view.findViewById(R.id.tv_distancia_ruta)
            val descripcion: TextView = view.findViewById(R.id.tv_desc_ruta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ruta, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ruta = rutas[position]
            holder.nombre.text = ruta.nombre
            holder.distancia.text = ruta.distancia
            holder.descripcion.text = ruta.descripcion
            holder.card.setOnClickListener { onClick(ruta) }
        }

        override fun getItemCount() = rutas.size
    }
}
