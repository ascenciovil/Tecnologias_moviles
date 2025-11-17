package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class VistaRuta : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vista_ruta)

        // 🧭 Referencias a vistas
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val btnSeguir = findViewById<MaterialButton>(R.id.btn_seguir_ruta)
        val btnFotos = findViewById<MaterialButton>(R.id.btn_ver_fotos)
        val btnComentarios = findViewById<MaterialButton>(R.id.btn_ver_comentarios)
        val tvDescription = findViewById<TextView>(R.id.tv_description)
        val imgMap = findViewById<ImageView>(R.id.img_map_placeholder)

        val nombreRuta = "Ruta del Parque Central"
        toolbar.title = nombreRuta

        val descripcion = """
            Esta ruta recorre el Parque Central completo, ideal para caminar o trotar.
            Tiene una distancia aproximada de 5 km y cuenta con zonas verdes y senderos.
        """.trimIndent()
        tvDescription.text = descripcion

        toolbar.setNavigationOnClickListener {
            finish() // vuelve a la activity anterior
        }

        btnSeguir.setOnClickListener { view ->
            Snackbar.make(view, "Iniciando seguimiento (no implementado)...", Snackbar.LENGTH_SHORT).show()

        }

        // 🖼️ Botón "Ver fotos"
        btnFotos.setOnClickListener {
            Toast.makeText(this, "Abriendo galería (no implementado)", Toast.LENGTH_SHORT).show()
        }

        // 💬 Botón "Ver comentarios"
        btnComentarios.setOnClickListener {
            Toast.makeText(this, "Mostrando comentarios (no implementado)", Toast.LENGTH_SHORT).show()
        }

        // 🗺️ Click en el mapa (por ahora muestra un Toast)
        imgMap.setOnClickListener {
            Toast.makeText(this, "Mapa ampliado (en desarrollo)", Toast.LENGTH_SHORT).show()
        }
    }
}
