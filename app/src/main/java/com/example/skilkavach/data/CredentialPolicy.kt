package com.example.skilkavach.data

import java.time.Instant

/** Cached status is informational. The API independently authorizes every shift. */
object CredentialPolicy {
    fun status(serverStatus: String, expiresAt: String, now: Instant = Instant.now()): String {
        if (serverStatus == "REVOKED") return "REVOKED"
        if (serverStatus != "VALID") return "INVALID"
        val expiry = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return "INVALID"
        return if (expiry <= now) "EXPIRED" else "VALID"
    }

    fun coversShift(
        serverStatus: String,
        expiresAt: String,
        shiftEnd: String,
        now: Instant = Instant.now(),
    ): Boolean {
        if (status(serverStatus, expiresAt, now) != "VALID") return false
        val end = runCatching { Instant.parse(shiftEnd) }.getOrNull() ?: return false
        return Instant.parse(expiresAt) > end && end > now
    }
}
