package com.example.myapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.navegacion.AppNav

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val composeView = view.findViewById<ComposeView>(R.id.home_compose_view)
        composeView.setContent {
            MaterialTheme {
                AppNav()
            }
        }
    }
}
