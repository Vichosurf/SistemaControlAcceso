package com.example.sistemacontrolacceso

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

data class EstadoWemos(
    val barrera: String,
    val modo: String,
    val distancia: Int,
    val luz: Int,
    val abierta: Boolean,
    val motivo: String,
    val ip: String,
    val firmaSha256: String,
    val totalEventos: Int
)

class WemosController {
    fun consultarEstado(ipWemos: String, onResultado: (Boolean, EstadoWemos?, String) -> Unit) {
        ejecutarSolicitud(ipWemos, "estado") { ok, json, mensaje ->
            if (!ok || json == null) {
                onResultado(false, null, mensaje)
            } else {
                onResultado(true, EstadoWemos(
                    barrera = json.optString("barrera", "desconocida"),
                    modo = json.optString("modo", "automatico"),
                    distancia = json.optInt("distancia", -1),
                    luz = json.optInt("luz", -1),
                    abierta = json.optBoolean("abierta", json.optString("barrera") == "abierta"),
                    motivo = json.optString("motivo", json.optString("evento", "N/A")),
                    ip = json.optString("ip", ""),
                    firmaSha256 = json.optString("firma_sha256", ""),
                    totalEventos = json.optInt("total_eventos", 0)
                ), "Conectado")
            }
        }
    }

    fun enviarComando(ipWemos: String, accion: String, onResultado: (Boolean, String) -> Unit) {
        ejecutarSolicitud(ipWemos, accion) { ok, json, mensaje ->
            if (!ok || json == null) onResultado(false, mensaje)
            else {
                val firma = json.optString("firma_sha256", "")
                onResultado(true,
                    "Barrera: ${json.optString("barrera").uppercase()} | Modo: ${json.optString("modo").uppercase()}\n" +
                    "Distancia: ${json.optInt("distancia", -1)} cm | Luz: ${json.optInt("luz", -1)} ADC\n" +
                    "Motivo: ${json.optString("motivo", "N/A")}\nSHA-256: ${firma.take(16)}...")
            }
        }
    }

    private fun ejecutarSolicitud(
        ipWemos: String,
        endpoint: String,
        callback: (Boolean, JSONObject?, String) -> Unit
    ) {
        thread(name = "Wemos-$endpoint") {
            var conexion: HttpURLConnection? = null
            try {
                val host = ipWemos.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                require(host.isNotBlank()) { "Ingrese la dirección IP del WeMos" }
                conexion = (URL("http://$host/$endpoint").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                }
                val codigo = conexion.responseCode
                val stream = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                val texto = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (codigo in 200..299) callback(true, JSONObject(texto), "OK")
                else callback(false, null, "Error HTTP: $codigo")
            } catch (e: Exception) {
                callback(false, null, "Sin conexión con WeMos: ${e.localizedMessage ?: "error desconocido"}")
            } finally {
                conexion?.disconnect()
            }
        }
    }

    // SHA-256 es un checksum; no equivale a HMAC ni autentica por sí solo al dispositivo.
    fun generarSHA256(datos: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(datos.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
