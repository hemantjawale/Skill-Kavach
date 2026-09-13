package com.example.skilkavach

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.skilkavach.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSecurityTest {
    @Test
    fun encryptedValuesRoundTripAndTamperingFails() {
        val vault = Vault()
        val encrypted = vault.encrypt("private salary and session")
        assertFalse(encrypted.contains("private"))
        assertEquals("private salary and session", vault.decrypt(encrypted))
        val bytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(
            runCatching {
                vault.decrypt(
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                )
            }
                .isFailure
        )
    }

    @Test
    fun outboxIsIsolatedPerWorkerAndClearIsComplete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SafetyDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.enqueue(PendingAction("a", "worker-a", "encrypted-a", 1))
            dao.enqueue(PendingAction("b", "worker-b", "encrypted-b", 2))
            assertEquals(listOf("a"), dao.pending("worker-a").map { it.id })
            dao.put(CacheEntry("session", "ciphertext"))
            dao.clear()
            assertTrue(dao.pending("worker-a").isEmpty())
            assertTrue(dao.pending("worker-b").isEmpty())
            assertNull(dao.get("session"))
        } finally {
            db.close()
        }
    }
}
