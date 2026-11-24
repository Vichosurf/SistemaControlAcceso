package com.example.sistemacontrolacceso

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pantalla de Autenticación Oficial en Google Accounts (Web / OAuth2)
 * Carga directamente https://accounts.google.com para seleccionar o iniciar sesión con Google
 * e ingresa de forma automática al completar la autenticación (sin botón manual).
 */
class GoogleAuthActivity : AppCompatActivity() {

    private lateinit var wvGoogleAuth: WebView
    private lateinit var pbGoogleAuth: ProgressBar
    private lateinit var btnCancelGoogleAuth: Button
    private var isAuthCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.applyLocale(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_google_auth)

        val root = findViewById<View>(R.id.googleAuthRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        wvGoogleAuth = findViewById(R.id.wvGoogleAuth)
        pbGoogleAuth = findViewById(R.id.pbGoogleAuth)
        btnCancelGoogleAuth = findViewById(R.id.btnCancelGoogleAuth)

        configurarWebView()

        btnCancelGoogleAuth.setOnClickListener {
            finish()
        }
    }

    private fun configurarWebView() {
        val settings = wvGoogleAuth.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        wvGoogleAuth.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pbGoogleAuth.visibility = View.VISIBLE

                verificarAutenticacionExitosas(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pbGoogleAuth.visibility = View.GONE

                verificarAutenticacionExitosas(url)
            }
        }

        wvGoogleAuth.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    pbGoogleAuth.visibility = View.GONE
                }
            }
        }

        // Cargar la página oficial de Cuentas de Google (accounts.google.com)
        wvGoogleAuth.loadUrl("https://accounts.google.com/ServiceLogin")
    }

    private fun verificarAutenticacionExitosas(url: String?) {
        if (url != null && !isAuthCompleted) {
            val isSuccessTarget = url.contains("myaccount.google.com") ||
                    url.contains("CheckCookie") ||
                    url.contains("ManageAccount") ||
                    url.contains("speedbump") ||
                    url.contains("oauth")

            if (isSuccessTarget) {
                isAuthCompleted = true
                completarAutenticacionGoogle()
            }
        }
    }

    private fun completarAutenticacionGoogle() {
        val currentUrl = wvGoogleAuth.url ?: "https://accounts.google.com"
        val googleUser = "Operador.Google"

        SecurityManager.logEvent(
            googleUser,
            "GOOGLE_OAUTH_SUCCESS",
            "Autenticación automatizada exitosa mediante servidor de Google ($currentUrl)",
            true
        )

        NotificationHelper.sendNotification(
            context = this,
            notificationId = 1003,
            title = "🌐 Autenticado con Google",
            message = "Sesión validada exitosamente mediante Google Accounts.",
            isAlert = false
        )

        Toast.makeText(this, "¡Autenticación con Google completada!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("OPERATOR_USER", googleUser)
        startActivity(intent)
        finish()
    }
}