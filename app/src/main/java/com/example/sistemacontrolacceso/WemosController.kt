package com.example.sistemacontrolacceso

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

class WemosController {

    private val apiSecret = "ClaveSecretaControlAcceso2026"

    fun enviarComando(
        ipWemos: String,
        accion: String, // "abrir", "cerrar" o "estado"
        onResultado: (exito: Boolean, mensaje: String) -> Unit
    ) {
        thread {
            try {
                val urlLimpia = ipWemos.trim().removePrefix("http://").removeSuffix("/")
                val url = URL("http://$urlLimpia/$accion")
                val conexion = url.openConnection() as HttpURLConnection
                conexion.requestMethod = "GET"
                conexion.connectTimeout = 3000
                conexion.readTimeout = 3000

                val codigo = conexion.responseCode
                if (codigo == 200) {
                    val respuestaTexto = conexion.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respuestaTexto)
                    val estadoBarrera = json.optString("barrera", accion)
                    val luz = json.optInt("luz", -1)

                    // Genera firma local de auditoría para mostrar en la app
                    val firma = generarSHA256("$estadoBarrera|$luz|$apiSecret")
                    val detalle = if (luz != -1) {
                        "Barrera: ${estadoBarrera.uppercase()} | Luz: $luz\nSHA-256: ${firma.take(16)}..."
                    } else {
                        "Barrera: ${estadoBarrera.uppercase()}\nSHA-256: ${firma.take(16)}..."
                    }
                    onResultado(true, detalle)
                } else {
                    onResultado(false, "Error HTTP: $codigo")
                }
                conexion.disconnect()
            } catch (e: Exception) {
                onResultado(false, "Sin conexión con WeMos (${e.localizedMessage})")
            }
        }
    }

    fun generarSHA256(datos: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(datos.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
