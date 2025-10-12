package com.example.myapplication.ui.detalleruta

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.FakeRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleDeRuta(
    routeId: String,
    onBack: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenComments: () -> Unit
) {
    val route = FakeRoute.getRoute(routeId)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        Toast.makeText(context, "Seguir ruta: próximamente", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Seguir Ruta") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${route.etaMinutes} minutos de caminata") }
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text("Mapa (no implementado)") }

            Spacer(Modifier.height(8.dp))
            Text(
                "A ${"%.1f".format(route.distanceKm)} km de distancia",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
            Text("Descripción:", style = MaterialTheme.typography.titleMedium)
            Text(route.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onOpenPhotos,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Ver Fotos") }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenComments,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Ver Comentarios") }
        }
    }
}
