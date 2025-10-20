package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.LoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)

        val savedUsername = sharedPref.getString("username", "")
        val savedPassword = sharedPref.getString("contrasena", "")
        val isChecked = sharedPref.getBoolean("remember", false)

        binding.username.setText(savedUsername)
        binding.contrasena.setText(savedPassword)
        binding.guardar.isChecked = isChecked

        // LOGIN CON FIREBASE
        binding.loginButton.setOnClickListener {
            val user = binding.username.text.toString()
            val pass = binding.contrasena.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(user, pass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Inicio exitoso", Toast.LENGTH_SHORT).show()

                    if (binding.guardar.isChecked) {
                        sharedPref.edit().apply {
                            putString("username", user)
                            putString("contrasena", pass)
                            putBoolean("remember", true)
                            apply()
                        }
                    } else sharedPref.edit().clear().apply()

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                }
        }

        // CAMBIAR A REGISTRO
        binding.registerButton.setOnClickListener {
            binding.loginLayout.visibility = View.GONE
            binding.registerLayout.visibility = View.VISIBLE
        }

        // REGISTRO CON FIREBASE
        binding.RegistroButton.setOnClickListener {  // ID REAL DEL XML
            val user = binding.usernameRegistro.text.toString()
            val pass = binding.contrasenaRegistro.text.toString()
            val passConfirm = binding.contrasenaConfirma.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != passConfirm) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(user, pass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Usuario registrado", Toast.LENGTH_SHORT).show()
                    binding.registerLayout.visibility = View.GONE
                    binding.loginLayout.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al registrar: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // VOLVER A LOGIN
        binding.volverButton.setOnClickListener {
            binding.registerLayout.visibility = View.GONE
            binding.loginLayout.visibility = View.VISIBLE
        }
    }
}
