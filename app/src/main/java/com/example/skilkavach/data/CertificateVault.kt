package com.example.skilkavach.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.MessageDigest
import kotlin.math.exp

data class CertificateVerificationResult(
    val isValid: Boolean,
    val workerId: String,
    val hazardDomain: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val score: Float,
    val isChainValid: Boolean,
    val statusMessage: String
)

class CertificateVault {
    private val keyAlias = "suraksha.cert.asymmetric.v1"
    private val signatureAlgorithm = "SHA256withECDSA"

    @Synchronized
    private fun getOrCreateKeyPair(): Pair<PublicKey, PrivateKey> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
            val certificate = keyStore.getCertificate(keyAlias)
            return Pair(certificate.publicKey, privateKey)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()
        return Pair(keyPair.public, keyPair.private)
    }

    /**
     * Signs a certificate payload and returns the JSON payload with attached cryptographic signature and hash chain.
     */
    fun issueSignedCertificate(
        workerId: String,
        hazardDomain: String,
        score: Float,
        prevCertificateHash: String = "GENESIS_BLOCK",
        biometricHash: String = "NO_BIOMETRIC"
    ): JSONObject {
        val issuedAt = System.currentTimeMillis()
        val expiryDays = 365L
        val expiresAt = issuedAt + (expiryDays * 24 * 60 * 60 * 1000L)

        val corePayload = JSONObject().apply {
            put("workerId", workerId)
            put("hazardDomain", hazardDomain)
            put("score", score)
            put("issuedAt", issuedAt)
            put("expiresAt", expiresAt)
            put("prevHash", prevCertificateHash)
            put("bioHash", biometricHash)
        }

        val canonicalString = corePayload.toString()
        val (_, privateKey) = getOrCreateKeyPair()

        val signatureBytes = Signature.getInstance(signatureAlgorithm).run {
            initSign(privateKey)
            update(canonicalString.toByteArray(Charsets.UTF_8))
            sign()
        }

        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        val selfHash = computeSha256(canonicalString + signatureBase64)

        return corePayload.apply {
            put("signature", signatureBase64)
            put("certHash", selfHash)
        }
    }

    /**
     * Verifies an offline QR certificate payload using public key signature check and expiration check.
     */
    fun verifyCertificateQr(qrJsonString: String): CertificateVerificationResult {
        return runCatching {
            val json = JSONObject(qrJsonString)
            val workerId = json.getString("workerId")
            val hazardDomain = json.getString("hazardDomain")
            val score = json.getDouble("score").toFloat()
            val issuedAt = json.getLong("issuedAt")
            val expiresAt = json.getLong("expiresAt")
            val prevHash = json.optString("prevHash", "GENESIS_BLOCK")
            val bioHash = json.optString("bioHash", "NO_BIOMETRIC")
            val signatureBase64 = json.getString("signature")

            val recomputedCore = JSONObject().apply {
                put("workerId", workerId)
                put("hazardDomain", hazardDomain)
                put("score", score)
                put("issuedAt", issuedAt)
                put("expiresAt", expiresAt)
                put("prevHash", prevHash)
                put("bioHash", bioHash)
            }.toString()

            val (publicKey, _) = getOrCreateKeyPair()
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val signatureValid = Signature.getInstance(signatureAlgorithm).run {
                initVerify(publicKey)
                update(recomputedCore.toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }

            val isExpired = System.currentTimeMillis() > expiresAt
            val isValid = signatureValid && !isExpired

            val statusMsg = when {
                !signatureValid -> "FAILED: Cryptographic signature mismatch or tampered payload."
                isExpired -> "EXPIRED: Certificate valid period has ended. Recertification required."
                else -> "AUTHENTICATED: Valid tamper-proof certificate."
            }

            CertificateVerificationResult(
                isValid = isValid,
                workerId = workerId,
                hazardDomain = hazardDomain,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                score = score,
                isChainValid = prevHash.isNotEmpty(),
                statusMessage = statusMsg
            )
        }.getOrElse { ex ->
            CertificateVerificationResult(
                isValid = false,
                workerId = "UNKNOWN",
                hazardDomain = "UNKNOWN",
                issuedAt = 0,
                expiresAt = 0,
                score = 0f,
                isChainValid = false,
                statusMessage = "INVALID QR: ${ex.message}"
            )
        }
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
