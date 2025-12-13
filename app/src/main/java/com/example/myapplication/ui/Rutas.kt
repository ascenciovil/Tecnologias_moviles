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
import com.google.firebase.firestore.Query

class RutasFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private val rutasList = mutableListOf<Ruta>()
    private lateinit var adapter: RutasAdapter

    data class Ruta(
        val nombre: String = "",
        val autor: String = "",
        val autorId: String = "",
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
            val intent = Intent(requireContext(), VistaRuta::class.java)
            intent.putExtra("ruta_id", ruta.idRuta)
            intent.putExtra("autor_id", ruta.autorId)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        cargarRutas()
        return view
    }

    private fun cargarRutas() {
        db.collection("Rutas")
            .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->

                android.util.Log.d("RUTAS_UI", "docs=${result.size()}")

                rutasList.clear()
                val docs = result.documents

                // filtramos visible en memoria:
                val visibles = docs.filter { doc ->
                    val v = doc.getBoolean("visible")
                    v != false // ✅ si es null (no existe) lo consideramos visible
                }

                val total = visibles.size
                var completados = 0

                if (total == 0) {
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                for (doc in visibles) {
                    val nombre = doc.getString("nombre") ?: "Sin nombre"
                    val ratingNumber = doc.get("rating") as? Number
                    val rating = ratingNumber?.toDouble() ?: 0.0
                    val idUsuario = doc.getString("userId") ?: ""
                    val idRuta = doc.id

                    if (idUsuario.isNotEmpty()) {
                        db.collection("Usuarios").document(idUsuario).get()
                            .addOnSuccessListener { userDoc ->
                                val autor = userDoc.getString("nombre_usuario") ?: "Desconocido"
                                rutasList.add(
                                    Ruta(
                                        nombre = nombre,
                                        autor = autor,
                                        autorId = idUsuario,
                                        rating = rating,
                                        idRuta = idRuta
                                    )
                                )
                                completados++
                                if (completados == total) {
                                    // ya viene ordenado por rating, pero lo mantenemos por seguridad
                                    rutasList.sortByDescending { it.rating }
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener {
                                rutasList.add(
                                    Ruta(
                                        nombre = nombre,
                                        autor = "Desconocido",
                                        autorId = idUsuario,
                                        rating = rating,
                                        idRuta = idRuta
                                    )
                                )
                                completados++
                                if (completados == total) {
                                    rutasList.sortByDescending { it.rating }
                                    adapter.notifyDataSetChanged()
                                }
                            }
                    } else {
                        rutasList.add(
                            Ruta(
                                nombre = nombre,
                                autor = "Desconocido",
                                autorId = "",
                                rating = rating,
                                idRuta = idRuta
                            )
                        )
                        completados++
                        if (completados == total) {
                            rutasList.sortByDescending { it.rating }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RUTAS_UI", "Error al cargar rutas", e)
                Toast.makeText(requireContext(), "Error al cargar rutas: ${e.message}", Toast.LENGTH_SHORT).show()
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
