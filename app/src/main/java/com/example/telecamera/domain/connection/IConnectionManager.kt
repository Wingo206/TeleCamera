package com.example.telecamera.domain.connection

import com.example.telecamera.domain.model.Message
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IConnectionManager {
    val connectedDevices: StateFlow<List<ConnectedDevice>>
    val connectionQuality: StateFlow<ConnectionQuality>
    val incomingMessages: SharedFlow<Pair<String, Message>>
    val connectionState: StateFlow<ConnectionState>

    suspend fun sendToAll(message: Message)
    suspend fun sendTo(deviceId: String, message: Message)
    fun disconnect(deviceId: String)
    fun disconnectAll()
}

enum class ConnectionState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

