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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Autenticación Google obligatoria antes de acceder a los módulos OT/IoT. */
class LoginActivity : AppCompatActivity() {
    private lateinit var loginButton: Button
    private lateinit var errorLabel: TextView
    private lateinit var clockLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val clockTask = object : Runnable {
        override fun run() {
            clockLabel.text = SimpleDateFormat("HH:mm:ss | dd/MM/yyyy", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) {
                showError("Google no entregó un ID token. Revisa el OAuth Client ID Web y el SHA-1 en Firebase.")
                return@registerForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(token, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { auth ->
                    val user = auth.user
                    if (user == null) showError("Firebase no devolvió un usuario autenticado.")
                    else openMain(user.displayName ?: user.email ?: "Usuario Google")
                }
                .addOnFailureListener { showError("Falló la autenticación Firebase: ${it.localizedMessage}") }
        } catch (error: ApiException) {
            showError("No se pudo iniciar sesión con Google (código ${error.statusCode}).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.applyLocale(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val root = findViewById<View>(R.id.loginRoot)
        SettingsManager.applyBackgroundTheme(this, root, isLoginScreen = true)
        errorLabel = findViewById(R.id.tvLoginError)
        clockLabel = findViewById(R.id.tvLoginClock)
        loginButton = findViewById(R.id.btnLogin)

        // Se ocultan los campos de credenciales locales: no pueden saltarse Google.
        findViewById<EditText>(R.id.etUsername).visibility = View.GONE
        findViewById<EditText>(R.id.etPassword).visibility = View.GONE
        loginButton.text = "CONTINUAR CON GOOGLE"
        loginButton.setOnClickListener { startGoogleSignIn() }

        findViewById<Button>(R.id.btnSettingsLogin).setOnClickListener {
            SettingsManager.showSettingsDialog(this, root, true)
        }
        findViewById<Button>(R.id.btnAboutApp).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sistema de Control de Acceso OT / IoT")
                .setMessage("Aplicación Android para monitorear sensores y controlar actuadores Wemos mediante Firebase Realtime Database. El acceso requiere una cuenta Google autenticada con Firebase.")
                .setPositiveButton("Entendido", null)
                .show()
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            showError("Falta app/google-services.json. Sigue CONFIGURACION_FIREBASE.md.")
            loginButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()
        val user = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (user != null) openMain(user.displayName ?: user.email ?: "Usuario Google")
    }

    override fun onResume() { super.onResume(); handler.post(clockTask) }
    override fun onPause() { handler.removeCallbacks(clockTask); super.onPause() }

    private fun startGoogleSignIn() {
        val id = resources.getIdentifier("default_web_client_id", "string", packageName)
        val generatedId = if (id != 0) getString(id) else ""
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { generatedId }
        if (webClientId.isBlank()) {
            showError("Configura google_web_client_id en firebase.properties.")
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        signInLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun openMain(name: String) {
        SecurityManager.logEvent(name, "LOGIN_GOOGLE_SUCCESS", "Autenticación Google/Firebase correcta", true)
        startActivity(Intent(this, MainActivity::class.java).putExtra("OPERATOR_USER", name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun showError(message: String) {
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }
}
