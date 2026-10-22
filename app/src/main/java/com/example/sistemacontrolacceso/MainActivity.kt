package com.example.sistemacontrolacceso

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla Principal de Monitoreo, Activador e Interconexión Inalámbrica OT/IoT
 * con soporte para Notificaciones, Ubicación GPS y Reloj en Tiempo Real.
 */
class MainActivity : AppCompatActivity() {

    // Navegación y Encabezado
    private lateinit var rgNavigationTabs: RadioGroup
    private lateinit var panelMonitoreo: View
    private lateinit var panelActivador: View
    private lateinit var panelInalambrico: View
    private lateinit var btnSettings: Button
    private lateinit var btnAuditLogs: Button
    private lateinit var tvOperatorInfo: TextView

    // Ubicación GPS y Hora
    private lateinit var tvMainClock: TextView
    private lateinit var tvLocationCoords: TextView
    private lateinit var tvLocationZone: TextView
    private lateinit var btnRefreshLocation: Button

    // Vistas - Monitoreo
    private lateinit var seekBarDistance: SeekBar
    private lateinit var seekBarLight: SeekBar
    private lateinit var tvDistanceValue: TextView
    private lateinit var tvLightValue: TextView
    private lateinit var tvThresholdDistanceLabel: TextView
    private lateinit var tvThresholdLightLabel: TextView

    private lateinit var cardStatus: MaterialCardView
    private lateinit var tvStatusHeader: TextView
    private lateinit var tvBarrierState: TextView
    private lateinit var tvServoState: TextView
    private lateinit var ledGreen: View
    private lateinit var tvLedGreenLabel: TextView
    private lateinit var ledRed: View
    private lateinit var tvLedRedLabel: TextView
    private lateinit var tvLogicDescription: TextView

    // Vistas - Activador
    private lateinit var rgOperationMode: RadioGroup
    private lateinit var rbModeAuto: RadioButton
    private lateinit var rbModeForcedOpen: RadioButton
    private lateinit var rbModeForcedClosed: RadioButton
    private lateinit var switchEmergency: SwitchCompat
    private lateinit var seekBarThresholdDist: SeekBar
    private lateinit var seekBarThresholdLight: SeekBar
    private lateinit var tvThresholdDistCalib: TextView
    private lateinit var tvThresholdLightCalib: TextView
    private lateinit var etCommandPayload: EditText
    private lateinit var btnSendWirelessCmd: Button

    // Vistas - Conexión Inalámbrica
    private lateinit var tvWirelessStatus: TextView
    private lateinit var tvWirelessTech: TextView
    private lateinit var rgWirelessTech: RadioGroup
    private lateinit var btnConnectWireless: Button
    private lateinit var btnDisconnectWireless: Button
    private lateinit var tvMetricLatency: TextView
    private lateinit var tvMetricLoss: TextView
    private lateinit var tvMetricPackets: TextView
    private lateinit var tvMetricRssi: TextView
    private lateinit var tvWirelessFrameLog: TextView

    // Gestor Inalámbrico
    private lateinit var wirelessManager: WirelessConnectionManager

    // Umbrales Dinámicos OT
    private var umbralDistancia = 20
    private var umbralLuz = 400

    // Control de Estado Anterior para Notificaciones
    private var lastAlertState: Boolean? = null
    private var lastEmergencyState: Boolean? = null

    // Handlers
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm:ss | dd/MM/yyyy", Locale.getDefault())
            tvMainClock.text = "🕒 Hora del Sistema: ${sdf.format(Date())}"
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.applyLocale(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)
        SettingsManager.applyBackgroundTheme(this, root, isLoginScreen = false)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val operatorUser = intent.getStringExtra("OPERATOR_USER") ?: "admin"

        inicializarVistas(operatorUser)
        tvOperatorInfo.text = "Operador: $operatorUser | ISO 27400 / 27001 Activo"

        solicitarPermisosSistema()
        configurarNavegacion()
        configurarGestorInalambrico()
        configurarListenersMonitoreo()
        configurarListenersActivador()

        // Iniciar reloj en tiempo real y obtener GPS inicial
        clockHandler.post(clockRunnable)
        obtenerUbicacionGPS()

