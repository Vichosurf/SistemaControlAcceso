package com.example.sistemacontrolacceso

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * Gestor de Configuración General del Sistema:
 * 1. Cambio de Fondo / Tema (Nocturno vs Diurno).
 * 2. Cambio de Idioma (Español vs English).
 * 3. Cerrar Sesión con confirmación y registro en auditoría ISO 27001.
 */
object SettingsManager {

    private const val PREFS_SETTINGS = "ot_settings_prefs"
    private const val KEY_THEME = "theme_mode" // "NOCTURNO" | "DIURNO"
    private const val KEY_LANG = "language_code" // "es" | "en"

    const val THEME_NOCTURNO = "NOCTURNO"
    const val THEME_DIURNO = "DIURNO"

    const val LANG_ES = "es"
    const val LANG_EN = "en"

    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_NOCTURNO) ?: THEME_NOCTURNO
    }

    fun setThemeMode(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_ES) ?: LANG_ES
    }

    fun setLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, langCode)
            .apply()
    }

    /**
     * Aplica la configuración de idioma al Context/Resources de la aplicación.
     */
    fun applyLocale(context: Context) {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    /**
     * Aplica el fondo del tema seleccionado (Nocturno vs Diurno).
     */
    fun applyBackgroundTheme(activity: Activity, rootLayout: View, isLoginScreen: Boolean) {
        val theme = getThemeMode(activity)
        if (theme == THEME_DIURNO) {
            val bgRes = if (isLoginScreen) R.drawable.bg_login_day_gradient else R.drawable.bg_day_gradient
            rootLayout.setBackgroundResource(bgRes)
        } else {
            val bgRes = if (isLoginScreen) R.drawable.bg_login_gradient else R.drawable.bg_app_gradient
            rootLayout.setBackgroundResource(bgRes)
        }
    }

    /**
     * Muestra el diálogo modal de Configuración.
     */
    fun showSettingsDialog(
        activity: Activity,
        rootView: View,
        isLoginScreen: Boolean,
        operatorUser: String = "admin",
        onSettingsChanged: () -> Unit = {},
        onLogout: (() -> Unit)? = null
    ) {
        val currentTheme = getThemeMode(activity)
        val currentLang = getLanguage(activity)

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null)

        val rgTheme = dialogView.findViewById<RadioGroup>(R.id.rgTheme)
        val rbThemeNight = dialogView.findViewById<RadioButton>(R.id.rbThemeNight)
        val rbThemeDay = dialogView.findViewById<RadioButton>(R.id.rbThemeDay)

        val rgLanguage = dialogView.findViewById<RadioGroup>(R.id.rgLanguage)
        val rbLangEs = dialogView.findViewById<RadioButton>(R.id.rbLangEs)
        val rbLangEn = dialogView.findViewById<RadioButton>(R.id.rbLangEn)

        val btnLogoutInDialog = dialogView.findViewById<Button>(R.id.btnLogoutInDialog)

        // Estado inicial de los radios
        if (currentTheme == THEME_DIURNO) {
            rbThemeDay.isChecked = true
        } else {
            rbThemeNight.isChecked = true
        }

        if (currentLang == LANG_EN) {
            rbLangEn.isChecked = true
        } else {
            rbLangEs.isChecked = true
        }

        if (onLogout != null || !isLoginScreen) {
            btnLogoutInDialog.visibility = View.VISIBLE
        } else {
            btnLogoutInDialog.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = if (checkedId == R.id.rbThemeDay) THEME_DIURNO else THEME_NOCTURNO
            if (selectedTheme != getThemeMode(activity)) {
                setThemeMode(activity, selectedTheme)
                applyBackgroundTheme(activity, rootView, isLoginScreen)
                onSettingsChanged()
            }
        }

        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val selectedLang = if (checkedId == R.id.rbLangEn) LANG_EN else LANG_ES
            if (selectedLang != getLanguage(activity)) {
                setLanguage(activity, selectedLang)
                applyLocale(activity)
                activity.recreate()
            }
        }

        btnLogoutInDialog.setOnClickListener {
            dialog.dismiss()
            confirmarCerrarSesion(activity, operatorUser, onLogout)
        }

        dialogView.findViewById<Button>(R.id.btnCloseSettings).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmarCerrarSesion(
        activity: Activity,
        user: String,
        onLogout: (() -> Unit)?
    ) {
        val isEng = getLanguage(activity) == LANG_EN
        val title = if (isEng) "Logout Confirmation" else "Confirmar Cierre de Sesión"
        val message = if (isEng) "Are you sure you want to log out of the OT System?" else "¿Está seguro que desea cerrar sesión en el Sistema OT?"
        val posBtn = if (isEng) "Yes, Logout" else "Sí, Cerrar Sesión"
        val negBtn = if (isEng) "Cancel" else "Cancelar"

        AlertDialog.Builder(activity)
            .setTitle("🚪 $title")
            .setMessage(message)
            .setPositiveButton(posBtn) { _, _ ->
                SecurityManager.logEvent(user, "LOGOUT", "Cierre de sesión de operador realizado", true)
                onLogout?.invoke() ?: run {
                    val intent = Intent(activity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
            .setNegativeButton(negBtn, null)
            .show()
    }
}