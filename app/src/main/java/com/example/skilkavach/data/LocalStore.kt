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

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val siteId: String,
    val role: String,
    val preferredLanguage: String,
    val biometricEmbeddingHash: String?,
    val createdAt: Long
)

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey val id: String,
    val workerId: String,
    val moduleId: String,
    val siteMapId: String?,
    val startTime: Long,
    val endTime: Long,
    val eventLogJson: String,
    val isStressMode: Boolean
)

@Entity(tableName = "assessment_results")
data class AssessmentResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val workerId: String,
    val quizScore: Float,
    val behavioralScore: Float,
    val voiceScore: Float,
    val combinedScore: Float,
    val passed: Boolean,
    val evaluatedAt: Long
)

@Entity(tableName = "certificates")
data class CertificateEntity(
    @PrimaryKey val id: String,
    val workerId: String,
    val hazardDomain: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val currentConfidenceScore: Float,
    val signature: String,
    val prevCertificateHash: String,
    val biometricHash: String
)

@Entity(tableName = "site_maps")
data class SiteMapEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val name: String,
    val cloudAnchorRef: String?,
    val scannedAt: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val payloadType: String,
    val payloadJson: String,
    val synced: Boolean,
    val createdAt: Long,
    val hopCount: Int = 0
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

    // Certificates Ledger
    @Query("SELECT * FROM certificates WHERE workerId = :workerId ORDER BY issuedAt DESC")
    suspend fun getWorkerCertificates(workerId: String): List<CertificateEntity>

    @Query("SELECT * FROM certificates WHERE workerId = :workerId AND hazardDomain = :domain ORDER BY issuedAt DESC LIMIT 1")
    suspend fun getLatestCertificate(workerId: String, domain: String): CertificateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificate(cert: CertificateEntity)

    // Sync Queue
    @Query("SELECT * FROM sync_queue WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    // Assessment Results
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessmentResult(result: AssessmentResultEntity)

    @Query("SELECT * FROM assessment_results WHERE workerId = :workerId ORDER BY evaluatedAt DESC")
    suspend fun getWorkerAssessments(workerId: String): List<AssessmentResultEntity>

    @Transaction
    suspend fun clear() {
        clearCache()
        clearOutbox()
    }
}

@Database(
    entities = [
        CacheEntry::class,
        PendingAction::class,
        WorkerEntity::class,
        TrainingSessionEntity::class,
        AssessmentResultEntity::class,
        CertificateEntity::class,
        SiteMapEntity::class,
        SyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
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
