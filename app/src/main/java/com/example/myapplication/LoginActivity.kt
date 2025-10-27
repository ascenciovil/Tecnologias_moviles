package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.LoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // Importar Firestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // 1. Inicializar Firestore

    // Data class para representar la estructura de tu usuario en Firestore
    data class Usuario(
        val user_id: String = "",
        val Correo: String = "",
        val nombre_usuario: String = "", // Este campo vendrá de 'userRegistro'
        val foto_perf: String = "",
        val seguidos: List<String> = emptyList(),
        val rutas_creadas: List<String> = emptyList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance() // Inicializar Firestore
        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)

        // --- Lógica de 'Recuerdame' (sin cambios) ---
        val savedUsername = sharedPref.getString("username", "")
        val savedPassword = sharedPref.getString("contrasena", "")
        val isChecked = sharedPref.getBoolean("remember", false)

        binding.username.setText(savedUsername)
        binding.contrasena.setText(savedPassword)
        binding.guardar.isChecked = isChecked

        // ------------------------------------------------------------------
        // ## LOGIN CON FIREBASE (Usando Correo y Contraseña de Authentication)
        // ------------------------------------------------------------------
        binding.loginButton.setOnClickListener {
            // El campo 'username' en el layout de login lo usas para el CORREO
            val correo = binding.username.text.toString().trim()
            val pass = binding.contrasena.text.toString().trim()

            if (correo.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(correo, pass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Inicio exitoso", Toast.LENGTH_SHORT).show()

                    // Lógica de 'Recuerdame'
                    if (binding.guardar.isChecked) {
                        sharedPref.edit().apply {
                            putString("username", correo)
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

        // CAMBIAR A REGISTRO (sin cambios)
        binding.registerButton.setOnClickListener {
            binding.loginLayout.visibility = View.GONE
            binding.registerLayout.visibility = View.VISIBLE
        }

        // ----------------------------------------------------------------------
        // ## REGISTRO CON FIREBASE (Authentication + Firestore)
        // ----------------------------------------------------------------------
        binding.RegistroButton.setOnClickListener {
            // IDs de tu XML:
            val correo = binding.usernameRegistro.text.toString().trim() // Correo
            val nombreUsuario = binding.userRegistro.text.toString().trim() // Nombre de usuario
            val contrasena = binding.contrasenaRegistro.text.toString().trim() // Contraseña
            val passConfirm = binding.contrasenaConfirma.text.toString().trim() // Confirmar

            if (correo.isEmpty() || nombreUsuario.isEmpty() || contrasena.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (contrasena != passConfirm) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Crear usuario en Firebase Authentication
            auth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val userId = user?.uid ?: return@addOnSuccessListener // Obtener el UID único

                    // 2. Crear objeto de Usuario para Firestore con los datos extra
                    val nuevoUsuario = Usuario(
                        user_id = userId,
                        Correo = correo,
                        nombre_usuario = nombreUsuario, // Guardamos el nombre de usuario
                        foto_perf = "", // Se puede actualizar después
                        seguidos = emptyList(),
                        rutas_creadas = emptyList()
                    )

                    // 3. Guardar datos en Firestore en la colección "Usuarios"
                    db.collection("Usuarios").document(userId) // Usar el UID como Document ID
                        .set(nuevoUsuario)
                        .addOnSuccessListener {
                            Toast.makeText(this, "¡Registro exitoso y datos guardados!", Toast.LENGTH_LONG).show()

                            // Volver al login
                            binding.registerLayout.visibility = View.GONE
                            binding.loginLayout.visibility = View.VISIBLE
                        }
                        .addOnFailureListener { e ->
                            // Si falla el guardado de datos, es buena práctica borrar el usuario de Auth
                            user.delete()
                            Toast.makeText(this, "Error al guardar datos. Usuario eliminado.", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al registrar: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // VOLVER A LOGIN (sin cambios)
        binding.volverButton.setOnClickListener {
            binding.registerLayout.visibility = View.GONE
            binding.loginLayout.visibility = View.VISIBLE
        }
    }
}