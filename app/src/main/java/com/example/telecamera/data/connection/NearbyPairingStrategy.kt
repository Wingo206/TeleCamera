package com.example.telecamera.data.connection

import android.content.Context
import android.util.Log
import com.example.telecamera.domain.connection.ConnectedDevice
import com.example.telecamera.domain.connection.DiscoveredDevice
import com.example.telecamera.domain.connection.IPairingStrategy
import com.example.telecamera.domain.connection.PairingEvent
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyPairingStrategy @Inject constructor(
    @ApplicationContext private val context: Context
) : IPairingStrategy {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val _pairingEvents = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 64)
    override val pairingEvents: SharedFlow<PairingEvent> = _pairingEvents.asSharedFlow()

    override val sessionCode: String? = null // Not used for automatic discovery

    private val pendingConnections = mutableMapOf<String, DiscoveredDevice>()
    private val deviceName: String = android.os.Build.MODEL

    companion object {
        private const val TAG = "TeleCamera.Pairing"
        private const val SERVICE_ID = "com.example.telecamera.nearby"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated: endpointId=$endpointId, name=${info.endpointName}, isIncomingConnection=${info.isIncomingConnection}")
            val device = DiscoveredDevice(
                endpointId = endpointId,
                name = info.endpointName,
                serviceId = SERVICE_ID
            )
            pendingConnections[endpointId] = device
            _pairingEvents.tryEmit(PairingEvent.ConnectionRequested(device))
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "onConnectionResult: endpointId=$endpointId, statusCode=${result.status.statusCode}, statusMessage=${result.status.statusMessage}")
            val device = pendingConnections.remove(endpointId)
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i(TAG, "Connection successful to $endpointId (${device?.name})")
                    val connectedDevice = ConnectedDevice(
                        endpointId = endpointId,
                        name = device?.name ?: "Unknown"
                    )
                    _pairingEvents.tryEmit(PairingEvent.Connected(connectedDevice))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                    _pairingEvents.tryEmit(PairingEvent.Error("Connection rejected"))
                }
                else -> {
                    Log.e(TAG, "Connection failed to $endpointId: ${result.status.statusCode} - ${result.status.statusMessage}")
                    _pairingEvents.tryEmit(PairingEvent.Error("Connection failed: ${result.status.statusMessage}"))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "onDisconnected: endpointId=$endpointId")
            pendingConnections.remove(endpointId)
            _pairingEvents.tryEmit(PairingEvent.Disconnected(endpointId))
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "onEndpointFound: endpointId=$endpointId, name=${info.endpointName}, serviceId=${info.serviceId}")
            val device = DiscoveredDevice(
                endpointId = endpointId,
                name = info.endpointName,
                serviceId = info.serviceId
            )
            _pairingEvents.tryEmit(PairingEvent.DeviceFound(device))
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "onEndpointLost: endpointId=$endpointId")
            _pairingEvents.tryEmit(PairingEvent.DeviceLost(endpointId))
        }
    }

    override suspend fun startAsHost() {
        Log.d(TAG, "startAsHost: Starting advertising as '$deviceName' with serviceId=$SERVICE_ID")
        try {
            val advertisingOptions = AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

            connectionsClient.startAdvertising(
                deviceName,
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions
            ).await()

            Log.i(TAG, "startAsHost: Advertising started successfully")
            _pairingEvents.tryEmit(PairingEvent.AdvertisingStarted)
        } catch (e: Exception) {
            Log.e(TAG, "startAsHost: Failed to start advertising", e)
            _pairingEvents.tryEmit(PairingEvent.Error("Failed to start advertising: ${e.message}"))
        }
    }

    override suspend fun startAsClient() {
        Log.d(TAG, "startAsClient: Starting discovery for serviceId=$SERVICE_ID")
        try {
            val discoveryOptions = DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions
            ).await()

            Log.i(TAG, "startAsClient: Discovery started successfully")
            _pairingEvents.tryEmit(PairingEvent.DiscoveryStarted)
        } catch (e: Exception) {
            Log.e(TAG, "startAsClient: Failed to start discovery", e)
            _pairingEvents.tryEmit(PairingEvent.Error("Failed to start discovery: ${e.message}"))
        }
    }

    override suspend fun connectTo(device: DiscoveredDevice) {
        Log.d(TAG, "connectTo: Requesting connection to ${device.endpointId} (${device.name})")
        try {
            connectionsClient.requestConnection(
                deviceName,
                device.endpointId,
                connectionLifecycleCallback
            ).await()
            Log.i(TAG, "connectTo: Connection request sent to ${device.endpointId}")
        } catch (e: Exception) {
            Log.e(TAG, "connectTo: Failed to request connection to ${device.endpointId}", e)
            _pairingEvents.tryEmit(PairingEvent.Error("Failed to connect: ${e.message}"))
        }
    }

    override fun acceptConnection(endpointId: String) {
        Log.d(TAG, "acceptConnection: Accepting connection from $endpointId")
        connectionsClient.acceptConnection(endpointId, PayloadCallbackHolder.callback)
            .addOnSuccessListener { Log.d(TAG, "acceptConnection: Success for $endpointId") }
            .addOnFailureListener { e -> Log.e(TAG, "acceptConnection: Failed for $endpointId", e) }
    }

    override fun rejectConnection(endpointId: String) {
        Log.d(TAG, "rejectConnection: Rejecting connection from $endpointId")
        connectionsClient.rejectConnection(endpointId)
    }

    override fun stop() {
        Log.d(TAG, "stop: Stopping advertising and discovery")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        pendingConnections.clear()
    }
}
