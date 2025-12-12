package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.appbar.MaterialToolbar

class PhotoViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URLS = "urls"
        const val EXTRA_START_INDEX = "start_index"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        toolbar = findViewById(R.id.topAppBarPhotoViewer)
        pager = findViewById(R.id.pagerFotos)

        toolbar.setNavigationOnClickListener { finish() }

        val urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            .coerceIn(0, (urls.size - 1).coerceAtLeast(0))

        pager.adapter = PhotoPagerAdapter(urls)
        pager.setCurrentItem(startIndex, false)
    }
}

private class PhotoPagerAdapter(
    private val urls: List<String>
) : RecyclerView.Adapter<PhotoPagerAdapter.VH>() {

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val photoView: PhotoView = itemView.findViewById(R.id.photoView)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_page, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        Glide.with(holder.itemView.context)
            .load(urls[position])
            .into(holder.photoView)
    }

    override fun getItemCount(): Int = urls.size
}
