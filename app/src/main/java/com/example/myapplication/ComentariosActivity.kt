package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = ComentariosAdapter(
            db = db,
            currentUid = auth.currentUser?.uid,
            onDelete = { comentario -> borrarComentario(comentario) },
            onUserClick = { userId, userName -> navigateToUserProfile(userId, userName) }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        cargarNombreUsuario()
        escucharComentarios()

        btnEnviar.setOnClickListener { guardarComentario() }
    }

    // ==========================
    // PERFIL
    // ==========================
    private fun navigateToUserProfile(userId: String, userName: String) {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("user_id", userId)
            putExtra("user_name", userName)
            putExtra("destination", "dashboard_fragment")
            putExtra("force_reload", true)
        }
        startActivity(i)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun cargarNombreUsuario() {
        val usuario = auth.currentUser ?: return

        db.collection("Usuarios").document(usuario.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    nombreUsuarioActual = doc.getString("nombre_usuario") ?: "Usuario"
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
        if (texto.isNullOrEmpty()) return

        val usuario = auth.currentUser ?: run {
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
            .addOnSuccessListener { etNuevoComentario.setText("") }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar comentario", Toast.LENGTH_SHORT).show()
            }
    }

    private fun borrarComentario(c: Comentario) {
        val uid = auth.currentUser?.uid ?: return
        if (c.idUsuario != uid) return // seguridad extra

        db.collection("Rutas").document(rutaId)
            .collection("comentarios").document(c.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Comentario eliminado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    data class Comentario(
        val id: String = "",
        val texto: String = "",
        val nombreUsuario: String = "",
        val idUsuario: String = "",
        val fecha: Date? = null
    )

    class ComentariosAdapter(
        private val db: FirebaseFirestore,
        private val currentUid: String?,
        private val onDelete: (Comentario) -> Unit,
        private val onUserClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<ComentariosAdapter.ComentarioViewHolder>() {

        private val items = mutableListOf<Comentario>()
        private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Cache: uid -> url ("" significa “sin foto”)
        private val fotoCache = mutableMapOf<String, String>()

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

            val ivProfile: ImageView = view.findViewById(R.id.iv_profile)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_comment)

            var boundUserId: String? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComentarioViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_comentario, parent, false)
            return ComentarioViewHolder(view)
        }

        override fun onBindViewHolder(holder: ComentarioViewHolder, position: Int) {
            val c = items[position]
            holder.boundUserId = c.idUsuario

            holder.tvAutor.text = c.nombreUsuario
            holder.tvTexto.text = c.texto
            holder.tvFecha.text = c.fecha?.let { sdf.format(it) } ?: ""

            // ✅ Ir al perfil al tocar nombre / foto / inicial
            holder.tvAutor.setOnClickListener {
                if (c.idUsuario.isNotBlank()) onUserClick(c.idUsuario, c.nombreUsuario)
            }
            holder.ivProfile.setOnClickListener {
                if (c.idUsuario.isNotBlank()) onUserClick(c.idUsuario, c.nombreUsuario)
            }
            holder.tvAvatar.setOnClickListener {
                if (c.idUsuario.isNotBlank()) onUserClick(c.idUsuario, c.nombreUsuario)
            }


            val isMine = (c.idUsuario == currentUid)
            holder.btnDelete.visibility = if (isMine) View.VISIBLE else View.GONE
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Eliminar comentario")
                    .setMessage("¿Quieres eliminar tu comentario?")
                    .setPositiveButton("Eliminar") { _, _ -> onDelete(c) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            // Fallback inicial
            val inicial = c.nombreUsuario.firstOrNull()?.uppercaseChar() ?: '?'
            holder.tvAvatar.text = inicial.toString()

            // Foto
            val cached = fotoCache[c.idUsuario]
            if (cached != null) {
                pintarFoto(holder, cached)
            } else {
                holder.ivProfile.visibility = View.GONE
                holder.tvAvatar.visibility = View.VISIBLE

                db.collection("Usuarios").document(c.idUsuario)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        val url = (userDoc.getString("foto_perfil")
                            ?: userDoc.getString("foto_perf")
                            ?: "").trim()

                        fotoCache[c.idUsuario] = url

                        // evita reciclar mal
                        if (holder.boundUserId == c.idUsuario) {
                            pintarFoto(holder, url)
                        }
                    }
                    .addOnFailureListener {
                        fotoCache[c.idUsuario] = ""
                    }
            }
        }

        private fun pintarFoto(holder: ComentarioViewHolder, url: String) {
            val hasPhoto = url.isNotBlank()
            holder.ivProfile.visibility = if (hasPhoto) View.VISIBLE else View.GONE
            holder.tvAvatar.visibility = if (hasPhoto) View.GONE else View.VISIBLE

            if (hasPhoto) {
                Glide.with(holder.itemView)
                    .load(url)
                    .circleCrop()
                    .into(holder.ivProfile)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
