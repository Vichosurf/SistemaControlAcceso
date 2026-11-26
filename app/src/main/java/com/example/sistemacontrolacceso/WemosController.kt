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
        accion: String, // "abrir", "cerrar", "auto", "estado", etc.
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
                    val modo = json.optString("modo", "auto")
                    val distancia = json.optInt("distancia", -1)
                    val luz = json.optInt("luz", -1)
                    val pendientesOffline = json.optInt("pendientes_offline", 0)
                    val firmaRecibida = json.optString("firma_sha256", "")

                    // Firma criptográfica SHA-256
                    val rawFirma = "$estadoBarrera|$distancia|$luz|$apiSecret"
                    val firmaLocal = generarSHA256(rawFirma)
                    val firmaDefinitiva = if (firmaRecibida.isNotEmpty()) firmaRecibida else firmaLocal

                    val offlineText = if (pendientesOffline > 0) " | Offline: $pendientesOffline" else ""
                    val distText = if (distancia != -1) "$distancia cm" else "N/A"
                    val luzText = if (luz != -1) "$luz ADC" else "N/A"

                    val detalle = "Barrera: ${estadoBarrera.uppercase()} | Modo: ${modo.uppercase()}\n" +
                            "Dist: $distText | Luz: $luzText$offlineText\n" +
                            "SHA-256: ${firmaDefinitiva.take(16)}..."

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
