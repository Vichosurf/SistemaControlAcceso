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
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

object SettingsManager {
    private const val PREFS_SETTINGS = "ot_settings_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANG = "language_code"

    const val THEME_NOCTURNO = "NOCTURNO"
    const val THEME_DIURNO = "DIURNO"
    const val LANG_ES = "es"
    const val LANG_EN = "en"

    fun getThemeMode(context: Context): String =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_NOCTURNO) ?: THEME_NOCTURNO

    fun setThemeMode(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme).apply()
    }

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, LANG_ES) ?: LANG_ES

    fun setLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, langCode).apply()
    }

    fun applyLocale(context: Context) {
        val locale = Locale(getLanguage(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun applyBackgroundTheme(activity: Activity, rootLayout: View, isLoginScreen: Boolean) {
        val bg = if (getThemeMode(activity) == THEME_DIURNO) {
            if (isLoginScreen) R.drawable.bg_login_day_gradient else R.drawable.bg_day_gradient
        } else {
            if (isLoginScreen) R.drawable.bg_login_gradient else R.drawable.bg_app_gradient
        }
        rootLayout.setBackgroundResource(bg)
    }

    fun showSettingsDialog(
        activity: Activity,
        rootView: View,
        isLoginScreen: Boolean,
        operatorUser: String = "admin",
        onSettingsChanged: () -> Unit = {},
        onLogout: (() -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null)
        val rgTheme = dialogView.findViewById<RadioGroup>(R.id.rgTheme)
        val rbThemeNight = dialogView.findViewById<RadioButton>(R.id.rbThemeNight)
        val rbThemeDay = dialogView.findViewById<RadioButton>(R.id.rbThemeDay)
        val rgLanguage = dialogView.findViewById<RadioGroup>(R.id.rgLanguage)
        val rbLangEs = dialogView.findViewById<RadioButton>(R.id.rbLangEs)
        val rbLangEn = dialogView.findViewById<RadioButton>(R.id.rbLangEn)
        val logoutButton = dialogView.findViewById<Button>(R.id.btnLogoutInDialog)

        if (getThemeMode(activity) == THEME_DIURNO) rbThemeDay.isChecked = true else rbThemeNight.isChecked = true
        if (getLanguage(activity) == LANG_EN) rbLangEn.isChecked = true else rbLangEs.isChecked = true
        logoutButton.visibility = if (onLogout != null || !isLoginScreen) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()
        rgTheme.setOnCheckedChangeListener { _, checked ->
            val theme = if (checked == R.id.rbThemeDay) THEME_DIURNO else THEME_NOCTURNO
            if (theme != getThemeMode(activity)) {
                setThemeMode(activity, theme)
                applyBackgroundTheme(activity, rootView, isLoginScreen)
                onSettingsChanged()
            }
        }
        rgLanguage.setOnCheckedChangeListener { _, checked ->
            val language = if (checked == R.id.rbLangEn) LANG_EN else LANG_ES
            if (language != getLanguage(activity)) {
                setLanguage(activity, language)
                applyLocale(activity)
                activity.recreate()
            }
        }
        logoutButton.setOnClickListener {
            dialog.dismiss()
            confirmLogout(activity, operatorUser, onLogout)
        }
        dialogView.findViewById<Button>(R.id.btnCloseSettings).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmLogout(activity: Activity, user: String, onLogout: (() -> Unit)?) {
        val english = getLanguage(activity) == LANG_EN
        AlertDialog.Builder(activity)
            .setTitle(if (english) "Logout Confirmation" else "Confirmar Cierre de Sesión")
            .setMessage(if (english) "Are you sure you want to log out?" else "¿Está seguro que desea cerrar sesión?")
            .setPositiveButton(if (english) "Yes, Logout" else "Sí, Cerrar Sesión") { _, _ ->
                SecurityManager.logEvent(user, "LOGOUT", "Cierre de sesión realizado", true)
                try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) { }
                onLogout?.invoke() ?: run {
                    val intent = Intent(activity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
            .setNegativeButton(if (english) "Cancel" else "Cancelar", null)
            .show()
    }
}
