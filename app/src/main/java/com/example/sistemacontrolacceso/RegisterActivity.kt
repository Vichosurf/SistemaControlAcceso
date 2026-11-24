package com.example.sistemacontrolacceso

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de Registro de Nuevas Cuentas de Operador OT/IoT
 * con diseño 1:1 identico a Login, cifrado SHA-256 e integración con Google Sign-In.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var etRegUsername: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etRegPassword: EditText
    private lateinit var etRegConfirmPassword: EditText
    private lateinit var btnRegisterSubmit: Button
    private lateinit var btnGoogleRegister: Button
    private lateinit var btnBackToLogin: Button
    private lateinit var btnSettingsRegister: Button
    private lateinit var tvRegisterError: TextView
    private lateinit var tvRegisterClock: TextView

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm:ss | dd/MM/yyyy", Locale.getDefault())
            tvRegisterClock.text = "🕒 ${sdf.format(Date())}"
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.applyLocale(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        val root = findViewById<View>(R.id.registerRoot)
        SettingsManager.applyBackgroundTheme(this, root, isLoginScreen = true)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etRegUsername = findViewById(R.id.etRegUsername)
        etRegEmail = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        etRegConfirmPassword = findViewById(R.id.etRegConfirmPassword)
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit)
        btnGoogleRegister = findViewById(R.id.btnGoogleRegister)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
        btnSettingsRegister = findViewById(R.id.btnSettingsRegister)
        tvRegisterError = findViewById(R.id.tvRegisterError)
        tvRegisterClock = findViewById(R.id.tvRegisterClock)

        clockHandler.post(clockRunnable)

        btnRegisterSubmit.setOnClickListener {
            procesarRegistro()
        }

        btnGoogleRegister.setOnClickListener {
            startActivity(Intent(this, GoogleAuthActivity::class.java))
        }

        btnBackToLogin.setOnClickListener {
            finish()
        }

        btnSettingsRegister.setOnClickListener {
            SettingsManager.showSettingsDialog(
                activity = this,
                rootView = root,
                isLoginScreen = true,
                onSettingsChanged = { }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun procesarRegistro() {
        val user = etRegUsername.text.toString().trim()
        val email = etRegEmail.text.toString().trim()
        val pass = etRegPassword.text.toString().trim()
        val confirmPass = etRegConfirmPassword.text.toString().trim()

        if (user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
            mostrarError("Por favor complete todos los campos del formulario.")
            return
        }

        if (user.length < 3) {
            mostrarError("El nombre de usuario debe tener al menos 3 caracteres.")
            return
        }

        if (!email.contains("@") || !email.contains(".")) {
            mostrarError("Por favor ingrese un correo electrónico válido (ejemplo@gmail.com).")
            return
        }

        if (pass.length < 4) {
            mostrarError("La contraseña debe tener al menos 4 caracteres.")
            return
        }

        if (pass != confirmPass) {
            mostrarError("Las contraseñas no coinciden. Por favor verifique.")
            return
        }

        // Registrar la cuenta cifrada con Hash SHA-256 en SecurityManager
        val isSuccess = SecurityManager.registerNewUser(this, user, pass)
        if (isSuccess) {
            tvRegisterError.visibility = View.GONE

            SecurityManager.logEvent(user, "REGISTER_SUCCESS", "Cuenta creada exitosamente con correo $email", true)

            NotificationHelper.sendNotification(
                context = this,
                notificationId = 1004,
                title = "✨ Cuenta Registrada",
                message = "Bienvenido(a) '$user' al Sistema Control de Acceso OT.",
                isAlert = false
            )

            Toast.makeText(this, "¡Cuenta '$user' creada exitosamente!", Toast.LENGTH_LONG).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("OPERATOR_USER", user)
            startActivity(intent)
            finish()
        } else {
            mostrarError("Error al registrar la cuenta. Intente nuevamente.")
        }
    }

    private fun mostrarError(mensaje: String) {
        tvRegisterError.text = mensaje
        tvRegisterError.visibility = View.VISIBLE
    }
}