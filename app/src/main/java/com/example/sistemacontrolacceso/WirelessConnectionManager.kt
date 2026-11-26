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

/** Comunicación real Android-Wemos mediante Firebase Realtime Database. */
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
    private var state = ConnectionState.DISCONNECTED
    private var technology = TechType.WIFI_UDP_SOCKET
    private var sequence = 1
    private var tx = 0
    private var rx = 0

    fun connect(tech: TechType, targetAddress: String = "Firebase Realtime Database") {
        technology = tech
        setState(ConnectionState.CONNECTING)
        try {
            val app = FirebaseApp.getInstance()
            val context = app.applicationContext
            prefs = context.getSharedPreferences("iot_pending_commands", Context.MODE_PRIVATE)
            sequence = prefs?.getInt("sequence", 1) ?: 1
            val db = FirebaseDatabase.getInstance()
            try { db.setPersistenceEnabled(true) } catch (_: IllegalStateException) { }
            database = db
            commandRef = db.getReference("comandos/ultimo")
            connectionRef = db.getReference(".info/connected")
            connectionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    setState(if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
                    if (connected) {
                        SecurityManager.logEvent("WIRELESS", "FIREBASE_CONNECTED", "Conexión RTDB activa por HTTPS/TLS", true)
                        observeSensors()
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

    private fun observeSensors() {
        val ref = database?.getReference("sensores") ?: return
        sensorListener?.let { ref.removeEventListener(it) }
        sensorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val distance = snapshot.child("distancia").getValue(Int::class.javaObjectType)
                    ?: snapshot.child("distanciaCm").getValue(Int::class.javaObjectType)
                val light = snapshot.child("luz").getValue(Int::class.javaObjectType)
                    ?: snapshot.child("luminosidad").getValue(Int::class.javaObjectType)
                if (distance != null || light != null) {
                    rx++
                    val payload = "distancia=${distance ?: -1};luz=${light ?: -1}"
                    onFrameReceived(WirelessFrame(rx, "SENSOR_UPDATE", payload, System.currentTimeMillis(), "", true))
                    onMetricsUpdated(NetworkMetrics(0, tx, rx, 0f, 0))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                SecurityManager.logEvent("WIRELESS", "SENSOR_READ_ERROR", error.message, false)
            }
        }
        ref.addValueEventListener(sensorListener!!)
    }

    fun disconnect() {
        connectionRef?.let { ref -> connectionListener?.let(ref::removeEventListener) }
        database?.getReference("sensores")?.let { ref -> sensorListener?.let(ref::removeEventListener) }
        connectionListener = null
        sensorListener = null
        setState(ConnectionState.DISCONNECTED)
        SecurityManager.logEvent("WIRELESS", "DISCONNECT", "Escucha RTDB detenida", true)
    }

    fun sendFrame(command: String, payload: String): Boolean {
        val seq = sequence++
        prefs?.edit()?.putInt("sequence", sequence)?.apply()
        val timestamp = System.currentTimeMillis()
        val raw = "$seq|$command|$payload|$timestamp"
        val frame = JSONObject().apply {
            put("seq", seq); put("comando", command); put("payload", payload)
            put("timestamp", timestamp)
            put("checksum", SecurityManager.sha256(raw + "_OT_KEY_2026").take(8).uppercase())
        }
        if (state != ConnectionState.CONNECTED || commandRef == null) {
            enqueue(frame)
            SecurityManager.logEvent("WIRELESS", "COMMAND_QUEUED", "$command guardado para reconexión", true)
            return true
        }
        writeFrame(frame)
        return true
    }

    private fun writeFrame(frame: JSONObject) {
        val started = System.currentTimeMillis()
        val values = mapOf(
            "seq" to frame.optInt("seq"),
            "comando" to frame.optString("comando"),
            "payload" to frame.optString("payload"),
            "timestamp" to frame.optLong("timestamp"),
            "checksum" to frame.optString("checksum")
        )
        commandRef?.setValue(values)?.addOnSuccessListener {
            tx++
            onMetricsUpdated(NetworkMetrics(System.currentTimeMillis() - started, tx, rx, 0f, 0))
            SecurityManager.logEvent("WIRELESS", "COMMAND_SENT", "Comando ${frame.optString("comando")} enviado", true)
        }?.addOnFailureListener {
            enqueue(frame)
            SecurityManager.logEvent("WIRELESS", "COMMAND_FAILED", it.localizedMessage ?: "Error RTDB", false)
        }
    }

    private fun enqueue(frame: JSONObject) {
        // Los comandos representan estado deseado: se conserva el más reciente.
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
