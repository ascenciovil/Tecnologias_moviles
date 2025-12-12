package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar

class GaleriaRutaActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: GaleriaSeccionAdapter

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

        val fotosEnRuta = imagenes.filter { it.lat != null && it.lng != null }
        val fotosDespues = imagenes.filter { it.lat == null || it.lng == null }

        val ordenGlobal = (fotosEnRuta + fotosDespues)
        val urlsGlobales = ArrayList(ordenGlobal.map { it.uri })

        val items = buildList {
            if (fotosEnRuta.isNotEmpty()) {
                add(GaleriaItem.Header("Fotos sacadas en la ruta"))
                fotosEnRuta.forEachIndexed { idx, foto ->
                    add(GaleriaItem.Foto(foto, globalIndex = idx))
                }
            }
            if (fotosDespues.isNotEmpty()) {
                add(GaleriaItem.Header("Fotos subidas después de la ruta"))
                fotosDespues.forEachIndexed { idx, foto ->
                    add(GaleriaItem.Foto(foto, globalIndex = fotosEnRuta.size + idx))
                }
            }
        }

        adapter = GaleriaSeccionAdapter(items) { globalIndex ->
            val i = Intent(this, PhotoViewerActivity::class.java)
            i.putStringArrayListExtra(PhotoViewerActivity.EXTRA_URLS, urlsGlobales)
            i.putExtra(PhotoViewerActivity.EXTRA_START_INDEX, globalIndex)
            startActivity(i)
        }

        val grid = GridLayoutManager(this, 2)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == GaleriaSeccionAdapter.VIEW_TYPE_HEADER) 2 else 1
            }
        }

        recycler.layoutManager = grid
        recycler.adapter = adapter
    }
}

sealed class GaleriaItem {
    data class Header(val title: String) : GaleriaItem()
    data class Foto(val foto: FotoConCoordenada, val globalIndex: Int) : GaleriaItem()
}

class GaleriaSeccionAdapter(
    private val items: List<GaleriaItem>,
    private val onFotoClick: (globalIndex: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_FOTO = 1
    }

    class HeaderVH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tv_header)
    }

    class FotoVH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.img_foto)
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is GaleriaItem.Header -> VIEW_TYPE_HEADER
            is GaleriaItem.Foto -> VIEW_TYPE_FOTO
        }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_galeria_header, parent, false))
            else -> FotoVH(inflater.inflate(R.layout.item_foto, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is GaleriaItem.Header -> (holder as HeaderVH).title.text = item.title
            is GaleriaItem.Foto -> {
                val vh = holder as FotoVH
                Glide.with(vh.itemView.context).load(item.foto.uri).into(vh.img)
                vh.itemView.setOnClickListener { onFotoClick(item.globalIndex) }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
