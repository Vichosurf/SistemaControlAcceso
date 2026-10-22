package com.example.sistemacontrolacceso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de Seguridad e Integridad de Datos (ISO 27400 e ISO/IEC 27001).
 */
class SecurityManagerTest {

    @Test
    fun testSha256Hashing() {
        val input = "Admin123!"
        val hash = SecurityManager.sha256(input)
        assertNotNull(hash)
        assertEquals(64, hash.length) // 256 bits = 64 hex characters
    }

    @Test
    fun testFrameChecksumCalculation() {
        val payload = "1|CMD_OPEN_GATE|mode=AUTO|1700000000"
        val checksum = SecurityManager.calculateFrameChecksum(payload)
        assertNotNull(checksum)
        assertEquals(8, checksum.length)
        assertEquals(checksum.uppercase(), checksum)
    }

    @Test
    fun testAuditLogging() {
        SecurityManager.logEvent("TEST_USER", "UNIT_TEST", "Verificación de registro de auditoría", true)
        val logs = SecurityManager.getAuditLogs()
        assertTrue(logs.isNotEmpty())
        val lastLog = logs.first()
        assertEquals("TEST_USER", lastLog.user)
        assertEquals("UNIT_TEST", lastLog.eventType)
        assertTrue(lastLog.isSuccess)
    }
}
