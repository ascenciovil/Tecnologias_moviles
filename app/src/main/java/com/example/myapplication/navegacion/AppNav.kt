package com.example.myapplication.navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.comentarios.Comentarios
import com.example.myapplication.ui.fotos.Fotos
import com.example.myapplication.ui.detalleruta.DetalleDeRuta

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "route/1") {
        composable("route/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: "1"
            DetalleDeRuta(
                routeId = id,
                onBack = { /* raíz, no hace nada */ },
                onOpenPhotos = { nav.navigate("photos/$id") },
                onOpenComments = { nav.navigate("comments/$id") }
            )
        }
        composable("photos/{id}") { Fotos(onBack = { nav.popBackStack() }) }
        composable("comments/{id}") { Comentarios(onBack = { nav.popBackStack() }) }
    }
}
