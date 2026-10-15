package com.example.sistemacontrolacceso

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pantalla de Login con autenticación y medidas de seguridad ISO/IEC 27001.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvLoginError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvLoginError = findViewById(R.id.tvLoginError)

        btnLogin.setOnClickListener {
            procesarLogin()
        }
    }

    private fun procesarLogin() {
        val user = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (user.isEmpty() || password.isEmpty()) {
            mostrarError("Por favor ingrese usuario y contraseña.")
            return
        }

        // Autenticar mediante SecurityManager con hash SHA-256 y reglas ISO 27001
        when (val result = SecurityManager.authenticate(this, user, password)) {
            is SecurityManager.AuthResult.Success -> {
                tvLoginError.visibility = View.GONE
                // Navegar a la pantalla principal de Monitoreo y Activador
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("OPERATOR_USER", user)
                startActivity(intent)
                finish()
            }
            is SecurityManager.AuthResult.InvalidCredentials -> {
                mostrarError("Credenciales incorrectas. Intentos restantes antes de bloqueo: ${result.remainingAttempts}")
            }
            is SecurityManager.AuthResult.LockedOut -> {
                mostrarError("⚠️ Cuenta bloqueada por seguridad (ISO 27001). Reintente en ${result.remainingSeconds}s.")
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        tvLoginError.text = mensaje
        tvLoginError.visibility = View.VISIBLE
    }
}