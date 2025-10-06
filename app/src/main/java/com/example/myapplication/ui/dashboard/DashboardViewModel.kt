package com.example.myapplication.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    // Datos del perfil
    private val _titulo = MutableLiveData<String>().apply {
        value = "Desarrollador Android"
    }
    val titulo: LiveData<String> = _titulo

    private val _nombre = MutableLiveData<String>().apply {
        value = "Juan Pérez"
    }
    val nombre: LiveData<String> = _nombre

    private val _descripcion = MutableLiveData<String>().apply {
        value = "Apasionado por el desarrollo móvil y las nuevas tecnologías. Me encanta crear aplicaciones que hagan la vida más fácil."
    }
    val descripcion: LiveData<String> = _descripcion

    private val _habilidades = MutableLiveData<List<String>>().apply {
        value = listOf(
            "Kotlin",
            "Java",
            "Android SDK",
            "Jetpack Compose",
            "Firebase",
            "Git"
        )
    }
    val habilidades: LiveData<List<String>> = _habilidades
}