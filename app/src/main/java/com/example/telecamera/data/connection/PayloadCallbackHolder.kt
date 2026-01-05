package com.example.telecamera.data.connection

import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate

/**
 * Singleton holder for PayloadCallback to ensure consistent reference
 * across pairing strategy and connection manager
 */
object PayloadCallbackHolder {
    private const val TAG = "TeleCamera.Payload"
    
    private var payloadListener: ((String, ByteArray) -> Unit)? = null

    fun setPayloadListener(listener: (endpointId: String, bytes: ByteArray) -> Unit) {
        Log.d(TAG, "PayloadListener set")
        payloadListener = listener
    }

    val callback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.v(TAG, "onPayloadReceived from $endpointId, type=${payload.type}, id=${payload.id}")
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    Log.v(TAG, "Received ${bytes.size} bytes from $endpointId")
                    payloadListener?.invoke(endpointId, bytes)
                } ?: Log.w(TAG, "Payload bytes were null from $endpointId")
            } else {
                Log.w(TAG, "Received non-BYTES payload type: ${payload.type} from $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // We only use BYTES payloads, which are transferred in a single chunk
            // Log only for debugging large transfers
            if (update.totalBytes > 50000) {
                Log.v(TAG, "PayloadTransferUpdate from $endpointId: ${update.bytesTransferred}/${update.totalBytes}, status=${update.status}")
            }
        }
    }
}
