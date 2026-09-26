package com.example.skilkavach

import com.example.skilkavach.data.CertificateStatus
import com.example.skilkavach.data.CertificateVault
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator

class CertificateVaultTest {

    private val vault = CertificateVault()

    @Test
    fun testValidCertificateVerification() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP101",
            hazardDomain = "fire",
            score = 92.5f
        )
        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.VALID, result.status)
        assertTrue(result.isValid)
        assertEquals("EMP101", result.workerId)
        assertEquals("fire", result.hazardDomain)
        assertEquals(92.5f, result.score, 0.01f)
    }

    @Test
    fun testTamperedScore() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP102",
            hazardDomain = "gas_leak",
            score = 85.0f
        )
        // Modify score in payload
        certJson.put("score", 99.9f)

        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.TAMPERED, result.status)
        assertFalse(result.isValid)
        assertTrue(result.statusMessage.contains("TAMPERED"))
    }

    @Test
    fun testTamperedWorkerId() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP103",
            hazardDomain = "fire",
            score = 88.0f
        )
        // Modify workerId in payload
        certJson.put("workerId", "EMP999_ATTACKER")

        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.TAMPERED, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testTamperedExpiryDate() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP104",
            hazardDomain = "gas_leak",
            score = 90.0f
        )
        // Extend expiry date
        val originalExpiry = certJson.getLong("expiresAt")
        certJson.put("expiresAt", originalExpiry + 100000000L)

        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.TAMPERED, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testInvalidSignature() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP105",
            hazardDomain = "fire",
            score = 82.0f
        )
        // Corrupt signature string
        certJson.put("signature", "INVALID_BASE64_SIGNATURE_STRING_AAA=")

        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.TAMPERED, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testExpiredCertificate() {
        // Issue certificate with -1 days expiry (expired yesterday)
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP106",
            hazardDomain = "fire",
            score = 86.0f,
            expiryDays = -1L
        )

        val result = vault.verifyCertificateQr(certJson.toString())
        assertEquals(CertificateStatus.EXPIRED, result.status)
        assertFalse(result.isValid)
        assertTrue(result.statusMessage.contains("EXPIRED"))
    }

    @Test
    fun testMalformedQrPayload() {
        val result = vault.verifyCertificateQr("NOT_A_VALID_JSON_STRING")
        assertEquals(CertificateStatus.UNKNOWN, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testUnknownOrMissingRequiredFields() {
        val incompleteJson = JSONObject().apply {
            put("workerId", "EMP107")
            put("score", 90.0)
            // missing certificateId, issuer, hazardDomain, issuedAt, expiresAt, signature
        }
        val result = vault.verifyCertificateQr(incompleteJson.toString())
        assertEquals(CertificateStatus.UNKNOWN, result.status)
        assertFalse(result.isValid)
    }

    @Test
    fun testRevokedCertificate() {
        val certJson = vault.issueSignedCertificate(
            workerId = "EMP108",
            hazardDomain = "gas_leak",
            score = 89.0f,
            certificateId = "CERT-REVOKED-12345"
        )
        val revokedSet = setOf("CERT-REVOKED-12345")

        val result = vault.verifyCertificateQr(certJson.toString(), revokedCertIds = revokedSet)
        assertEquals(CertificateStatus.REVOKED, result.status)
        assertFalse(result.isValid)
        assertTrue(result.statusMessage.contains("REVOKED"))
    }

    @Test
    fun testCrossDeviceVerification() {
        // Simulate Device A (Signer)
        val keyPairGenA = KeyPairGenerator.getInstance("EC")
        keyPairGenA.initialize(256)
        val deviceAKeyPair = keyPairGenA.generateKeyPair()

        val vaultDeviceA = CertificateVault(signingKeyPair = deviceAKeyPair)

        // Device A signs a certificate
        val certJsonFromDeviceA = vaultDeviceA.issueSignedCertificate(
            workerId = "EMP200_CROSS_DEVICE",
            hazardDomain = "fire",
            score = 95.0f
        )

        // Simulate Device B (Inspector / Admin Verifier)
        // Device B has Device A's Trusted Public Key
        val vaultDeviceB = CertificateVault(trustedVerificationPublicKey = deviceAKeyPair.public)

        // Device B verifies the certificate signed by Device A
        val verificationResultOnDeviceB = vaultDeviceB.verifyCertificateQr(certJsonFromDeviceA.toString())

        assertEquals(CertificateStatus.VALID, verificationResultOnDeviceB.status)
        assertTrue(verificationResultOnDeviceB.isValid)
        assertEquals("EMP200_CROSS_DEVICE", verificationResultOnDeviceB.workerId)
    }

    @Test
    fun testHashChainVerification() {
        val cert1 = vault.issueSignedCertificate(
            workerId = "EMP300",
            hazardDomain = "fire",
            score = 85.0f,
            prevCertificateHash = "GENESIS_BLOCK"
        )
        val hash1 = cert1.getString("certHash")

        val cert2 = vault.issueSignedCertificate(
            workerId = "EMP300",
            hazardDomain = "gas_leak",
            score = 90.0f,
            prevCertificateHash = hash1
        )

        val validChain = listOf(cert1, cert2)
        assertTrue(vault.verifyCertificateChain(validChain))

        // Broken chain test (alter prevHash of cert2)
        val tamperedCert2 = JSONObject(cert2.toString()).apply {
            put("prevHash", "BROKEN_HASH_VALUE")
        }
        val brokenChain = listOf(cert1, tamperedCert2)
        assertFalse(vault.verifyCertificateChain(brokenChain))
    }
}
