package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private lateinit var etNuevoComentario: TextInputEditText
    private lateinit var btnEnviar: MaterialButton

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var rutaId: String
    private var nombreUsuarioActual: String = "Usuario"

    private lateinit var adapter: ComentariosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comentarios)

        rutaId = intent.getStringExtra("ruta_id") ?: run {
            Toast.makeText(this, "No se pudo abrir la ruta", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.topAppBar)
        recycler = findViewById(R.id.recycler_comentarios)
        etNuevoComentario = findViewById(R.id.et_nuevo_comentario)
        btnEnviar = findViewById(R.id.btn_enviar_comentario)

        // Flecha de back (si tienes un drawable, puedes setearlo aquí)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = ComentariosAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        cargarNombreUsuario()
        escucharComentarios()

        btnEnviar.setOnClickListener {
            guardarComentario()
        }
    }

    // ───────────────────────── cargar nombre de usuario ─────────────────────────
    private fun cargarNombreUsuario() {
        val usuario = auth.currentUser ?: return

        db.collection("Usuarios").document(usuario.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nombre = doc.getString("nombre_usuario") ?: "Usuario"
                    nombreUsuarioActual = nombre   // o "@$nombre" si quieres el arroba
                }
            }
    }

    // ───────────────────────── escuchar comentarios en tiempo real ─────────────
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

    // ───────────────────────── guardar nuevo comentario ────────────────────────
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

    // ───────────────────────── modelo y adapter ────────────────────────────────
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

        inner class ComentarioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvAutor: TextView = view.findViewById(R.id.tv_autor_comentario)
            val tvFecha: TextView = view.findViewById(R.id.tv_fecha_comentario)
            val tvTexto: TextView = view.findViewById(R.id.tv_texto_comentario)
            val tvAvatar: TextView = view.findViewById(R.id.tv_avatar_comentario)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComentarioViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comentario, parent, false)
            return ComentarioViewHolder(view)
        }

        override fun onBindViewHolder(holder: ComentarioViewHolder, position: Int) {
            val c = items[position]
            holder.tvAutor.text = c.nombreUsuario
            holder.tvTexto.text = c.texto
            holder.tvFecha.text = c.fecha?.let { sdf.format(it) } ?: ""

            val inicial = c.nombreUsuario.firstOrNull()?.uppercaseChar() ?: '?'
            holder.tvAvatar.text = inicial.toString()
        }

        override fun getItemCount(): Int = items.size
    }
}
