package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComentariosActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ComentariosAdapter
    private lateinit var etNuevoComentario: TextInputEditText
    private lateinit var btnEnviar: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var nombreUsuarioActual: String = "Usuario"


    private lateinit var rutaId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comentarios)

        rutaId = intent.getStringExtra("ruta_id") ?: run {
            Toast.makeText(this, "No se pudo abrir la ruta", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cargarNombreUsuario()

        toolbar = findViewById(R.id.topAppBar)
        recycler = findViewById(R.id.recycler_comentarios)
        etNuevoComentario = findViewById(R.id.et_nuevo_comentario)
        btnEnviar = findViewById(R.id.btn_enviar_comentario)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = ComentariosAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        escucharComentarios()

        btnEnviar.setOnClickListener {
            guardarComentario()
        }
    }

    private fun cargarNombreUsuario() {
        val usuario = auth.currentUser ?: return

        db.collection("Usuarios").document(usuario.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nombre = doc.getString("nombre_usuario") ?: "Usuario"
                    nombreUsuarioActual = nombre
                }
            }
    }


    private fun escucharComentarios() {
        db.collection("Rutas").document(rutaId)
            .collection("comentarios")
            .orderBy("fecha", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al cargar comentarios", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.map { doc ->
                    Comentario(
                        id = doc.id,
                        texto = doc.getString("texto") ?: "",
                        nombreUsuario = doc.getString("nombre_usuario") ?: "Anónimo",
                        idUsuario = doc.getString("id_usuario") ?: "",
                        fecha = doc.getTimestamp("fecha")?.toDate()
                    )
                } ?: emptyList()

                adapter.setItems(lista)
            }
    }

    private fun guardarComentario() {
        val texto = etNuevoComentario.text?.toString()?.trim()
        if (texto.isNullOrEmpty()) {
            return
        }

        val usuario = auth.currentUser
        if (usuario == null) {
            Toast.makeText(this, "Debes iniciar sesión para comentar", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "texto" to texto,
            "id_usuario" to usuario.uid,
            "nombre_usuario" to nombreUsuarioActual,
            "fecha" to FieldValue.serverTimestamp()
        )


        db.collection("Rutas").document(rutaId)
            .collection("comentarios")
            .add(data)
            .addOnSuccessListener {
                etNuevoComentario.setText("")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar comentario", Toast.LENGTH_SHORT).show()
            }
    }
    //  MODELO Y ADAPTADOR

    data class Comentario(
        val id: String = "",
        val texto: String = "",
        val nombreUsuario: String = "",
        val idUsuario: String = "",
        val fecha: Date? = null
    )

    class ComentariosAdapter : RecyclerView.Adapter<ComentariosAdapter.ComentarioViewHolder>() {

        private val items = mutableListOf<Comentario>()
        private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun setItems(nuevos: List<Comentario>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        inner class ComentarioViewHolder(itemView: android.view.View) :
            RecyclerView.ViewHolder(itemView) {

            val tvAutor = itemView.findViewById<android.widget.TextView>(R.id.tv_autor_comentario)
            val tvFecha = itemView.findViewById<android.widget.TextView>(R.id.tv_fecha_comentario)
            val tvTexto = itemView.findViewById<android.widget.TextView>(R.id.tv_texto_comentario)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ComentarioViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comentario, parent, false)
            return ComentarioViewHolder(view)
        }

        override fun onBindViewHolder(holder: ComentarioViewHolder, position: Int) {
            val c = items[position]
            holder.tvAutor.text = c.nombreUsuario
            holder.tvTexto.text = c.texto
            holder.tvFecha.text = c.fecha?.let { sdf.format(it) } ?: ""
        }

        override fun getItemCount(): Int = items.size
    }
}
