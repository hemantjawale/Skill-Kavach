package com.example.skilkavach

import com.example.skilkavach.data.CredentialPolicy
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CredentialPolicyTest {
    private val now = Instant.parse("2026-09-12T08:00:00Z")

    @Test
    fun expiryAtCurrentInstantIsExpired() {
        assertEquals("EXPIRED", CredentialPolicy.status("VALID", now.toString(), now))
    }

    @Test
    fun revokedAndUnknownStatusesNeverDisplayValid() {
        assertEquals("REVOKED", CredentialPolicy.status("REVOKED", "2030-01-01T00:00:00Z", now))
        assertEquals("INVALID", CredentialPolicy.status("PENDING", "2030-01-01T00:00:00Z", now))
        assertEquals("INVALID", CredentialPolicy.status("VALID", "corrupted-cache", now))
    }

    @Test
    fun certificateMustCoverWholeShiftAndNotOnlyCheckIn() {
        assertFalse(
            CredentialPolicy.coversShift(
                "VALID",
                "2026-09-12T10:00:00Z",
                "2026-09-12T16:00:00Z",
                now,
            )
        )
        assertFalse(
            CredentialPolicy.coversShift(
                "VALID",
                "2026-09-12T16:00:00Z",
                "2026-09-12T16:00:00Z",
                now,
            )
        )
        assertTrue(
            CredentialPolicy.coversShift(
                "VALID",
                "2026-09-13T00:00:00Z",
                "2026-09-12T16:00:00Z",
                now,
            )
        )
    }

    @Test
    fun malformedShiftOrEndedShiftFailsClosed() {
        assertFalse(CredentialPolicy.coversShift("VALID", "2030-01-01T00:00:00Z", "invalid", now))
        assertFalse(
            CredentialPolicy.coversShift(
                "VALID",
                "2030-01-01T00:00:00Z",
                "2026-09-11T00:00:00Z",
                now,
            )
        )
    }
}
