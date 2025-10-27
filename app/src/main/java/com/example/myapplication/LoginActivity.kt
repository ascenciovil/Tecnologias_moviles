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

        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)

        val savedUsername = sharedPref.getString("username", "")
        val savedPassword = sharedPref.getString("contrasena", "")
        val isChecked = sharedPref.getBoolean("remember", false)

        binding.username.setText(savedUsername)
        binding.contrasena.setText(savedPassword)
        binding.guardar.isChecked = isChecked

        binding.loginButton.setOnClickListener(View.OnClickListener{
            val user = binding.username.text.toString()
            val pass = binding.contrasena.text.toString()


            if (binding.username.text.toString() == "User" && binding.contrasena.text.toString() == "pass"){
                Toast.makeText(this, "Se ha iniciado sesion", Toast.LENGTH_SHORT).show()

                if (binding.guardar.isChecked) {
                    val editor = sharedPref.edit()
                    editor.putString("username", user)
                    editor.putString("contrasena", pass)
                    editor.putBoolean("remember", true)
                    editor.apply()
                } else {
                    // Si no está marcado, limpiamos las preferencias
                    sharedPref.edit().clear().apply()
                }

                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else{
                Toast.makeText(this, "Login fallido", Toast.LENGTH_SHORT).show()
            }
        })
        //cambiar de vista
        binding.registerButton.setOnClickListener {
            binding.loginLayout.visibility = View.GONE
            binding.registerLayout.visibility = View.VISIBLE
        }

        binding.volverButton.setOnClickListener {
            binding.registerLayout.visibility = View.GONE
            binding.loginLayout.visibility = View.VISIBLE
        }

    }
}