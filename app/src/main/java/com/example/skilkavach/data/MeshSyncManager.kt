package com.example.skilkavach.data

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Peer-to-Peer Mesh Relay Sync Manager using Google Nearby Connections API.
 * Enables off-grid workers to exchange signed certificate records and assessment logs via Bluetooth / Wi-Fi Direct.
 */
class MeshSyncManager(
    private val context: Context,
    private val dao: SafetyDao,
    private val workerId: String
) {
    private val serviceId = "com.example.skilkavach.mesh.v1"
    private val strategy = Strategy.P2P_CLUSTER
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _meshStatus = MutableStateFlow("Mesh Offline")
    val meshStatus = _meshStatus.asStateFlow()

    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers = _connectedPeers.asStateFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val jsonString = String(bytes, Charsets.UTF_8)
            Log.d("MeshSyncManager", "Received mesh payload from $endpointId: $jsonString")
            handleIncomingMeshPayload(jsonString)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track transfer progress
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("MeshSyncManager", "Mesh connection initiated with ${info.endpointName}")
            // Automatically accept mesh connections for trusted workers
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("MeshSyncManager", "Connected to peer $endpointId")
                _connectedPeers.value = _connectedPeers.value + endpointId
                _meshStatus.value = "Connected (${_connectedPeers.value.size} peer/s)"
                broadcastUnsyncedMeshItems(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("MeshSyncManager", "Disconnected from peer $endpointId")
            _connectedPeers.value = _connectedPeers.value - endpointId
            _meshStatus.value = if (_connectedPeers.value.isEmpty()) "Mesh Listening" else "Connected (${_connectedPeers.value.size})"
        }
    }

    fun startMeshService() {
        _meshStatus.value = "Starting Mesh..."
        startAdvertising()
        startDiscovery()
    }

    fun stopMeshService() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        Nearby.getConnectionsClient(context).stopDiscovery()
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        _meshStatus.value = "Mesh Offline"
        _connectedPeers.value = emptySet()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        Nearby.getConnectionsClient(context)
            .startAdvertising(workerId, serviceId, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener { _meshStatus.value = "Mesh Advertising" }
            .addOnFailureListener { err -> Log.e("MeshSyncManager", "Advertising failed", err) }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        Nearby.getConnectionsClient(context)
            .startDiscovery(
                serviceId,
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                        Log.d("MeshSyncManager", "Discovered peer: ${info.endpointName}")
                        Nearby.getConnectionsClient(context)
                            .requestConnection(workerId, endpointId, connectionLifecycleCallback)
                    }

                    override fun onEndpointLost(endpointId: String) {
                        Log.d("MeshSyncManager", "Lost peer: $endpointId")
                    }
                },
                discoveryOptions
            )
            .addOnSuccessListener { Log.d("MeshSyncManager", "Discovery active") }
            .addOnFailureListener { err -> Log.e("MeshSyncManager", "Discovery failed", err) }
    }

    private fun broadcastUnsyncedMeshItems(endpointId: String) {
        scope.launch {
            val pendingSyncItems = dao.getUnsyncedItems()
            for (item in pendingSyncItems) {
                if (item.hopCount < 5) { // Prevent infinite mesh looping (TTL max 5 hops)
                    val meshEnvelope = JSONObject().apply {
                        put("type", item.payloadType)
                        put("json", item.payloadJson)
                        put("id", item.id)
                        put("hop", item.hopCount + 1)
                    }.toString()

                    val payload = Payload.fromBytes(meshEnvelope.toByteArray(Charsets.UTF_8))
                    Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
                }
            }
        }
    }

    private fun handleIncomingMeshPayload(jsonString: String) {
        scope.launch {
            runCatching {
                val envelope = JSONObject(jsonString)
                val id = envelope.getString("id")
                val type = envelope.getString("type")
                val payloadJson = envelope.getString("json")
                val hopCount = envelope.getInt("hop")

                val entity = SyncQueueEntity(
                    id = id,
                    payloadType = type,
                    payloadJson = payloadJson,
                    synced = false,
                    createdAt = System.currentTimeMillis(),
                    hopCount = hopCount
                )

                dao.enqueueSyncItem(entity)
                Log.d("MeshSyncManager", "Successfully enqueued mesh item: $id ($type)")
            }
        }
    }
}
