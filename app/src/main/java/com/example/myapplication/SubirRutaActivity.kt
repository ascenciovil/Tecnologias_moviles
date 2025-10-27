package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.myapplication.databinding.LoginBinding
import  android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity


class SubirRutaActivity : AppCompatActivity(){
    private lateinit var gridLayout: GridLayout
    private val imageUris = mutableListOf<Uri>()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()){ uri: Uri? ->
            uri?.let {
                imageUris.add(it)
                updateGrid()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)
        setContentView(R.layout.subir_ruta)
        gridLayout = findViewById(R.id.layoutgrid)
        updateGrid()
    }

    private fun updateGrid() {
        gridLayout.removeAllViews()
        for (uri in imageUris){
            val imageView = ImageView(this).apply {
                setImageURI(uri)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 200
                    height = 200
                }
                scaleType = ImageView.ScaleType.CENTER_CROP

            }
            gridLayout.addView(imageView)
        }


        val addButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 200
                height = 200
            }
            setOnClickListener { openGallery() }
        }
        gridLayout.addView(addButton)

    }

    private fun openGallery(){
        pickImageLauncher.launch("image/*")
    }
}