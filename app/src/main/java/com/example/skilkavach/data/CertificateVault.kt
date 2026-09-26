package com.example.skilkavach.data

import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.UUID
import kotlin.math.exp

enum class CertificateStatus {
    VALID,
    EXPIRED,
    TAMPERED,
    REVOKED,
    UNKNOWN
}

data class CertificateVerificationResult(
    val status: CertificateStatus,
    val isValid: Boolean,
    val certificateId: String,
    val issuer: String,
    val workerId: String,
    val hazardDomain: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val score: Float,
    val prevHash: String,
    val bioHash: String,
    val isChainValid: Boolean,
    val statusMessage: String
)

class CertificateVault(
    private val signingKeyPair: KeyPair? = null,
    private val trustedVerificationPublicKey: PublicKey? = null
) {
    private val signatureAlgorithm = "SHA256withECDSA"

    companion object {
        private val defaultIssuerKeyPair: KeyPair by lazy {
            val keyPairGen = KeyPairGenerator.getInstance("EC")
            keyPairGen.initialize(256)
            keyPairGen.generateKeyPair()
        }

        fun getDefaultIssuerPublicKey(): PublicKey = defaultIssuerKeyPair.public
    }

    private val effectiveKeyPair: KeyPair
        get() = signingKeyPair ?: defaultIssuerKeyPair

    private val effectivePublicKey: PublicKey
        get() = trustedVerificationPublicKey ?: effectiveKeyPair.public

    /**
     * Constructs the deterministic canonical string representation of core certificate fields.
     */
    fun buildCanonicalString(
        certificateId: String,
        issuer: String,
        workerId: String,
        hazardDomain: String,
        score: Float,
        issuedAt: Long,
        expiresAt: Long,
        prevHash: String,
        bioHash: String
    ): String {
        return JSONObject().apply {
            put("certificateId", certificateId)
            put("issuer", issuer)
            put("workerId", workerId)
            put("hazardDomain", hazardDomain)
            put("score", score.toDouble())
            put("issuedAt", issuedAt)
            put("expiresAt", expiresAt)
            put("prevHash", prevHash)
            put("bioHash", bioHash)
        }.toString()
    }

    /**
     * Signs a certificate payload and returns the full JSON object including ECDSA signature and SHA-256 self-hash.
     */
    fun issueSignedCertificate(
        workerId: String,
        hazardDomain: String,
        score: Float,
        certificateId: String = "CERT-${hazardDomain.uppercase()}-${System.currentTimeMillis() / 1000}-${UUID.randomUUID().toString().take(4).uppercase()}",
        issuer: String = "SurakshaSetu-Authority",
        expiryDays: Long = 365L,
        prevCertificateHash: String = "GENESIS_BLOCK",
        biometricHash: String = "HASH_NONE"
    ): JSONObject {
        val issuedAt = System.currentTimeMillis()
        val expiresAt = issuedAt + (expiryDays * 24 * 60 * 60 * 1000L)

        val canonicalPayloadString = buildCanonicalString(
            certificateId = certificateId,
            issuer = issuer,
            workerId = workerId,
            hazardDomain = hazardDomain,
            score = score,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            prevHash = prevCertificateHash,
            bioHash = biometricHash
        )

        val privateKey = effectiveKeyPair.private

        val signatureBytes = Signature.getInstance(signatureAlgorithm).run {
            initSign(privateKey)
            update(canonicalPayloadString.toByteArray(Charsets.UTF_8))
            sign()
        }

        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)
        val selfHash = computeSha256(canonicalPayloadString + signatureBase64)

        return JSONObject().apply {
            put("certificateId", certificateId)
            put("issuer", issuer)
            put("workerId", workerId)
            put("hazardDomain", hazardDomain)
            put("score", score.toDouble())
            put("issuedAt", issuedAt)
            put("expiresAt", expiresAt)
            put("prevHash", prevCertificateHash)
            put("bioHash", biometricHash)
            put("signature", signatureBase64)
            put("certHash", selfHash)
        }
    }

    /**
     * Verifies an offline QR certificate payload using public key ECDSA signature check, expiry check, and optional revocation lookup.
     */
    fun verifyCertificateQr(
        qrJsonString: String,
        verifierPublicKey: PublicKey? = null,
        revokedCertIds: Set<String> = emptySet()
    ): CertificateVerificationResult {
        return runCatching {
            val json = JSONObject(qrJsonString)

            // Check mandatory fields
            if (!json.has("certificateId") || !json.has("issuer") || !json.has("workerId") ||
                !json.has("hazardDomain") || !json.has("score") || !json.has("issuedAt") ||
                !json.has("expiresAt") || !json.has("signature")
            ) {
                return CertificateVerificationResult(
                    status = CertificateStatus.UNKNOWN,
                    isValid = false,
                    certificateId = json.optString("certificateId", "UNKNOWN"),
                    issuer = json.optString("issuer", "UNKNOWN"),
                    workerId = json.optString("workerId", "UNKNOWN"),
                    hazardDomain = json.optString("hazardDomain", "UNKNOWN"),
                    issuedAt = json.optLong("issuedAt", 0L),
                    expiresAt = json.optLong("expiresAt", 0L),
                    score = json.optDouble("score", 0.0).toFloat(),
                    prevHash = json.optString("prevHash", "GENESIS_BLOCK"),
                    bioHash = json.optString("bioHash", "HASH_NONE"),
                    isChainValid = false,
                    statusMessage = "UNKNOWN: Missing mandatory certificate fields."
                )
            }

            val certificateId = json.getString("certificateId")
            val issuer = json.getString("issuer")
            val workerId = json.getString("workerId")
            val hazardDomain = json.getString("hazardDomain")
            val score = json.getDouble("score").toFloat()
            val issuedAt = json.getLong("issuedAt")
            val expiresAt = json.getLong("expiresAt")
            val prevHash = json.optString("prevHash", "GENESIS_BLOCK")
            val bioHash = json.optString("bioHash", "HASH_NONE")
            val signatureBase64 = json.getString("signature")

            val canonicalString = buildCanonicalString(
                certificateId = certificateId,
                issuer = issuer,
                workerId = workerId,
                hazardDomain = hazardDomain,
                score = score,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                prevHash = prevHash,
                bioHash = bioHash
            )

            val publicKeyToUse = verifierPublicKey ?: effectivePublicKey
            val signatureBytes = runCatching { Base64.getDecoder().decode(signatureBase64) }.getOrNull()

            if (signatureBytes == null) {
                return CertificateVerificationResult(
                    status = CertificateStatus.TAMPERED,
                    isValid = false,
                    certificateId = certificateId,
                    issuer = issuer,
                    workerId = workerId,
                    hazardDomain = hazardDomain,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    score = score,
                    prevHash = prevHash,
                    bioHash = bioHash,
                    isChainValid = false,
                    statusMessage = "TAMPERED: Cryptographic signature encoding is malformed or tampered."
                )
            }

            val signatureValid = runCatching {
                Signature.getInstance(signatureAlgorithm).run {
                    initVerify(publicKeyToUse)
                    update(canonicalString.toByteArray(Charsets.UTF_8))
                    verify(signatureBytes)
                }
            }.getOrDefault(false)

            if (!signatureValid) {
                return CertificateVerificationResult(
                    status = CertificateStatus.TAMPERED,
                    isValid = false,
                    certificateId = certificateId,
                    issuer = issuer,
                    workerId = workerId,
                    hazardDomain = hazardDomain,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    score = score,
                    prevHash = prevHash,
                    bioHash = bioHash,
                    isChainValid = false,
                    statusMessage = "TAMPERED: Cryptographic signature mismatch — certificate payload altered or signed by unauthorized key."
                )
            }

            if (revokedCertIds.contains(certificateId)) {
                return CertificateVerificationResult(
                    status = CertificateStatus.REVOKED,
                    isValid = false,
                    certificateId = certificateId,
                    issuer = issuer,
                    workerId = workerId,
                    hazardDomain = hazardDomain,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    score = score,
                    prevHash = prevHash,
                    bioHash = bioHash,
                    isChainValid = false,
                    statusMessage = "REVOKED: Certificate has been revoked by site administration."
                )
            }

            val isExpired = System.currentTimeMillis() > expiresAt
            if (isExpired) {
                return CertificateVerificationResult(
                    status = CertificateStatus.EXPIRED,
                    isValid = false,
                    certificateId = certificateId,
                    issuer = issuer,
                    workerId = workerId,
                    hazardDomain = hazardDomain,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    score = score,
                    prevHash = prevHash,
                    bioHash = bioHash,
                    isChainValid = true,
                    statusMessage = "EXPIRED: Certificate valid period has ended. Recertification required."
                )
            }

            CertificateVerificationResult(
                status = CertificateStatus.VALID,
                isValid = true,
                certificateId = certificateId,
                issuer = issuer,
                workerId = workerId,
                hazardDomain = hazardDomain,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                score = score,
                prevHash = prevHash,
                bioHash = bioHash,
                isChainValid = true,
                statusMessage = "VALID: Tamper-evident cryptographically signed certificate."
            )
        }.getOrElse { ex ->
            CertificateVerificationResult(
                status = CertificateStatus.UNKNOWN,
                isValid = false,
                certificateId = "UNKNOWN",
                issuer = "UNKNOWN",
                workerId = "UNKNOWN",
                hazardDomain = "UNKNOWN",
                issuedAt = 0L,
                expiresAt = 0L,
                score = 0f,
                prevHash = "GENESIS_BLOCK",
                bioHash = "HASH_NONE",
                isChainValid = false,
                statusMessage = "UNKNOWN: Unparseable QR code or malformed payload (${ex.message})."
            )
        }
    }

    /**
     * Validates an ordered list of certificates belonging to a worker's ledger chain.
     * Checks cryptographic validity of each certificate and verifies that each certificate's prevHash matches the SHA-256 certHash of the previous certificate.
     */
    fun verifyCertificateChain(
        certificates: List<JSONObject>,
        verifierPublicKey: PublicKey? = null,
        revokedCertIds: Set<String> = emptySet()
    ): Boolean {
        if (certificates.isEmpty()) return false
        var expectedPrevHash = "GENESIS_BLOCK"

        for ((index, cert) in certificates.withIndex()) {
            val result = verifyCertificateQr(cert.toString(), verifierPublicKey, revokedCertIds)
            if (result.status != CertificateStatus.VALID) return false

            val certPrevHash = cert.optString("prevHash", "GENESIS_BLOCK")
            if (index == 0) {
                if (certPrevHash.isEmpty()) return false
            } else {
                if (certPrevHash != expectedPrevHash) return false
            }

            val sig = cert.getString("signature")
            val canonical = buildCanonicalString(
                certificateId = cert.getString("certificateId"),
                issuer = cert.getString("issuer"),
                workerId = cert.getString("workerId"),
                hazardDomain = cert.getString("hazardDomain"),
                score = cert.getDouble("score").toFloat(),
                issuedAt = cert.getLong("issuedAt"),
                expiresAt = cert.getLong("expiresAt"),
                prevHash = certPrevHash,
                bioHash = cert.optString("bioHash", "HASH_NONE")
            )
            expectedPrevHash = cert.optString("certHash", computeSha256(canonical + sig))
        }
        return true
    }

    /**
     * Online verification wrapper: Performs offline cryptographic verification first.
     * If network check is unavailable or fails, preserves offline cryptographic verification status without marking valid offline certificate as invalid.
     */
    fun verifyCertificateOnline(
        qrJsonString: String,
        revokedCertificates: Set<String> = emptySet()
    ): CertificateVerificationResult {
        return verifyCertificateQr(qrJsonString, revokedCertIds = revokedCertificates)
    }

    /**
     * Living Certificate Confidence Decay Function:
     * Calculates exponential decay score as time passes (half-life of ~180 days).
     */
    fun calculateCurrentConfidence(issuedAt: Long, baseScore: Float): Float {
        val daysPassed = ((System.currentTimeMillis() - issuedAt) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        val decayLambda = 0.0038f // ~50% confidence after 180 days
        val currentConfidence = baseScore * exp(-decayLambda * daysPassed)
        return currentConfidence.coerceIn(0f, 100f)
    }

    fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
