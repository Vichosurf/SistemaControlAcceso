package com.example.sistemacontrolacceso

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de Login con autenticación, descripción del sistema,
 * reloj en tiempo real y seguridad ISO/IEC 27001.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnSettingsLogin: Button
    private lateinit var btnAboutApp: Button
    private lateinit var tvLoginError: TextView
    private lateinit var tvLoginClock: TextView

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm:ss | dd/MM/yyyy", Locale.getDefault())
            tvLoginClock.text = "🕒 ${sdf.format(Date())}"
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.applyLocale(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val root = findViewById<View>(R.id.loginRoot)
        SettingsManager.applyBackgroundTheme(this, root, isLoginScreen = true)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnSettingsLogin = findViewById(R.id.btnSettingsLogin)
        btnAboutApp = findViewById(R.id.btnAboutApp)
        tvLoginError = findViewById(R.id.tvLoginError)
        tvLoginClock = findViewById(R.id.tvLoginClock)

        // Iniciar reloj en tiempo real
        clockHandler.post(clockRunnable)

        btnLogin.setOnClickListener {
            procesarLogin()
        }

        btnSettingsLogin.setOnClickListener {
            SettingsManager.showSettingsDialog(
                activity = this,
                rootView = root,
                isLoginScreen = true,
                onSettingsChanged = { }
            )
        }

        btnAboutApp.setOnClickListener {
            mostrarDialogoAcercaDe()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun mostrarDialogoAcercaDe() {
        val descripcionLarga = """
            📱 SISTEMA CONTROL DE ACCESO OT / IoT
            
            🔹 ¿De qué se trata la aplicación?
            Es una solución tecnológica integral diseñada para el control de acceso automatizado y monitoreo operacional en zonas industriales, mineras u operativas con baja visibilidad o zonas oscuras.
            
            ⚙️ Funcionalidades Principales:
            1. Monitoreo en Tiempo Real:
               - Lectura de sensor ultrasónico (distancia de aproximación).
               - Lectura de fotorresistencia LDR (nivel de luz ambiente).
            2. Control Automático y Manual de Actuadores:
               - Activación de servomotor (barrera 0° / 90°).
               - Indicación por señalización LED (Verde / Rojo).
               - Switch de anulación de emergencia (seguridad OT).
            3. Interconexión Inalámbrica:
               - Soporte para Bluetooth (HC-05/ESP32), Wi-Fi UDP Sockets y Redes Mesh entre dispositivos móviles.
            4. Seguridad Normativa ISO/IEC 27001:
               - Almacenamiento seguro con Hash SHA-256.
               - Prevención de ataques de fuerza bruta (bloqueo por reintentos).
               - Registro inmutable de Auditoría OT.
            5. Ubicación GPS y Hora en Tiempo Real:
               - Geolocalización de la zona de control y fecha/hora sincronizada.
               - Notificaciones instantáneas del sistema.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("ℹ️ Descripción del Sistema OT")
            .setMessage(descripcionLarga)
            .setPositiveButton("Entendido", null)
            .show()
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

                // Enviar notificación de inicio de sesión exitoso
                NotificationHelper.sendNotification(
                    context = this,
                    notificationId = 1001,
                    title = "🔐 Sesión Iniciada",
                    message = "Operador '$user' ha ingresado al Sistema Control de Acceso OT.",
                    isAlert = false
                )

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
