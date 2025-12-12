package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.VistaRuta
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore

class RutasFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private val rutasList = mutableListOf<Ruta>()
    private lateinit var adapter: RutasAdapter

    // Modelo para mostrar la tarjeta
    data class Ruta(
        val nombre: String = "",
        val autor: String = "",
        val rating: Double = 0.0,
        val idRuta: String = ""
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rutas, container, false)

        recyclerView = view.findViewById(R.id.recycler_rutas)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RutasAdapter(rutasList) { ruta ->
            // Al hacer click, abrimos VistaRuta pasando el ID del documento
            val intent = Intent(requireContext(), VistaRuta::class.java)
            intent.putExtra("ruta_id", ruta.idRuta)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        cargarRutas()

        return view
    }

    private fun cargarRutas() {
        db.collection("Rutas").get()
            .addOnSuccessListener { result ->

                rutasList.clear()
                val total = result.size()
                var completados = 0

                if (total == 0) {
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                for (doc in result) {
                    val nombre = doc.getString("nombre") ?: "Sin nombre"
                    val ratingNumber = doc.get("rating") as? Number
                    val rating = ratingNumber?.toDouble() ?: 0.0
                    val idUsuario = doc.getString("userId") ?: ""
                    val idRuta = doc.id

                    if (idUsuario.isNotEmpty()) {
                        db.collection("Usuarios").document(idUsuario).get()
                            .addOnSuccessListener { userDoc ->
                                val autor = userDoc.getString("nombre_usuario") ?: "Desconocido"

                                rutasList.add(Ruta(nombre, autor, rating, idRuta))
                                completados++

                                // Cuando todo termine → ordenar y refrescar
                                if (completados == total) {
                                    rutasList.sortByDescending { it.rating }
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener {
                                rutasList.add(Ruta(nombre, "Desconocido", rating, idRuta))
                                completados++
                                if (completados == total) {
                                    rutasList.sortByDescending { it.rating }
                                    adapter.notifyDataSetChanged()
                                }
                            }
                    } else {
                        rutasList.add(Ruta(nombre, "Desconocido", rating, idRuta))
                        completados++
                        if (completados == total) {
                            rutasList.sortByDescending { it.rating }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al cargar rutas: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    class RutasAdapter(
        private val rutas: List<Ruta>,
        private val onClick: (Ruta) -> Unit
    ) : RecyclerView.Adapter<RutasAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_ruta)
            val nombre: TextView = view.findViewById(R.id.tv_nombre_ruta)
            val autor: TextView = view.findViewById(R.id.tv_autor_ruta)
            val rating: TextView = view.findViewById(R.id.tv_rating_ruta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ruta, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ruta = rutas[position]
            holder.nombre.text = ruta.nombre
            holder.autor.text = "Por: ${ruta.autor}"
            holder.rating.text = "⭐ ${ruta.rating}"
            holder.card.setOnClickListener { onClick(ruta) }
        }

        override fun getItemCount() = rutas.size
    }
}
