package com.example.myapplication.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.card.MaterialCardView
import android.widget.TextView

class NotificationsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val rutasUsuario = mutableListOf<RutaUsuario>()
    private lateinit var adapter: RutasUsuarioAdapter
    private lateinit var recyclerView: RecyclerView

    data class RutaUsuario(
        val nombre: String = "",
        val comentarios: Int = 0,
        val idRuta: String = ""
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        recyclerView = view.findViewById(R.id.recycler_rutas_usuario)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RutasUsuarioAdapter(rutasUsuario)
        recyclerView.adapter = adapter

        cargarRutasUsuario()

        return view
    }

    private fun cargarRutasUsuario() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(requireContext(), "No estás logueado", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Rutas")
            .whereEqualTo("userId", userId)
            .whereEqualTo("visible", true)
            .get()
            .addOnSuccessListener { result ->
                rutasUsuario.clear()

                for (doc in result) {
                    val idRuta = doc.id
                    val nombre = doc.getString("nombre") ?: "Sin nombre"

                    // AHORA SE CONSULTA LA SUBCOLECCIÓN CORRECTA:
                    db.collection("Rutas")
                        .document(idRuta)
                        .collection("comentarios")
                        .get()
                        .addOnSuccessListener { comentariosSnapshot ->
                            val cantidadComentarios = comentariosSnapshot.size()

                            rutasUsuario.add(
                                RutaUsuario(
                                    nombre = nombre,
                                    comentarios = cantidadComentarios,
                                    idRuta = idRuta
                                )
                            )

                            adapter.notifyDataSetChanged()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error cargando rutas", Toast.LENGTH_SHORT).show()
            }
    }

    class RutasUsuarioAdapter(
        private val rutas: List<RutaUsuario>
    ) : RecyclerView.Adapter<RutasUsuarioAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_ruta_usuario)
            val nombre: TextView = view.findViewById(R.id.tv_nombre_ruta_usuario)
            val comentarios: TextView = view.findViewById(R.id.tv_comentarios_ruta_usuario)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ruta_usuario, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ruta = rutas[position]
            holder.nombre.text = ruta.nombre
            holder.comentarios.text = "🗨️ ${ruta.comentarios} comentarios"
        }

        override fun getItemCount() = rutas.size
    }
}
