package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.LoginBinding

class LoginActivity : AppCompatActivity(){
    private lateinit var binding: LoginBinding

    lateinit var username : EditText
    lateinit var contraseña : EditText
    lateinit var loginButton : Button
    lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener(View.OnClickListener{
            if (binding.username.text.toString() == "User" && binding.contraseA.text.toString() == "pass"){
                Toast.makeText(this, "Se ha iniciado sesion", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else{
                Toast.makeText(this, "Login fallido", Toast.LENGTH_SHORT).show()
            }
        })

    }
}