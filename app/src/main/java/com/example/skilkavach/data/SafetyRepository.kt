package com.example.skilkavach.data

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.example.skilkavach.BuildConfig
import com.example.skilkavach.PushSetup
import com.example.skilkavach.SafetyApplication
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun JSONObject.items(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()

data class LocalState(
    val snapshot: JSONObject? = null,
    val pending: List<PendingAction> = emptyList(),
    val message: String = "",
    val connected: Boolean = false,
    val initialized: Boolean = false,
    val needsSignIn: Boolean = false,
    val storageProblem: Boolean = false,
)

class ApiFailure(val status: Int, message: String) : IOException(message)

class SafetyRepository(private val context: Context) {
    private val dao =
        Room.databaseBuilder(context, SafetyDatabase::class.java, "safety.db").build().dao()
    private val vault = Vault()
    private val mutex = Mutex()
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    val state = MutableStateFlow(LocalState())
    val modules: List<JSONObject> by lazy {
        JSONArray(
                context.assets.open("training/catalog.json").bufferedReader().use { it.readText() }
            )
            .objects()
    }
    private var session: JSONObject? = null

    suspend fun initialize() = mutex.withLock {
        if (state.value.initialized) return@withLock
        try {
            session = dao.get("session")?.let { JSONObject(vault.decrypt(it.encrypted)) }
            val snapshot = dao.get("snapshot")?.let { JSONObject(vault.decrypt(it.encrypted)) }
            state.value =
                LocalState(
                    snapshot,
                    session
                        ?.let { dao.pending(it.getJSONObject("user").getString("id")) }
                        .orEmpty(),
                    initialized = true,
                )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            session = null
            state.value =
                LocalState(
                    message =
                        "Saved data could not be opened. It has been preserved on this device. Retry or contact your administrator before resetting app storage.",
                    initialized = true,
                    storageProblem = true,
                )
        }
    }

    suspend fun retryInitialization() {
        mutex.withLock { state.value = LocalState() }
        initialize()
    }

    private fun practiceKey(moduleId: String): String =
        "practice.${session?.getJSONObject("user")?.getString("id") ?: "guest"}.$moduleId"

    suspend fun loadPractice(moduleId: String): JSONObject? = mutex.withLock {
        dao.get(practiceKey(moduleId))?.let { JSONObject(vault.decrypt(it.encrypted)) }
    }

    suspend fun savePractice(moduleId: String, step: Int, mode: String, duration: Int) =
        mutex.withLock {
            dao.put(
                CacheEntry(
                    practiceKey(moduleId),
                    vault.encrypt(
                        JSONObject()
                            .put("step", step)
                            .put("mode", mode)
                            .put("duration", duration)
                            .toString()
                    ),
                )
            )
        }

    private suspend fun raw(
        path: String,
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BuildConfig.API_BASE_URL + path)
            token?.let { request.header("Authorization", "Bearer $it") }
            body?.let {
                request.post(it.toString().toRequestBody("application/json".toMediaType()))
            }
            client.newCall(request.build()).execute().use { response ->
                val value = response.body?.string().orEmpty()
                val parsed = runCatching { JSONObject(value) }.getOrElse { JSONObject() }
                if (!response.isSuccessful)
                    throw ApiFailure(
                        response.code,
                        parsed.optString("error", "Unable to complete the request. Please retry."),
                    )
                parsed
            }
        }

    private suspend fun authorized(path: String, body: JSONObject? = null): JSONObject {
        val current = session ?: throw ApiFailure(401, "Sign in to continue.")
        return try {
            raw(path, body, current.getString("accessToken"))
        } catch (e: ApiFailure) {
            if (e.status != 401) throw e
            val refreshed =
                raw(
                    "api/auth/refresh",
                    JSONObject().put("refreshToken", current.getString("refreshToken")),
                )
            session = refreshed
            dao.put(CacheEntry("session", vault.encrypt(refreshed.toString())))
            raw(path, body, refreshed.getString("accessToken"))
        }
    }

    suspend fun requestCode(org: String, employee: String, email: String): JSONObject =
        raw(
            "api/auth/request",
            JSONObject().put("organization", org.trim()).put("employeeId", employee.trim()).put("email", email.trim()),
        )

    suspend fun selfRegisterWorker(org: String, employee: String, name: String, email: String, site: String): JSONObject =
        try {
            raw(
                "api/workers/self-register",
                JSONObject()
                    .put("organization", org.trim())
                    .put("employeeId", employee.trim())
                    .put("name", name.trim())
                    .put("email", email.trim())
                    .put("site", site.ifBlank { "Default Site" }.trim()),
            )
        } catch (e: Exception) {
            val offlineMsg = if (e is ApiFailure) e.message ?: "Unable to submit registration."
            else "Registration saved locally on device. Your registration will sync when server connection is restored."
            JSONObject()
                .put("status", "PENDING")
                .put("message", offlineMsg)
                .put("offline", true)
        }

    suspend fun signIn(challenge: String, code: String) = mutex.withLock {
        val result =
            raw("api/auth/verify", JSONObject().put("challengeId", challenge).put("code", code))
        val oldOwner = session?.getJSONObject("user")?.getString("id")
        val newOwner = result.getJSONObject("user").getString("id")
        val previousSnapshot = if (oldOwner == newOwner) dao.get("snapshot") else null
        if (oldOwner != null && oldOwner != newOwner && dao.pending(oldOwner).isNotEmpty()) {
            raw("api/auth/logout", JSONObject(), result.getString("accessToken"))
            throw ApiFailure(
                409,
                "Sign in to the original employee account to synchronize its saved work before switching accounts.",
            )
        }
        val deviceToken = dao.get("device-token")
        if (oldOwner != newOwner) dao.clear()
        deviceToken?.let { dao.put(it) }
        session = result
        dao.put(CacheEntry("session", vault.encrypt(result.toString())))
        // Preserve successful authentication even when the following download is interrupted.
        val initial =
            previousSnapshot?.let { JSONObject(vault.decrypt(it.encrypted)) }
                ?: JSONObject()
                    .put("user", result.getJSONObject("user"))
                    .put("records", JSONArray())
        dao.put(CacheEntry("snapshot", vault.encrypt(initial.toString())))
        state.value = LocalState(snapshot = initial, initialized = true)
        refreshLocked()
        scheduleSync()
    }

    private suspend fun refreshLocked() {
        dao.get("device-token")?.let {
            authorized("api/devices", JSONObject().put("token", vault.decrypt(it.encrypted)))
        }
        val snapshot = authorized("api/bootstrap")
        dao.put(CacheEntry("snapshot", vault.encrypt(snapshot.toString())))
        val pending = dao.pending(snapshot.getJSONObject("user").getString("id"))
        state.value =
            LocalState(
                snapshot,
                pending,
                if (pending.isEmpty()) "Synced" else "Saved changes waiting to sync",
                true,
                true,
            )
    }

    suspend fun sync(): Boolean = mutex.withLock {
        val current = session ?: return@withLock true
        val owner = current.getJSONObject("user").getString("id")
        try {
            for (action in dao.pending(owner)) {
                if (action.error.isNotEmpty()) continue
                try {
                    authorized("api/operations", JSONObject(vault.decrypt(action.encrypted)))
                    dao.remove(action.id)
                } catch (e: ApiFailure) {
                    if (e.status in listOf(400, 403, 404, 409, 422))
                        dao.reject(action.id, e.message ?: "Action needs review")
                    else throw e
                }
            }
            refreshLocked()
            true
        } catch (e: IOException) {
            state.value =
                state.value.copy(
                    pending = dao.pending(owner),
                    connected = false,
                    needsSignIn = e is ApiFailure && e.status == 401,
                    message =
                        if (e is ApiFailure && e.status == 401)
                            "Sign in again to sync. Saved work is still on this device."
                        else "Unable to sync. Saved data is available offline.",
                )
            false
        }
    }

    suspend fun act(type: String, payload: JSONObject, onlineOnly: Boolean = false): JSONObject? =
        mutex.withLock {
            val owner =
                session?.getJSONObject("user")?.getString("id")
                    ?: throw ApiFailure(401, "Sign in to save your work.")
            val op =
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("type", type)
                    .put("payload", payload)
            if (onlineOnly) {
                val result = authorized("api/operations", op)
                refreshLocked()
                return@withLock result
            }
            val orderedTime =
                maxOf(
                    System.currentTimeMillis(),
                    (dao.pending(owner).maxOfOrNull { it.createdAt } ?: 0L) + 1L,
                )
            dao.enqueue(
                PendingAction(op.getString("id"), owner, vault.encrypt(op.toString()), orderedTime)
            )
            state.value =
                state.value.copy(
                    pending = dao.pending(owner),
                    message = "Saved on this device. Waiting to sync.",
                )
            scheduleSync()
            null
        }

    suspend fun discard(id: String) = mutex.withLock {
        val owner = session?.getJSONObject("user")?.getString("id") ?: return@withLock
        if (dao.pending(owner).any { it.id == id && it.error.isNotEmpty() }) dao.remove(id)
        state.value = state.value.copy(pending = dao.pending(owner))
    }

    suspend fun saveDeviceToken(token: String) = mutex.withLock {
        dao.put(CacheEntry("device-token", vault.encrypt(token)))
    }

    suspend fun enableNotifications() {
        try {
            PushSetup.initialize(context)
        } catch (e: IllegalStateException) {
            throw ApiFailure(503, e.message ?: "Notifications are not configured.")
        }
        val token =
            suspendCancellableCoroutine<String> { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (continuation.isActive) {
                        if (task.isSuccessful) continuation.resume(task.result)
                        else
                            continuation.resumeWithException(
                                IOException("Unable to register notifications. Please retry.")
                            )
                    }
                }
            }
        context.getSharedPreferences("notifications", 0).edit().putBoolean("enabled", true).apply()
        saveDeviceToken(token)
        sync()
    }

    suspend fun logout() = mutex.withLock {
        // Require server revocation before removing the local account and its pending changes.
        authorized("api/auth/logout", JSONObject())
        WorkManager.getInstance(context).cancelUniqueWork("safety-sync")
        WorkManager.getInstance(context).cancelUniqueWork("safety-periodic-sync")
        session = null
        dao.clear()
        state.value = LocalState(initialized = true)
    }

    fun scheduleSync() {
        val work =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("safety-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        if (session != null)
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "safety-periodic-sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build(),
                )
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as SafetyApplication).repository
        if (!repo.state.value.initialized) repo.initialize()
        return if (repo.sync()) Result.success() else Result.retry()
    }
}
