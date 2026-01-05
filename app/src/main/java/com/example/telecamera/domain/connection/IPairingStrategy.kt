package com.example.telecamera.domain.connection

import kotlinx.coroutines.flow.SharedFlow

interface IPairingStrategy {
    val pairingEvents: SharedFlow<PairingEvent>
    val sessionCode: String?

    suspend fun startAsHost()
    suspend fun startAsClient()
    suspend fun connectTo(device: DiscoveredDevice)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    fun stop()
}

sealed class PairingEvent {
    data class DeviceFound(val device: DiscoveredDevice) : PairingEvent()
    data class DeviceLost(val endpointId: String) : PairingEvent()
    data class ConnectionRequested(val device: DiscoveredDevice) : PairingEvent()
    data class Connected(val device: ConnectedDevice) : PairingEvent()
    data class Disconnected(val endpointId: String) : PairingEvent()
    data class Error(val message: String) : PairingEvent()
    object AdvertisingStarted : PairingEvent()
    object DiscoveryStarted : PairingEvent()
}

