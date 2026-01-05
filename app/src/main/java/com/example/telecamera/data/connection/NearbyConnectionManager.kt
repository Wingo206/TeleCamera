package com.example.telecamera.data.connection

import android.content.Context
import android.util.Log
import com.example.telecamera.domain.connection.ConnectedDevice
import com.example.telecamera.domain.connection.ConnectionQuality
import com.example.telecamera.domain.connection.ConnectionState
import com.example.telecamera.domain.connection.DiscoveredDevice
import com.example.telecamera.domain.connection.IConnectionManager
import com.example.telecamera.domain.connection.IPairingStrategy
import com.example.telecamera.domain.connection.PairingEvent
import com.example.telecamera.domain.model.Message
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingStrategy: IPairingStrategy,
    private val messageSerializer: MessageSerializer
) : IConnectionManager {

    companion object {
        private const val TAG = "TeleCamera.Connection"
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    override val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val _connectionQuality = MutableStateFlow(ConnectionQuality())
    override val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, Message>>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<Pair<String, Message>> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val latencyTracking = mutableMapOf<String, Long>()

    init {
        Log.d(TAG, "NearbyConnectionManager initialized")
        setupPayloadCallback()
        observePairingEvents()
    }

    private fun setupPayloadCallback() {
        PayloadCallbackHolder.setPayloadListener { endpointId, bytes ->
            Log.v(TAG, "Payload received from $endpointId, ${bytes.size} bytes")
            val message = messageSerializer.deserialize(bytes)
            if (message != null) {
                handleIncomingMessage(endpointId, message)
            } else {
                Log.w(TAG, "Failed to deserialize message from $endpointId")
            }
        }
    }

    private fun handleIncomingMessage(endpointId: String, message: Message) {
        scope.launch {
            when (message) {
                is Message.Ping -> {
                    Log.v(TAG, "Received Ping from $endpointId")
                    sendTo(endpointId, Message.Pong(originalTimestamp = message.timestamp))
                }
                is Message.Pong -> {
                    val latency = System.currentTimeMillis() - message.originalTimestamp
                    Log.v(TAG, "Received Pong from $endpointId, latency=${latency}ms")
                    _connectionQuality.value = ConnectionQuality(
                        latencyMs = latency,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                is Message.PreviewFrame -> {
                    Log.v(TAG, "Received PreviewFrame from $endpointId")
                    _incomingMessages.emit(endpointId to message)
                }
                else -> {
                    Log.d(TAG, "Received ${message::class.simpleName} from $endpointId")
                    _incomingMessages.emit(endpointId to message)
                }
            }
        }
    }

    private fun observePairingEvents() {
        scope.launch {
            pairingStrategy.pairingEvents.collect { event ->
                Log.d(TAG, "Pairing event received: ${event::class.simpleName}")
                when (event) {
                    is PairingEvent.AdvertisingStarted -> {
                        Log.i(TAG, "State -> ADVERTISING")
                        _connectionState.value = ConnectionState.ADVERTISING
                    }
                    is PairingEvent.DiscoveryStarted -> {
                        Log.i(TAG, "State -> DISCOVERING")
                        _connectionState.value = ConnectionState.DISCOVERING
                    }
                    is PairingEvent.DeviceFound -> {
                        Log.i(TAG, "Device found: ${event.device.endpointId} (${event.device.name}) - Auto-connecting...")
                        // Auto-connect to the first discovered device
                        _connectionState.value = ConnectionState.CONNECTING
                        scope.launch {
                            pairingStrategy.connectTo(event.device)
                        }
                    }
                    is PairingEvent.DeviceLost -> {
                        Log.d(TAG, "Device lost: ${event.endpointId}")
                    }
                    is PairingEvent.ConnectionRequested -> {
                        Log.i(TAG, "Connection requested from ${event.device.endpointId} (${event.device.name}) - Auto-accepting...")
                        _connectionState.value = ConnectionState.CONNECTING
                        pairingStrategy.acceptConnection(event.device.endpointId)
                    }
                    is PairingEvent.Connected -> {
                        Log.i(TAG, "Connected to ${event.device.endpointId} (${event.device.name})")
                        val currentDevices = _connectedDevices.value.toMutableList()
                        if (currentDevices.none { it.endpointId == event.device.endpointId }) {
                            currentDevices.add(event.device)
                            _connectedDevices.value = currentDevices
                        }
                        Log.i(TAG, "State -> CONNECTED (${currentDevices.size} devices)")
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                    is PairingEvent.Disconnected -> {
                        Log.i(TAG, "Disconnected from ${event.endpointId}")
                        val currentDevices = _connectedDevices.value.toMutableList()
                        currentDevices.removeAll { it.endpointId == event.endpointId }
                        _connectedDevices.value = currentDevices
                        if (currentDevices.isEmpty()) {
                            Log.i(TAG, "State -> IDLE (no devices connected)")
                            _connectionState.value = ConnectionState.IDLE
                        }
                    }
                    is PairingEvent.Error -> {
                        Log.e(TAG, "Pairing error: ${event.message}")
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            }
        }
    }

    override suspend fun sendToAll(message: Message) {
        val bytes = messageSerializer.serialize(message)
        val payload = Payload.fromBytes(bytes)
        val devices = _connectedDevices.value
        if (message !is Message.PreviewFrame && message !is Message.Ping && message !is Message.Pong) {
            Log.d(TAG, "Sending ${message::class.simpleName} to ${devices.size} devices")
        }
        devices.forEach { device ->
            try {
                connectionsClient.sendPayload(device.endpointId, payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to ${device.endpointId}", e)
            }
        }
    }

    override suspend fun sendTo(deviceId: String, message: Message) {
        val bytes = messageSerializer.serialize(message)
        val payload = Payload.fromBytes(bytes)
        try {
            connectionsClient.sendPayload(deviceId, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to $deviceId", e)
        }
    }

    override fun disconnect(deviceId: String) {
        Log.d(TAG, "Disconnecting from $deviceId")
        connectionsClient.disconnectFromEndpoint(deviceId)
        val currentDevices = _connectedDevices.value.toMutableList()
        currentDevices.removeAll { it.endpointId == deviceId }
        _connectedDevices.value = currentDevices
        if (currentDevices.isEmpty()) {
            _connectionState.value = ConnectionState.IDLE
        }
    }

    override fun disconnectAll() {
        Log.d(TAG, "Disconnecting all endpoints")
        connectionsClient.stopAllEndpoints()
        _connectedDevices.value = emptyList()
        _connectionState.value = ConnectionState.IDLE
        pairingStrategy.stop()
    }

    fun startAsCamera() {
        Log.i(TAG, "Starting as Camera (host/advertiser)")
        scope.launch {
            pairingStrategy.startAsHost()
        }
    }

    fun startAsRemote() {
        Log.i(TAG, "Starting as Remote (client/discoverer)")
        scope.launch {
            pairingStrategy.startAsClient()
        }
    }

    fun connectToDevice(device: DiscoveredDevice) {
        Log.d(TAG, "Manual connect to device: ${device.endpointId}")
        scope.launch {
            pairingStrategy.connectTo(device)
        }
    }

    fun sendPing() {
        scope.launch {
            sendToAll(Message.Ping())
        }
    }
}
