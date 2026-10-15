package com.example.sistemacontrolacceso

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

/**
 * Pantalla Principal de Monitoreo, Activador e Interconexión Inalámbrica OT/IoT.
 */
class MainActivity : AppCompatActivity() {

    // Navegación
    private lateinit var rgNavigationTabs: RadioGroup
    private lateinit var panelMonitoreo: View
    private lateinit var panelActivador: View
    private lateinit var panelInalambrico: View
    private lateinit var btnAuditLogs: Button
    private lateinit var tvOperatorInfo: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val operatorUser = intent.getStringExtra("OPERATOR_USER") ?: "admin"

        inicializarVistas()
        tvOperatorInfo.text = "Operador: $operatorUser | ISO 27001 Activo"

        configurarNavegacion()
        configurarGestorInalambrico()
        configurarListenersMonitoreo()
        configurarListenersActivador()

        // Evaluación inicial
        evaluarSistema()
    }

    private fun inicializarVistas() {
        rgNavigationTabs = findViewById(R.id.rgNavigationTabs)
        panelMonitoreo = findViewById(R.id.panelMonitoreo)
        panelActivador = findViewById(R.id.panelActivador)
        panelInalambrico = findViewById(R.id.panelInalambrico)
        btnAuditLogs = findViewById(R.id.btnAuditLogs)
        tvOperatorInfo = findViewById(R.id.tvOperatorInfo)

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

        btnAuditLogs.setOnClickListener { mostrarDialogoAuditoria() }
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
                            Toast.makeText(this, "Conexión Inalámbrica Establecida (ISO 27001 Checksum)", Toast.LENGTH_SHORT).show()
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
                    tvMetricLoss.text = "Pérdida: ${String.format(java.util.Locale.US, "%.1f", metrics.packetLossPercent)}%"
                    tvMetricPackets.text = "Paquetes Tx/Rx: ${metrics.packetsSent}/${metrics.packetsReceived}"
                    tvMetricRssi.text = "RSSI: ${metrics.rssiDbm} dBm"
                }
            }
        )

        btnConnectWireless.setOnClickListener {
            val tech = when (rgWirelessTech.checkedRadioButtonId) {
                R.id.rbWifiSocket -> WirelessConnectionManager.TechType.WIFI_UDP_SOCKET
                R.id.rbInterApp -> WirelessConnectionManager.TechType.INTERAPP_MESH
                else -> WirelessConnectionManager.TechType.BLUETOOTH_HC05
            }
            tvWirelessTech.text = "Tecnología: ${tech.name}"
            wirelessManager.connect(tech)
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