        // Evaluación inicial
        evaluarSistema()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun inicializarVistas(operatorUser: String) {
        rgNavigationTabs = findViewById(R.id.rgNavigationTabs)
        panelMonitoreo = findViewById(R.id.panelMonitoreo)
        panelActivador = findViewById(R.id.panelActivador)
        panelInalambrico = findViewById(R.id.panelInalambrico)
        btnSettings = findViewById(R.id.btnSettings)
        btnAuditLogs = findViewById(R.id.btnAuditLogs)
        tvOperatorInfo = findViewById(R.id.tvOperatorInfo)

        // Ubicación y Hora
        tvMainClock = findViewById(R.id.tvMainClock)
        tvLocationCoords = findViewById(R.id.tvLocationCoords)
        tvLocationZone = findViewById(R.id.tvLocationZone)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)

        // Monitoreo
        seekBarDistance = findViewById(R.id.seekBarDistance)
        seekBarLight = findViewById(R.id.seekBarLight)
        tvDistanceValue = findViewById(R.id.tvDistanceValue)
        tvLightValue = findViewById(R.id.tvLightValue)
        tvThresholdDistanceLabel = findViewById(R.id.tvThresholdDistanceLabel)
        tvThresholdLightLabel = findViewById(R.id.tvThresholdLightLabel)

        cardStatus = findViewById(R.id.cardStatus)
        tvStatusHeader = findViewById(R.id.tvStatusHeader)
        tvBarrierState = findViewById(R.id.tvBarrierState)
        tvServoState = findViewById(R.id.tvServoState)
        ledGreen = findViewById(R.id.ledGreen)
        tvLedGreenLabel = findViewById(R.id.tvLedGreenLabel)
        ledRed = findViewById(R.id.ledRed)
        tvLedRedLabel = findViewById(R.id.tvLedRedLabel)
        tvLogicDescription = findViewById(R.id.tvLogicDescription)

        // Activador
        rgOperationMode = findViewById(R.id.rgOperationMode)
        rbModeAuto = findViewById(R.id.rbModeAuto)
        rbModeForcedOpen = findViewById(R.id.rbModeForcedOpen)
        rbModeForcedClosed = findViewById(R.id.rbModeForcedClosed)
        switchEmergency = findViewById(R.id.switchEmergency)
        seekBarThresholdDist = findViewById(R.id.seekBarThresholdDist)
        seekBarThresholdLight = findViewById(R.id.seekBarThresholdLight)
        tvThresholdDistCalib = findViewById(R.id.tvThresholdDistCalib)
        tvThresholdLightCalib = findViewById(R.id.tvThresholdLightCalib)
        etCommandPayload = findViewById(R.id.etCommandPayload)
        btnSendWirelessCmd = findViewById(R.id.btnSendWirelessCmd)

        // Conexión Inalámbrica
        tvWirelessStatus = findViewById(R.id.tvWirelessStatus)
        tvWirelessTech = findViewById(R.id.tvWirelessTech)
        rgWirelessTech = findViewById(R.id.rgWirelessTech)
        btnConnectWireless = findViewById(R.id.btnConnectWireless)
        btnDisconnectWireless = findViewById(R.id.btnDisconnectWireless)
        tvMetricLatency = findViewById(R.id.tvMetricLatency)
        tvMetricLoss = findViewById(R.id.tvMetricLoss)
        tvMetricPackets = findViewById(R.id.tvMetricPackets)
        tvMetricRssi = findViewById(R.id.tvMetricRssi)
        tvWirelessFrameLog = findViewById(R.id.tvWirelessFrameLog)

        btnSettings.setOnClickListener {
            val root = findViewById<View>(R.id.main)
            SettingsManager.showSettingsDialog(
                activity = this,
                rootView = root,
                isLoginScreen = false,
                operatorUser = operatorUser
            )
        }

