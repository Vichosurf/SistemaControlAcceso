package com.example.sistemacontrolacceso

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android -> Firebase -> Wemos -> UART -> Arduino Uno.
 * Firebase confirma la nube; el estado físico se confirma con telemetría del Uno.
 */
class WirelessConnectionManager(
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onFrameReceived: (WirelessFrame) -> Unit,
    private val onMetricsUpdated: (NetworkMetrics) -> Unit
) {
    enum class TechType { BLUETOOTH_HC05, WIFI_UDP_SOCKET, INTERAPP_MESH }
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    data class WirelessFrame(
        val seqNum: Int, val command: String, val payload: String,
        val timestamp: Long, val checksum: String, val isVerified: Boolean
    )
    data class NetworkMetrics(
        val latencyMs: Long, val packetsSent: Int, val packetsReceived: Int,
        val packetLossPercent: Float, val rssiDbm: Int
    )

    private val handler = Handler(Looper.getMainLooper())
    private var prefs: android.content.SharedPreferences? = null
    private var database: FirebaseDatabase? = null
    private var connectionRef: DatabaseReference? = null
    private var commandRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null
    private var sensorListener: ValueEventListener? = null
    private var accessListener: ValueEventListener? = null
    private var state = ConnectionState.DISCONNECTED
    private var technology = TechType.WIFI_UDP_SOCKET
    private var sequence = 1
    private var tx = 0
    private var rx = 0
    private var lastWriteStarted = 0L

    fun connect(tech: TechType, targetAddress: String = "Firebase Realtime Database") {
        technology = tech
        setState(ConnectionState.CONNECTING)
        try {
            val context = FirebaseApp.getInstance().applicationContext
            prefs = context.getSharedPreferences("iot_pending_commands", Context.MODE_PRIVATE)
            sequence = prefs?.getInt("sequence", 1) ?: 1
            val db = FirebaseDatabase.getInstance()
            database = db
            commandRef = db.getReference("comandos/ultimo")
            connectionRef = db.getReference(".info/connected")
            connectionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    setState(if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
                    if (connected) {
                        SecurityManager.logEvent("WIRELESS", "FIREBASE_CONNECTED", "Android conectado a Firebase; el Wemos debe estar conectado para alcanzar el Arduino.", true)
                        observeHardware()
                        flushPending()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    setState(ConnectionState.ERROR)
                    SecurityManager.logEvent("WIRELESS", "FIREBASE_CONNECTION_ERROR", error.message, false)
                }
            }
            connectionRef?.addValueEventListener(connectionListener!!)
        } catch (error: Exception) {
            setState(ConnectionState.ERROR)
            SecurityManager.logEvent("WIRELESS", "FIREBASE_INIT_ERROR", error.message ?: targetAddress, false)
        }
    }

    private fun observeHardware() {
        val sensors = database?.getReference("sensores") ?: return
        sensorListener?.let { sensors.removeEventListener(it) }
        sensorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val distance = snapshot.child("distancia").getValue(Int::class.javaObjectType)
                val light = snapshot.child("luz").getValue(Int::class.javaObjectType)
                val angle = snapshot.child("anguloServo").getValue(Int::class.javaObjectType) ?: 0
                val mode = snapshot.child("modo").getValue(String::class.java) ?: "AUTO"
                val emergency = snapshot.child("emergencia").getValue(Boolean::class.java) ?: false
                val rssi = snapshot.child("rssi").getValue(Int::class.javaObjectType) ?: -127
                if (distance != null && light != null) {
                    rx++
                    val payload = "distancia=$distance;luz=$light;angulo=$angle;modo=$mode;emergencia=$emergency;rssi=$rssi"
                    onFrameReceived(WirelessFrame(rx, "HARDWARE_UPDATE", payload,
                        snapshot.child("actualizadoEn").getValue(Long::class.java) ?: System.currentTimeMillis(),
                        "RTDB", true))
                    onMetricsUpdated(NetworkMetrics(
                        if (lastWriteStarted == 0L) 0 else System.currentTimeMillis() - lastWriteStarted,
                        tx, rx, 0f, rssi
                    ))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                SecurityManager.logEvent("WIRELESS", "SENSOR_READ_ERROR", error.message, false)
            }
        }
        sensors.addValueEventListener(sensorListener!!)

        val access = database?.getReference("acceso") ?: return
        accessListener?.let { access.removeEventListener(it) }
        accessListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val angle = snapshot.child("anguloServo").getValue(Int::class.javaObjectType) ?: return
                val confirmed = snapshot.child("confirmadoArduino").getValue(Boolean::class.java) ?: false
                if (confirmed) {
                    SecurityManager.logEvent("HARDWARE", "ARDUINO_STATE_CONFIRMED",
                        "Arduino confirmó servomotor a $angle grados.", true)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                SecurityManager.logEvent("WIRELESS", "ACCESS_READ_ERROR", error.message, false)
            }
        }
        access.addValueEventListener(accessListener!!)
    }

    fun disconnect() {
        connectionRef?.let { ref -> connectionListener?.let(ref::removeEventListener) }
        database?.getReference("sensores")?.let { ref -> sensorListener?.let(ref::removeEventListener) }
        database?.getReference("acceso")?.let { ref -> accessListener?.let(ref::removeEventListener) }
        connectionListener = null
        sensorListener = null
        accessListener = null
        setState(ConnectionState.DISCONNECTED)
        SecurityManager.logEvent("WIRELESS", "DISCONNECT", "Escucha RTDB detenida", true)
    }

    fun sendFrame(command: String, payload: String): Boolean {
        if (database == null || commandRef == null) {
            SecurityManager.logEvent("WIRELESS", "COMMAND_REJECTED", "Conecta Firebase antes de enviar órdenes al Arduino.", false)
            return false
        }
        val seq = sequence++
        prefs?.edit()?.putInt("sequence", sequence)?.apply()
        val timestamp = System.currentTimeMillis()
        val raw = "$seq|$command|$payload|$timestamp"
        val frame = JSONObject().apply {
            put("seq", seq)
            put("comando", command)
            put("payload", payload)
            put("timestamp", timestamp)
            put("checksum", SecurityManager.sha256(raw + "_OT_KEY_2026").take(8).uppercase())
        }
        if (state != ConnectionState.CONNECTED) {
            enqueue(frame)
            SecurityManager.logEvent("WIRELESS", "COMMAND_QUEUED", "$command quedó en espera de Firebase.", true)
            return true
        }
        writeFrame(frame)
        return true
    }

    private fun writeFrame(frame: JSONObject) {
        lastWriteStarted = System.currentTimeMillis()
        val values = mapOf(
            "seq" to frame.optInt("seq"),
            "comando" to frame.optString("comando"),
            "payload" to frame.optString("payload"),
            "timestamp" to frame.optLong("timestamp"),
            "checksum" to frame.optString("checksum")
        )
        commandRef?.setValue(values)?.addOnSuccessListener {
            tx++
            onMetricsUpdated(NetworkMetrics(System.currentTimeMillis() - lastWriteStarted, tx, rx, 0f, -127))
            SecurityManager.logEvent("WIRELESS", "COMMAND_PUBLISHED", "Orden publicada en Firebase: ${frame.optString("comando")}. Esperando confirmación del Arduino.", true)
        }?.addOnFailureListener {
            enqueue(frame)
            SecurityManager.logEvent("WIRELESS", "COMMAND_FAILED", it.localizedMessage ?: "Error RTDB", false)
        }
    }

    private fun enqueue(frame: JSONObject) {
        prefs?.edit()?.putString("queue", JSONArray().put(frame).toString())?.apply()
    }

    private fun flushPending() {
        val pending = JSONArray(prefs?.getString("queue", "[]") ?: "[]")
        if (pending.length() == 0) return
        val last = pending.getJSONObject(pending.length() - 1)
        prefs?.edit()?.putString("queue", "[]")?.apply()
        writeFrame(last)
    }

    private fun setState(next: ConnectionState) {
        if (state != next) {
            state = next
            handler.post { onStateChanged(next) }
        }
    }

    fun getCurrentState() = state
    fun getCurrentTech() = technology
}
