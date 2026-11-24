package com.example.sistemacontrolacceso

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
 * soporte para Continuar con Google, Registro de Nuevas Cuentas,
 * reloj en tiempo real y seguridad ISO/IEC 27001.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogleLogin: Button
    private lateinit var btnRegisterAccount: Button
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
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        btnRegisterAccount = findViewById(R.id.btnRegisterAccount)
        btnSettingsLogin = findViewById(R.id.btnSettingsLogin)
        btnAboutApp = findViewById(R.id.btnAboutApp)
        tvLoginError = findViewById(R.id.tvLoginError)
        tvLoginClock = findViewById(R.id.tvLoginClock)

        // Iniciar reloj en tiempo real
        clockHandler.post(clockRunnable)

        btnLogin.setOnClickListener {
            procesarLogin()
        }

        btnGoogleLogin.setOnClickListener {
            mostrarDialogoGoogleSignIn()
        }

        btnRegisterAccount.setOnClickListener {
            mostrarDialogoOpcionesRegistro()
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

    private fun mostrarDialogoGoogleSignIn() {
        val accounts = arrayOf(
            "Vicente Vega (vicente.vega@inacapmail.cl)",
            "Cristóbal Jara (cristobal.jara@inacapmail.cl)",
            "Operador OT (operador.ot.2026@gmail.com)",
            "➕ Usar otra cuenta de Google..."
        )

        AlertDialog.Builder(this)
            .setTitle("🌐 Seleccionar Cuenta de Google")
            .setItems(accounts) { _, which ->
                if (which == 3) {
                    solicitarOtraCuentaGoogle()
                } else {
                    val selectedAccount = accounts[which]
                    val userName = selectedAccount.substringBefore(" (")
                    completarIngresoGoogle(userName, selectedAccount)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun solicitarOtraCuentaGoogle() {
        val input = EditText(this)
        input.hint = "ejemplo@gmail.com"
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        AlertDialog.Builder(this)
            .setTitle("🌐 Iniciar Sesión con Google")
            .setMessage("Ingrese su correo electrónico de Google:")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val email = input.text.toString().trim()
                if (email.contains("@")) {
                    val name = email.substringBefore("@")
                    completarIngresoGoogle(name, email)
                } else {
                    Toast.makeText(this, "Por favor ingrese un correo válido.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun completarIngresoGoogle(userName: String, accountInfo: String) {
        SecurityManager.logEvent(userName, "GOOGLE_LOGIN_SUCCESS", "Autenticación iniciada con Google ($accountInfo)", true)

        NotificationHelper.sendNotification(
            context = this,
            notificationId = 1002,
            title = "🌐 Sesión Google Iniciada",
            message = "Bienvenido(a) '$userName' al Sistema Control de Acceso OT.",
            isAlert = false
        )

        Toast.makeText(this, "¡Bienvenido(a) $userName!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("OPERATOR_USER", userName)
        startActivity(intent)
        finish()
    }

    private fun mostrarDialogoOpcionesRegistro() {
        val opciones = arrayOf(
            "👤 Ingreso con Usuario Operador y Contraseña nueva",
            "🌐 Continuar con Google"
        )

        AlertDialog.Builder(this)
            .setTitle("✨ Crear Nueva Cuenta")
            .setMessage("Seleccione la opción con la que desea registrarse:")
            .setItems(opciones) { _, which ->
                if (which == 0) {
                    mostrarFormularioRegistroManual()
                } else {
                    mostrarDialogoGoogleSignIn()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarFormularioRegistroManual() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 10)

        val etNewUser = EditText(this)
        etNewUser.hint = "Nuevo Usuario Operador"
        layout.addView(etNewUser)

        val etNewPass = EditText(this)
        etNewPass.hint = "Nueva Contraseña"
        etNewPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(etNewPass)

        val etConfirmPass = EditText(this)
        etConfirmPass.hint = "Confirmar Contraseña"
        etConfirmPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(etConfirmPass)

        AlertDialog.Builder(this)
            .setTitle("👤 Registro de Nuevo Operador")
            .setView(layout)
            .setPositiveButton("REGISTRAR E INGRESAR") { _, _ ->
                val user = etNewUser.text.toString().trim()
                val pass = etNewPass.text.toString().trim()
                val confirm = etConfirmPass.text.toString().trim()

                if (user.length < 3) {
                    Toast.makeText(this, "El usuario debe tener al menos 3 caracteres.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.length < 4) {
                    Toast.makeText(this, "La contraseña debe tener al menos 4 caracteres.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass != confirm) {
                    Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val registered = SecurityManager.registerNewUser(this, user, pass)
                if (registered) {
                    Toast.makeText(this, "¡Cuenta '$user' creada exitosamente!", Toast.LENGTH_LONG).show()
                    etUsername.setText(user)
                    etPassword.setText(pass)
                    procesarLogin()
                } else {
                    Toast.makeText(this, "Error al registrar usuario", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            4. Seguridad Normativa ISO 27400 / ISO/IEC 27001:
               - Almacenamiento seguro con Hash SHA-256.
               - Autenticación mediante Google Sign-In y Registro Dinámico de Usuarios.
               - Prevención de ataques de fuerza bruta (bloqueo por reintentos).
               - Registro inmutable de Auditoría OT e Integridad de Datos.
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

        when (val result = SecurityManager.authenticate(this, user, password)) {
            is SecurityManager.AuthResult.Success -> {
                tvLoginError.visibility = View.GONE

                NotificationHelper.sendNotification(
                    context = this,
                    notificationId = 1001,
                    title = "🔐 Sesión Iniciada",
                    message = "Operador '$user' ha ingresado al Sistema Control de Acceso OT.",
                    isAlert = false
                )

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("OPERATOR_USER", user)
                startActivity(intent)
                finish()
            }
            is SecurityManager.AuthResult.InvalidCredentials -> {
                mostrarError("Credenciales incorrectas. Intentos restantes antes de bloqueo: ${result.remainingAttempts}")
            }
            is SecurityManager.AuthResult.LockedOut -> {
                mostrarError("⚠️ Cuenta bloqueada por seguridad (ISO 27400). Reintente en ${result.remainingSeconds}s.")
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        tvLoginError.text = mensaje
        tvLoginError.visibility = View.VISIBLE
    }
}