        btnAuditLogs.setOnClickListener { mostrarDialogoAuditoria() }
        btnRefreshLocation.setOnClickListener {
            obtenerUbicacionGPS()
            Toast.makeText(this, "Ubicación GPS actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun solicitarPermisosSistema() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }
    }

    private fun obtenerUbicacionGPS() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            var locationFound = false

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val locGps = if (isGpsEnabled) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                val locNet = if (isNetEnabled) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
                val bestLoc = locGps ?: locNet

                if (bestLoc != null) {
                    val lat = String.format(Locale.US, "%.4f", bestLoc.latitude)
                    val lon = String.format(Locale.US, "%.4f", bestLoc.longitude)
                    val alt = String.format(Locale.US, "%.0f", bestLoc.altitude)

                    tvLocationCoords.text = "📍 GPS Activo: Lat $lat | Lon $lon | Alt ${alt}m"
                    tvLocationZone.text = "🏢 Zona Operativa: Planta Principal OT - Acceso Norte"
                    locationFound = true

                    SecurityManager.logEvent("LOCATION", "GPS_UPDATE", "Ubicación fijada: Lat $lat, Lon $lon", true)
                }
            }

            if (!locationFound) {
                // Ubicación Simulada Industrial OT de Alta Precisión
                tvLocationCoords.text = "📍 GPS Fijado (Zona OT): Lat -33.4489, Lon -70.6693 | Alt 520m"
                tvLocationZone.text = "🏢 Zona Operativa: Planta Industrial OT - Acceso Controlado"
                SecurityManager.logEvent("LOCATION", "GPS_SIMULATED", "Ubicación industrial registrada: -33.4489, -70.6693", true)
            }
        } catch (e: Exception) {
            tvLocationCoords.text = "📍 Coordenadas OT: Lat -33.4489, Lon -70.6693"
        }
    }

    private fun configurarNavegacion() {
        rgNavigationTabs.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTabMonitoreo -> {
                    panelMonitoreo.visibility = View.VISIBLE
                    panelActivador.visibility = View.GONE
                    panelInalambrico.visibility = View.GONE
                }
                R.id.rbTabActivador -> {
                    panelMonitoreo.visibility = View.GONE
                    panelActivador.visibility = View.VISIBLE
                    panelInalambrico.visibility = View.GONE
                }
                R.id.rbTabInalambrico -> {
                    panelMonitoreo.visibility = View.GONE
                    panelActivador.visibility = View.GONE
                    panelInalambrico.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun configurarGestorInalambrico() {
        wirelessManager = WirelessConnectionManager(
            onStateChanged = { state ->
                runOnUiThread {
                    when (state) {
                        WirelessConnectionManager.ConnectionState.CONNECTED -> {
                            tvWirelessStatus.text = "Estado: CONECTADO Y SEGURO"
                            tvWirelessStatus.setTextColor(ContextCompat.getColor(this, R.color.wireless_connected))
                            Toast.makeText(this, "Conexión Inalámbrica Establecida (ISO 27400 Checksum)", Toast.LENGTH_SHORT).show()

                            NotificationHelper.sendNotification(
                                context = this,
                                notificationId = 3001,
                                title = "📶 Conexión Inalámbrica Establecida",
                                message = "Enlace seguro activo mediante ${wirelessManager.getCurrentTech().name}.",
                                isAlert = false
                            )
                        }
                        WirelessConnectionManager.ConnectionState.CONNECTING -> {
                            tvWirelessStatus.text = "Estado: ESTABLECIENDO SOCKET..."
                            tvWirelessStatus.setTextColor(ContextCompat.getColor(this, R.color.wireless_connecting))
                        }
                        WirelessConnectionManager.ConnectionState.DISCONNECTED -> {
                            tvWirelessStatus.text = "Estado: DESCONECTADO"
                            tvWirelessStatus.setTextColor(ContextCompat.getColor(this, R.color.wireless_disconnected))
                        }
                        WirelessConnectionManager.ConnectionState.ERROR -> {
                            tvWirelessStatus.text = "Estado: ERROR DE CONEXIÓN"
                            tvWirelessStatus.setTextColor(ContextCompat.getColor(this, R.color.wireless_disconnected))
                        }
                    }
                }
            },
            onFrameReceived = { frame ->
                runOnUiThread {
                    tvWirelessFrameLog.text = "[RX] Seq:#${frame.seqNum} | Cmd:${frame.command} | Checksum:${frame.checksum} (OK)"
                }
            },
            onMetricsUpdated = { metrics ->
                runOnUiThread {
                    tvMetricLatency.text = "Latencia: ${metrics.latencyMs} ms"
                    tvMetricLoss.text = "Pérdida: ${String.format(Locale.US, "%.1f", metrics.packetLossPercent)}%"
                    tvMetricPackets.text = "Paquetes Tx/Rx: ${metrics.packetsSent}/${metrics.packetsReceived}"
                    tvMetricRssi.text = "RSSI: ${metrics.rssiDbm} dBm"
                }
            }
        )

        btnConnectWireless.setOnClickListener {
            val tech = when (rgWirelessTech.checkedRadioButtonId) {
                R.id.rbWifiSocket -> WirelessConnectionManager.TechType.WIFI_UDP_SOCKET
                R.id.rbMqtt -> WirelessConnectionManager.TechType.MQTT_BROKER_IOT
                R.id.rbInterApp -> WirelessConnectionManager.TechType.INTERAPP_MESH
                else -> WirelessConnectionManager.TechType.BLUETOOTH_HC05
            }
            tvWirelessTech.text = "Tecnología: ${tech.name}"
            wirelessManager.connect(tech)
            val infoJustificacion = wirelessManager.getTechComparisonInfo(tech)
            Toast.makeText(this, infoJustificacion, Toast.LENGTH_LONG).show()
        }

        btnDisconnectWireless.setOnClickListener {
            wirelessManager.disconnect()
        }

        btnSendWirelessCmd.setOnClickListener {
            val cmd = etCommandPayload.text.toString().ifBlank { "CMD_OVERRIDE" }
            val sent = wirelessManager.sendFrame(cmd, "mode=MANUAL_TRIGGER")
            if (sent) {
                Toast.makeText(this, "Comando enviado inalámbricamente.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Conecte primero el gestor inalámbrico.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configurarListenersMonitoreo() {
        seekBarDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDistanceValue.text = "$progress cm"
                evaluarSistema()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarLight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvLightValue.text = progress.toString()
                evaluarSistema()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun configurarListenersActivador() {
        rgOperationMode.setOnCheckedChangeListener { _, _ -> evaluarSistema() }
        switchEmergency.setOnCheckedChangeListener { _, isChecked ->
            SecurityManager.logEvent("OPERATOR", "EMERGENCY_SWITCH", "Switch Anulación Emergencia = $isChecked", true)

            if (lastEmergencyState != isChecked) {
                lastEmergencyState = isChecked
                NotificationHelper.sendNotification(
                    context = this,
                    notificationId = 2002,
                    title = if (isChecked) "⚠️ Emergencia Activada" else "✅ Emergencia Desactivada",
                    message = if (isChecked) "Inhabilitación de barrera activada manualmente." else "Modo de operación normal restablecido.",
                    isAlert = isChecked
                )
            }

            evaluarSistema()
        }

        seekBarThresholdDist.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                umbralDistancia = progress
                tvThresholdDistCalib.text = "Umbral Distancia: $progress cm"
                tvThresholdDistanceLabel.text = "Umbral configurado: < $progress cm"
                evaluarSistema()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarThresholdLight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                umbralLuz = progress
                tvThresholdLightCalib.text = "Umbral Luz: $progress ADC"
                tvThresholdLightLabel.text = "Umbral configurado: Oscuridad < $progress"
                evaluarSistema()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Evalúa el estado del sistema combinando:
     * 1. Switch de Anulación de Emergencia.
     * 2. Modo de Operación (Automático vs Forzado Manual).
     * 3. Lógica de Sensores en Modo Automático (Distancia < Umbral Y Luz < Umbral).
     */
    private fun evaluarSistema() {
        val distancia = seekBarDistance.progress
        val luz = seekBarLight.progress
        val isEmergencyActive = switchEmergency.isChecked

        val esAlerta: Boolean

        if (isEmergencyActive) {
            esAlerta = false
        } else {
            when (rgOperationMode.checkedRadioButtonId) {
                R.id.rbModeForcedOpen -> esAlerta = true
                R.id.rbModeForcedClosed -> esAlerta = false
                else -> {
                    // Modo Automático por Sensores
                    esAlerta = (distancia < umbralDistancia) && (luz < umbralLuz)
                }
            }
        }

        // Disparar Notificación de Alerta de Barrera Abierta
        if (esAlerta && lastAlertState != true) {
            NotificationHelper.sendNotification(
                context = this,
                notificationId = 2001,
                title = "🚨 BARRERA ABIERTA - ACCESO AUTORIZADO",
                message = "Detección a ${distancia}cm en zona oscura ($luz ADC). Servomotor a 90°. Ubicación: Planta OT.",
                isAlert = true
            )
        }
        lastAlertState = esAlerta

        actualizarUIEstado(esAlerta, distancia, luz, isEmergencyActive)
    }

    private fun actualizarUIEstado(esAlerta: Boolean, distancia: Int, luz: Int, isEmergency: Boolean) {
        if (esAlerta) {
            tvStatusHeader.text = "ESTADO: ALERTA / ACCESO AUTORIZADO"
            tvBarrierState.text = "BARRERA ABIERTA"
            tvServoState.text = "Servomotor: 90°"

            val headerColor = ContextCompat.getColor(this, R.color.state_alert_header)
            tvStatusHeader.setTextColor(headerColor)
            tvBarrierState.setTextColor(headerColor)

            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.state_alert_bg))
            cardStatus.strokeColor = ContextCompat.getColor(this, R.color.state_alert_stroke)

            // LED Rojo ON, Verde OFF
            ledRed.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.led_red_on))
            tvLedRedLabel.text = "LED Rojo (ON)"
            tvLedRedLabel.setTextColor(headerColor)
            tvLedRedLabel.setTypeface(null, Typeface.BOLD)

            ledGreen.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.led_green_off))
            tvLedGreenLabel.text = "LED Verde (OFF)"
            tvLedGreenLabel.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            tvLedGreenLabel.setTypeface(null, Typeface.NORMAL)

            tvLogicDescription.text = "¡ALERTA! Objeto a $distancia cm (< $umbralDistancia cm) en zona oscura ($luz < $umbralLuz)."
        } else {
            tvStatusHeader.text = if (isEmergency) "ESTADO: ANULACIÓN DE EMERGENCIA" else "ESTADO NORMAL (REPOSO)"
            tvBarrierState.text = "BARRERA CERRADA"
            tvServoState.text = "Servomotor: 0°"

            val headerColor = ContextCompat.getColor(this, R.color.state_normal_header)
            tvStatusHeader.setTextColor(headerColor)
            tvBarrierState.setTextColor(headerColor)

            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.state_normal_bg))
            cardStatus.strokeColor = ContextCompat.getColor(this, R.color.state_normal_stroke)

            // LED Verde ON, Rojo OFF
            ledGreen.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.led_green_on))
            tvLedGreenLabel.text = "LED Verde (ON)"
            tvLedGreenLabel.setTextColor(headerColor)
            tvLedGreenLabel.setTypeface(null, Typeface.BOLD)

            ledRed.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.led_red_off))
            tvLedRedLabel.text = "LED Rojo (OFF)"
            tvLedRedLabel.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            tvLedRedLabel.setTypeface(null, Typeface.NORMAL)

            tvLogicDescription.text = if (isEmergency) {
                "Anulación manual de emergencia activa por switch de seguridad."
            } else {
                "Condiciones no cumplidas (Distancia: $distancia cm, Luz: $luz)."
            }
        }
    }

    private fun mostrarDialogoAuditoria() {
        val logs = SecurityManager.getAuditLogs()
        val textLog = logs.joinToString("\n\n") {
            "[${it.timestamp}] ${it.eventType}\nUsuario: ${it.user} | Éxito: ${it.isSuccess}\n${it.detail}"
        }

        AlertDialog.Builder(this)
            .setTitle("🔒 Log de Auditoría OT (ISO/IEC 27001)")
            .setMessage(textLog.ifBlank { "No hay eventos registrados." })
            .setPositiveButton("Aceptar", null)
            .show()
    }
}
