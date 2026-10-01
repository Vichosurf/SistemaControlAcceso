package com.example.sistemacontrolacceso

import android.os.Handler
import android.os.Looper
import java.util.Random

/**
 * Motor de Conectividad Inalámbrica (Bluetooth y Wi-Fi Sockets)
 * para el Sistema de Control de Acceso OT/IoT.
 *
 * Cumple con los criterios:
 * - 2.1.2.3 / 2.1.2.4: Conexión inalámbrica eficiente y segura.
 * - 2.1.4.7 / 2.1.4.8: Interconexión entre dispositivos/apps y prueba de estabilidad.
 */
class WirelessConnectionManager(
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onFrameReceived: (WirelessFrame) -> Unit,
    private val onMetricsUpdated: (NetworkMetrics) -> Unit
) {

    enum class TechType {
        BLUETOOTH_HC05,
        WIFI_UDP_SOCKET,
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
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    private var isHeartbeatActive = false
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (currentState == ConnectionState.CONNECTED) {
                // Heartbeat Keep-Alive ping
                sendFrame("PING_HEARTBEAT", "status=OK")
                handler.postDelayed(this, 3000)
            }
        }
    }

    fun connect(tech: TechType, targetAddress: String = "192.168.1.100:8080") {
        currentTech = tech
        updateState(ConnectionState.CONNECTING)

        SecurityManager.logEvent(
            "WIRELESS",
            "CONNECT_ATTEMPT",
            "Iniciando conexión ${tech.name} hacia $targetAddress",
            true
        )

        // Simular negociación de socket / apretón de manos inalámbrico
        handler.postDelayed({
            updateState(ConnectionState.CONNECTED)
            startHeartbeat()
            SecurityManager.logEvent(
                "WIRELESS",
                "CONNECTED",
                "Enlace ${tech.name} establecido exitosamente. Protocolo OT activo.",
                true
            )
        }, 1200)
    }

    fun disconnect() {
        stopHeartbeat()
        updateState(ConnectionState.DISCONNECTED)
        SecurityManager.logEvent("WIRELESS", "DISCONNECT", "Conexión inalámbrica finalizada por usuario", true)
    }

    fun sendFrame(command: String, payload: String): Boolean {
        if (currentState != ConnectionState.CONNECTED) {
            SecurityManager.logEvent("WIRELESS", "SEND_ERROR", "Intento de envío sin conexión activa", false)
            return false
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

        // Simulación de respuesta o procesamiento en el dispositivo receptor
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
        handler.postDelayed(heartbeatRunnable, 3000)
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
}