package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar

class GaleriaRutaActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FotosAdapter
    private var imagenes: List<FotoConCoordenada> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galeria_ruta)

        toolbar = findViewById(R.id.topAppBarGaleria)
        recycler = findViewById(R.id.recycler_fotos)

        toolbar.setNavigationOnClickListener { finish() }

        imagenes = intent.getSerializableExtra("imagenes_ruta") as? ArrayList<FotoConCoordenada>
            ?: emptyList()

        if (imagenes.isEmpty()) {
            Toast.makeText(this, "Esta ruta no tiene fotos", Toast.LENGTH_SHORT).show()
        }

        adapter = FotosAdapter(imagenes)
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter
    }

    class FotosAdapter(
        private val imagenes: List<FotoConCoordenada>
    ) : RecyclerView.Adapter<FotosAdapter.FotoViewHolder>() {

        class FotoViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val imgFoto: ImageView = itemView.findViewById(R.id.img_foto)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FotoViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_foto, parent, false)
            return FotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
            val foto = imagenes[position]

            Glide.with(holder.itemView.context)
                .load(foto.uri)     // ← ahora usa la propiedad uri
                .into(holder.imgFoto)
        }

        override fun getItemCount(): Int = imagenes.size
    }
}
