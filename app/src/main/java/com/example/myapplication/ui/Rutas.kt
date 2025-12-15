package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.widget.addTextChangedListener
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.VistaRuta
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.TextInputEditText


class RutasFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RutasAdapter

    /** Lista visible (filtrada) */
    private val rutasList = mutableListOf<Ruta>()

    /** Lista completa original */
    private val rutasOriginal = mutableListOf<Ruta>()

    private val db = FirebaseFirestore.getInstance()
    private var regionUsuario: String? = null

    data class Ruta(
        val nombre: String = "",
        val autor: String = "",
        val autorId: String = "",
        val rating: Double = 0.0,
        val idRuta: String = "",
        val region: String = ""
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

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

        val searchInput = view.findViewById<TextInputEditText>(R.id.search_rutas)

        searchInput.addTextChangedListener {
            filtrarRutas(it.toString())
        }




        regionUsuario = obtenerRegionUsuario(requireContext())

        cargarRutas()

        return view
    }

    /** Región guardada en SharedPreferences */
    private fun obtenerRegionUsuario(context: Context): String? {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_region", null)
            ?.lowercase()
            ?.trim()
    }

    /** Carga rutas desde Firestore */
    private fun cargarRutas() {
        db.collection("Rutas")
            .get()
            .addOnSuccessListener { result ->

                rutasOriginal.clear()
                rutasList.clear()

                val sameRegion = mutableListOf<Ruta>()
                val otherRegion = mutableListOf<Ruta>()

                for (doc in result.documents) {

                    val visible = doc.getBoolean("visible") != false
                    if (!visible) continue

                    val nombre = doc.getString("nombre") ?: "Sin nombre"
                    val rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0
                    val autorId = doc.getString("userId") ?: ""
                    val regionRuta = normalizarRegion(doc.getString("region") ?: "")
                    val idRuta = doc.id

                    val ruta = Ruta(
                        nombre = nombre,
                        autorId = autorId,
                        rating = rating,
                        idRuta = idRuta,
                        region = regionRuta
                    )

                    if (regionUsuario != null && regionRuta == regionUsuario) {
                        sameRegion.add(ruta)
                    } else {
                        otherRegion.add(ruta)
                    }
                }

                sameRegion.sortByDescending { it.rating }
                otherRegion.sortByDescending { it.rating }

                rutasOriginal.addAll(sameRegion)
                rutasOriginal.addAll(otherRegion)

                rutasList.addAll(rutasOriginal)

                cargarAutores()
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Error al cargar rutas",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /** Carga los nombres de los autores */
    private fun cargarAutores() {
        var completados = 0

        if (rutasOriginal.isEmpty()) {
            adapter.notifyDataSetChanged()
            return
        }

        rutasOriginal.forEachIndexed { index, ruta ->
            if (ruta.autorId.isEmpty()) {
                completados++
                if (completados == rutasOriginal.size) adapter.notifyDataSetChanged()
                return@forEachIndexed
            }

            db.collection("Usuarios").document(ruta.autorId).get()
                .addOnSuccessListener { doc ->
                    val autor = doc.getString("nombre_usuario") ?: "Desconocido"
                    rutasOriginal[index] = rutasOriginal[index].copy(autor = autor)

                    // reflejar cambio si está visible
                    rutasList.find { it.idRuta == ruta.idRuta }?.let { r ->
                        val pos = rutasList.indexOf(r)
                        rutasList[pos] = r.copy(autor = autor)
                    }

                    completados++
                    if (completados == rutasOriginal.size) adapter.notifyDataSetChanged()
                }
                .addOnFailureListener {
                    completados++
                    if (completados == rutasOriginal.size) adapter.notifyDataSetChanged()
                }
        }
    }

    /** Filtra rutas por nombre o autor */
    private fun filtrarRutas(texto: String?) {
        rutasList.clear()

        if (texto.isNullOrBlank()) {
            rutasList.addAll(rutasOriginal)
        } else {
            val filtro = texto.lowercase().trim()
            rutasList.addAll(
                rutasOriginal.filter {
                    it.nombre.lowercase().contains(filtro) ||
                            it.autor.lowercase().contains(filtro)
                }
            )
        }

        adapter.notifyDataSetChanged()
    }

    private fun normalizarRegion(region: String): String =
        region.lowercase().trim()

    // ---------------- ADAPTER ----------------

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

        override fun getItemCount(): Int = rutas.size
    }
}
