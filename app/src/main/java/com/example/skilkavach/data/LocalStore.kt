package com.example.skilkavach.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val id: String, val encrypted: String)

@Entity(tableName = "outbox")
data class PendingAction(
    @PrimaryKey val id: String,
    val owner: String,
    val encrypted: String,
    val createdAt: Long,
    val error: String = "",
)

@Dao
interface SafetyDao {
    @Query("SELECT * FROM cache WHERE id = :id") suspend fun get(id: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CacheEntry)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun enqueue(action: PendingAction)

    @Query("SELECT * FROM outbox WHERE owner = :owner ORDER BY createdAt, id")
    suspend fun pending(owner: String): List<PendingAction>

    @Query("DELETE FROM outbox WHERE id = :id") suspend fun remove(id: String)

    @Query("UPDATE outbox SET error = :error WHERE id = :id")
    suspend fun reject(id: String, error: String)

    @Query("DELETE FROM cache") suspend fun clearCache()

    @Query("DELETE FROM outbox") suspend fun clearOutbox()

    @Transaction
    suspend fun clear() {
        clearCache()
        clearOutbox()
    }
}

@Database(entities = [CacheEntry::class, PendingAction::class], version = 1, exportSchema = true)
abstract class SafetyDatabase : RoomDatabase() {
    abstract fun dao(): SafetyDao
}

/** Encrypts both tokens and cached personal data. Keys never leave Android Keystore. */
class Vault {
    private val alias = "suraksha.local.v1"

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun encrypt(value: String): String {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
    }

    fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
