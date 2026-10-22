package com.example.sistemacontrolacceso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para el Gestor de Comunicaciones Inalámbricas OT/IoT (Bluetooth, Wi-Fi UDP, MQTT).
 */
class WirelessConnectionManagerTest {

    @Test
    fun testTechTypeComparisonInfo() {
        val manager = WirelessConnectionManager(
            onStateChanged = {},
            onFrameReceived = {},
            onMetricsUpdated = {}
        )

        val bluetoothInfo = manager.getTechComparisonInfo(WirelessConnectionManager.TechType.BLUETOOTH_HC05)
        val wifiInfo = manager.getTechComparisonInfo(WirelessConnectionManager.TechType.WIFI_UDP_SOCKET)
        val mqttInfo = manager.getTechComparisonInfo(WirelessConnectionManager.TechType.MQTT_BROKER_IOT)
        val meshInfo = manager.getTechComparisonInfo(WirelessConnectionManager.TechType.INTERAPP_MESH)

        assertTrue(bluetoothInfo.contains("Bluetooth"))
        assertTrue(wifiInfo.contains("Wi-Fi"))
        assertTrue(mqttInfo.contains("MQTT"))
        assertTrue(meshInfo.contains("Mesh"))
    }

    @Test
    fun testDisconnectedStateCannotSendFrame() {
        val manager = WirelessConnectionManager(
            onStateChanged = {},
            onFrameReceived = {},
            onMetricsUpdated = {}
        )

        assertEquals(WirelessConnectionManager.ConnectionState.DISCONNECTED, manager.getCurrentState())
        val sent = manager.sendFrame("CMD_TEST", "payload=1")
        assertFalse("No se debe permitir enviar tramas en estado desconectado", sent)
    }
}
