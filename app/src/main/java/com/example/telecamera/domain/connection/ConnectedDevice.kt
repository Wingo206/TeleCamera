package com.example.telecamera.domain.connection

data class ConnectedDevice(
    val endpointId: String,
    val name: String,
    val connectedAt: Long = System.currentTimeMillis()
)

data class DiscoveredDevice(
    val endpointId: String,
    val name: String,
    val serviceId: String
)

