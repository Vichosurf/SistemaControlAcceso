package com.example.sistemacontrolacceso

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Cola persistente de eventos de transición de barrera.
 * Los eventos sobreviven al cierre de la app.
 * Solo deben eliminarse después de recibir confirmación del servicio remoto.
 */
object WemosEventStore {
    private const val PREFS = "wemos_event_queue"
    private const val KEY_EVENTS = "pending_events"
    private const val MAX_EVENTS = 200

    @Synchronized
    fun registrarCambio(context: Context, abierta: Boolean, motivo: String, distancia: Int, luz: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
        val item = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()))
            .put("barrera", if (abierta) "abierta" else "cerrada")
            .put("evento", motivo)
            .put("distancia", distancia)
            .put("luz", luz)
            .put("sincronizado", false)
        events.put(item)
        val bounded = JSONArray()
        val start = (events.length() - MAX_EVENTS).coerceAtLeast(0)
        for (i in start until events.length()) bounded.put(events.getJSONObject(i))
        prefs.edit().putString(KEY_EVENTS, bounded.toString()).apply()
    }

    @Synchronized
    fun obtenerPendientes(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
    }

    @Synchronized
    fun marcarSincronizado(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
        val remaining = JSONArray()
        for (i in 0 until source.length()) {
            val event = source.getJSONObject(i)
            if (event.optString("id") !in ids) remaining.put(event)
        }
        prefs.edit().putString(KEY_EVENTS, remaining.toString()).apply()
    }
}
