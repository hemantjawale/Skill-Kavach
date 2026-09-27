package com.example.skilkavach.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val id: String, val encrypted: String)

@Entity(
    tableName = "outbox",
    indices = [Index("owner"), Index("createdAt")]
)
data class PendingAction(
    @PrimaryKey val id: String,
    val owner: String,
    val encrypted: String,
    val createdAt: Long,
    val error: String = "",
)

@Entity(
    tableName = "workers",
    indices = [Index("siteId"), Index("role")]
)
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val siteId: String,
    val role: String,
    val preferredLanguage: String,
    val biometricEmbeddingHash: String?,
    val createdAt: Long
)

@Entity(
    tableName = "training_sessions",
    indices = [Index("workerId"), Index("moduleId"), Index("startTime")]
)
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

@Entity(
    tableName = "assessment_results",
    indices = [Index("sessionId"), Index("workerId"), Index("evaluatedAt")]
)
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

@Entity(
    tableName = "certificates",
    indices = [
        Index("workerId"),
        Index(value = ["workerId", "hazardDomain"]),
        Index("issuedAt")
    ]
)
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

@Entity(
    tableName = "site_maps",
    indices = [Index("siteId")]
)
data class SiteMapEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val name: String,
    val cloudAnchorRef: String?,
    val scannedAt: Long
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("synced"),
        Index("createdAt"),
        Index("payloadType"),
        Index("status")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val payloadType: String,
    val payloadJson: String,
    val synced: Boolean,
    val createdAt: Long,
    val hopCount: Int = 0,
    val sourceDeviceId: String = "LOCAL",
    val version: Int = 1,
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val status: String = "PENDING", // PENDING, SYNCED, FAILED, DEAD_LETTER
    val errorMessage: String? = null
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

    @Query("SELECT * FROM certificates WHERE id = :id")
    suspend fun getCertificateById(id: String): CertificateEntity?

    @Query("SELECT * FROM certificates WHERE workerId = :workerId AND hazardDomain = :domain ORDER BY issuedAt DESC LIMIT 1")
    suspend fun getLatestCertificate(workerId: String, domain: String): CertificateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificate(cert: CertificateEntity)

    // Sync Queue (Idempotent outbox)
    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getSyncQueueItem(id: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' AND retryCount < 5 ORDER BY createdAt ASC")
    suspend fun getUnsyncedItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET synced = 1, status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE sync_queue SET retryCount = :retryCount, lastAttemptAt = :lastAttemptAt, status = :nextStatus, errorMessage = :errorMsg WHERE id = :id")
    suspend fun recordSyncFailure(id: String, errorMsg: String, retryCount: Int, nextStatus: String, lastAttemptAt: Long)

    // Assessment Results
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessmentResult(result: AssessmentResultEntity)

    @Query("SELECT * FROM assessment_results WHERE id = :id")
    suspend fun getAssessmentById(id: String): AssessmentResultEntity?

    @Query("SELECT * FROM assessment_results WHERE workerId = :workerId ORDER BY evaluatedAt DESC")
    suspend fun getWorkerAssessments(workerId: String): List<AssessmentResultEntity>

    // Worker Pre-Seeded Profiles
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkers(workers: List<WorkerEntity>)

    @Query("SELECT * FROM workers ORDER BY createdAt ASC")
    suspend fun getAllWorkers(): List<WorkerEntity>

    @Transaction
    suspend fun clear() {
        clearCache()
        clearOutbox()
    }
}

val DEFAULT_SEEDED_WORKERS = listOf(
    WorkerEntity(
        id = "worker-sat-01",
        name = "Ramesh Tudu",
        siteId = "Jharia Coalfield, Dhanbad",
        role = "MINER",
        preferredLanguage = "sat",
        biometricEmbeddingHash = "hash-sat-8042",
        createdAt = 1700000000000L
    ),
    WorkerEntity(
        id = "worker-hi-02",
        name = "Sita Murmu",
        siteId = "Tata Steel Works, Jamshedpur",
        role = "PLANT_OPERATOR",
        preferredLanguage = "hi",
        biometricEmbeddingHash = "hash-hi-9120",
        createdAt = 1700000000000L
    ),
    WorkerEntity(
        id = "worker-en-03",
        name = "Anil Kumar",
        siteId = "HEC Industrial Complex, Ranchi",
        role = "SAFETY_INSPECTOR",
        preferredLanguage = "en",
        biometricEmbeddingHash = "hash-en-7011",
        createdAt = 1700000000000L
    )
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `siteId` TEXT NOT NULL, `role` TEXT NOT NULL, `preferredLanguage` TEXT NOT NULL, `biometricEmbeddingHash` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `training_sessions` (`id` TEXT NOT NULL, `workerId` TEXT NOT NULL, `moduleId` TEXT NOT NULL, `siteMapId` TEXT, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `eventLogJson` TEXT NOT NULL, `isStressMode` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `assessment_results` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `workerId` TEXT NOT NULL, `quizScore` REAL NOT NULL, `behavioralScore` REAL NOT NULL, `voiceScore` REAL NOT NULL, `combinedScore` REAL NOT NULL, `passed` INTEGER NOT NULL, `evaluatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `certificates` (`id` TEXT NOT NULL, `workerId` TEXT NOT NULL, `hazardDomain` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `currentConfidenceScore` REAL NOT NULL, `signature` TEXT NOT NULL, `prevCertificateHash` TEXT NOT NULL, `biometricHash` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `site_maps` (`id` TEXT NOT NULL, `siteId` TEXT NOT NULL, `name` TEXT NOT NULL, `cloudAnchorRef` TEXT, `scannedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`id` TEXT NOT NULL, `payloadType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `synced` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `hopCount` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN sourceDeviceId TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastAttemptAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN errorMessage TEXT DEFAULT NULL")

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_owner ON outbox(owner)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_createdAt ON outbox(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workers_siteId ON workers(siteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_training_sessions_workerId ON training_sessions(workerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assessment_results_workerId ON assessment_results(workerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_certificates_workerId ON certificates(workerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue(status)")
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
    version = 3,
    exportSchema = false
)
abstract class SafetyDatabase : RoomDatabase() {
    abstract fun dao(): SafetyDao
}

/** Encrypts both tokens and cached personal data. Keys never leave Android Keystore safely. */
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

    fun decrypt(value: String): String? {
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            if (bytes.size < 28) return null // 12 bytes IV + min 16 bytes auth tag
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
}

