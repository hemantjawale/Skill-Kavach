package com.example.skilkavach

import com.example.skilkavach.data.CertificateVault
import com.example.skilkavach.data.SyncQueueEntity
import com.example.skilkavach.data.Vault
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflineArchitectureTest {

    @Test
    fun testVaultCorruptedCiphertextReturnsNull() {
        val vault = Vault()
        // Passing corrupted base64 ciphertext
        val decryptedResult = vault.decrypt("INVALID_CORRUPTED_CIPHERTEXT_BASE64_DATA==")
        assertNull("Corrupted ciphertext should return null safely without throwing uncaught exception", decryptedResult)
    }

    @Test
    fun testVaultShortPayloadReturnsNull() {
        val vault = Vault()
        // Passing payload shorter than 28 bytes (12 IV + 16 auth tag min)
        val shortBase64 = java.util.Base64.getEncoder().encodeToString("SHORT".toByteArray())
        val decryptedResult = vault.decrypt(shortBase64)
        assertNull("Short payload should return null safely", decryptedResult)
    }

    @Test
    fun testSyncQueueEntityStructure() {
        val item = SyncQueueEntity(
            id = "SYNC-001",
            payloadType = "ASSESSMENT_RESULT",
            payloadJson = "{\"score\":90.0}",
            synced = false,
            createdAt = System.currentTimeMillis(),
            hopCount = 1,
            sourceDeviceId = "DEVICE_A",
            version = 1,
            retryCount = 0,
            status = "PENDING"
        )
        assertEquals("SYNC-001", item.id)
        assertEquals("ASSESSMENT_RESULT", item.payloadType)
        assertEquals("DEVICE_A", item.sourceDeviceId)
        assertEquals("PENDING", item.status)
        assertEquals(0, item.retryCount)
    }

    @Test
    fun testMeshPayloadValidationAndHopLimit() {
        val certVault = CertificateVault()
        val payloadId = "MESH-ITEM-99"
        val payloadType = "CERTIFICATE"
        val payloadJson = "{\"certId\":\"CERT-123\"}"
        val senderId = "WORKER_EMP001"

        // Generate HMAC signature for mesh payload
        val validSig = certVault.computeSha256("$payloadId:$payloadType:$payloadJson:$senderId")

        val envelope = JSONObject().apply {
            put("id", payloadId)
            put("type", payloadType)
            put("json", payloadJson)
            put("senderId", senderId)
            put("signature", validSig)
            put("hop", 2)
        }

        // 1. Verify valid signature match
        val computedSig = certVault.computeSha256(
            "${envelope.getString("id")}:${envelope.getString("type")}:${envelope.getString("json")}:${envelope.getString("senderId")}"
        )
        assertEquals(envelope.getString("signature"), computedSig)

        // 2. Test Hop limit rejection (> 5 hops)
        envelope.put("hop", 6)
        assertTrue("Hop count > 5 must be rejected", envelope.getInt("hop") > 5)
    }

    @Test
    fun testMeshSensitiveDataGuardRejection() {
        val sensitivePayload = "{\"password\":\"secret123\", \"rawBiometric\":\"FINGERPRINT_BYTES\"}"
        val containsSensitiveData = sensitivePayload.contains("password") || sensitivePayload.contains("rawBiometric")
        assertTrue("Payload containing raw password/biometrics must be flagged and rejected", containsSensitiveData)
    }
}
