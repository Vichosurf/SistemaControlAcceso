package com.example.sistemacontrolacceso

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

/**
 * Motor de Conectividad Inalámbrica (Bluetooth y Wi-Fi Sockets / HTTP ESP8266 WeMos D1)
 * para el Sistema de Control de Acceso OT/IoT.
 */
class WirelessConnectionManager(
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onFrameReceived: (WirelessFrame) -> Unit,
    private val onMetricsUpdated: (NetworkMetrics) -> Unit
) {

    enum class TechType {
        BLUETOOTH_HC05,
        WIFI_UDP_SOCKET,
        MQTT_BROKER_IOT,
        INTERAPP_MESH
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    data class WirelessFrame(
        val seqNum: Int,
        val command: String,
        val payload: String,
        val timestamp: Long,
        val checksum: String,
        val isVerified: Boolean
    )

    data class NetworkMetrics(
        val latencyMs: Long,
        val packetsSent: Int,
        val packetsReceived: Int,
        val packetLossPercent: Float,
        val rssiDbm: Int
    )

    private var currentState = ConnectionState.DISCONNECTED
    private var currentTech = TechType.BLUETOOTH_HC05
    private var sequenceCounter = 1

    private var sentCount = 0
    private var receivedCount = 0
    private var simulatedLatency = 18L
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val random = Random()

    // Configuración Hardware ESP8266 (WeMos D1)
    var espTargetIp: String = "192.168.1.100"
    var onHardwareDataReceived: ((isAbierta: Boolean, luz: Int) -> Unit)? = null

    private var isHeartbeatActive = false
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (currentState == ConnectionState.CONNECTED) {
                if (currentTech == TechType.WIFI_UDP_SOCKET) {
                    // Polling real del estado del WeMos D1
                    fetchEsp8266Status()
                } else {
                    sendFrame("PING_HEARTBEAT", "status=OK")
                }
                handler.postDelayed(this, 2000)
            }
        }
    }

    fun connect(tech: TechType, targetAddress: String = "192.168.1.100") {
        currentTech = tech
        espTargetIp = targetAddress.ifBlank { "192.168.1.100" }
        updateState(ConnectionState.CONNECTING)

        SecurityManager.logEvent(
            "WIRELESS",
            "CONNECT_ATTEMPT",
            "Iniciando conexión ${tech.name} hacia $espTargetIp",
            true
        )

        if (tech == TechType.WIFI_UDP_SOCKET) {
            // Probar conexión HTTP directa con el WeMos D1
            fetchEsp8266Status { success ->
                if (success) {
                    updateState(ConnectionState.CONNECTED)
                    startHeartbeat()
                    SecurityManager.logEvent(
                        "WIRELESS",
                        "CONNECTED_HARDWARE",
                        "Enlace HTTP activo con WeMos D1 ESP8266 en IP: $espTargetIp",
                        true
                    )
                } else {
                    // Modo respaldo / simulado si el hardware aún no responde
                    updateState(ConnectionState.CONNECTED)
                    startHeartbeat()
                    SecurityManager.logEvent(
                        "WIRELESS",
                        "CONNECTED_SIMULATED",
                        "Enlace simulado activo para $espTargetIp (Hardware local sin respuesta en este puerto)",
                        true
                    )
                }
            }
        } else {
            handler.postDelayed({
                updateState(ConnectionState.CONNECTED)
                startHeartbeat()
                SecurityManager.logEvent(
                    "WIRELESS",
                    "CONNECTED",
                    "Enlace ${tech.name} establecido exitosamente. Protocolo OT activo.",
                    true
                )
            }, 1000)
        }
    }

    fun disconnect() {
        stopHeartbeat()
        updateState(ConnectionState.DISCONNECTED)
        SecurityManager.logEvent("WIRELESS", "DISCONNECT", "Conexión inalámbrica finalizada por usuario", true)
    }

    /**
     * Envía comandos HTTP directos al hardware WeMos D1 ESP8266:
     * endpoint "abrir", "cerrar" o "estado".
     */
    fun sendEsp8266Command(action: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanIp = espTargetIp.replace("http://", "").replace("/", "").trim()
        val urlString = "http://$cleanIp/$action"

        Thread {
            try {
                val startTime = System.currentTimeMillis()
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"

                val code = conn.responseCode
                val latency = System.currentTimeMillis() - startTime
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    sentCount++
                    receivedCount++

                    val metrics = NetworkMetrics(
                        latencyMs = latency,
                        packetsSent = sentCount,
                        packetsReceived = receivedCount,
                        packetLossPercent = 0f,
                        rssiDbm = -48
                    )
                    handler.post {
                        onMetricsUpdated(metrics)
                        onResult?.invoke(true, response)
                    }
                } else {
                    handler.post { onResult?.invoke(false, "HTTP $code") }
                }
            } catch (e: Exception) {
                handler.post { onResult?.invoke(false, e.message ?: "Error de red ESP8266") }
            }
        }.start()
    }

    /**
     * Consulta el estado actual (/estado) del WeMos D1 ESP8266.
     */
    fun fetchEsp8266Status(onComplete: ((Boolean) -> Unit)? = null) {
        sendEsp8266Command("estado") { success, json ->
            if (success && json != null) {
                try {
                    val isAbierta = json.contains("\"barrera\":\"abierta\"")
                    val luzMatch = Regex("\"luz\":(\\d+)").find(json)
                    val luz = luzMatch?.groupValues?.get(1)?.toIntOrNull() ?: 500

                    onHardwareDataReceived?.invoke(isAbierta, luz)

                    val frame = WirelessFrame(
                        seqNum = sequenceCounter++,
                        command = "HW_STATUS",
                        payload = "barrera=${if (isAbierta) "ABIERTA" else "CERRADA"}|luz=$luz",
                        timestamp = System.currentTimeMillis(),
                        checksum = SecurityManager.calculateFrameChecksum(json),
                        isVerified = true
                    )
                    onFrameReceived(frame)
                    onComplete?.invoke(true)
                } catch (e: Exception) {
                    onComplete?.invoke(false)
                }
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    fun sendFrame(command: String, payload: String): Boolean {
        if (currentState != ConnectionState.CONNECTED) {
            SecurityManager.logEvent("WIRELESS", "SEND_ERROR", "Intento de envío sin conexión activa", false)
            return false
        }

        if (currentTech == TechType.WIFI_UDP_SOCKET) {
            val action = when {
                command.contains("OPEN") || payload.contains("OPEN") -> "abrir"
                command.contains("CLOSE") || payload.contains("CLOSE") -> "cerrar"
                else -> "estado"
            }
            sendEsp8266Command(action)
            return true
        }

        val seq = sequenceCounter++
        val timestamp = System.currentTimeMillis()
        val rawData = "$seq|$command|$payload|$timestamp"
        val checksum = SecurityManager.calculateFrameChecksum(rawData)

        sentCount++
        simulatedLatency = (12 + random.nextInt(18)).toLong()

        val frame = WirelessFrame(
            seqNum = seq,
            command = command,
            payload = payload,
            timestamp = timestamp,
            checksum = checksum,
            isVerified = true
        )

        SecurityManager.logEvent(
            "WIRELESS",
            "FRAME_SENT",
            "Cmd: $command | Checksum: $checksum | Latencia: ${simulatedLatency}ms",
            true
        )

        handler.postDelayed({
            receivedCount++
            val rssi = -55 - random.nextInt(15)
            val packetLoss = if (sentCount > 0) ((sentCount - receivedCount).toFloat() / sentCount) * 100f else 0f

            val metrics = NetworkMetrics(
                latencyMs = simulatedLatency,
                packetsSent = sentCount,
                packetsReceived = receivedCount,
                packetLossPercent = packetLoss,
                rssiDbm = rssi
            )

            onMetricsUpdated(metrics)
            onFrameReceived(frame)
        }, simulatedLatency)

        return true
    }

    private fun startHeartbeat() {
        isHeartbeatActive = true
        handler.postDelayed(heartbeatRunnable, 2000)
    }

    private fun stopHeartbeat() {
        isHeartbeatActive = false
        handler.removeCallbacks(heartbeatRunnable)
    }

    private fun updateState(state: ConnectionState) {
        currentState = state
        onStateChanged(state)
    }

    fun getCurrentState() = currentState
    fun getCurrentTech() = currentTech

    fun getTechComparisonInfo(tech: TechType = currentTech): String {
        return when (tech) {
            TechType.BLUETOOTH_HC05 -> "Bluetooth HC-05/BLE: Distancia corta (10m), Consumo Mínimo (Muy bajo), Alta Estabilidad local directa sin infraestructura."
            TechType.WIFI_UDP_SOCKET -> "Wi-Fi HTTP/UDP ESP8266 (WeMos D1): Control físico directo por red local Wi-Fi, servomotor y sensores LDR/Ultrasónico en tiempo real."
            TechType.MQTT_BROKER_IOT -> "MQTT Broker Pub/Sub: Distancia Ilimitada (vía Cloud/LAN), Consumo Optimizado IoT, Máxima Estabilidad con QoS 1 y reconexión."
            TechType.INTERAPP_MESH -> "Red Mesh Inter-App: Distancia adaptable por saltos, Consumo Distribuido, Alta resiliencia sin punto único de falla."
        }
    }
}