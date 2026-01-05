package com.example.telecamera.data.connection

import android.util.Base64
import com.example.telecamera.domain.model.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSerializer @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(message: Message): ByteArray {
        val jsonString = json.encodeToString(message)
        return jsonString.toByteArray(Charsets.UTF_8)
    }

    fun deserialize(bytes: ByteArray): Message? {
        return try {
            val jsonString = String(bytes, Charsets.UTF_8)
            json.decodeFromString<Message>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun encodeImageToBase64(jpeg: ByteArray): String {
            return Base64.encodeToString(jpeg, Base64.NO_WRAP)
        }

        fun decodeBase64ToImage(base64: String): ByteArray {
            return Base64.decode(base64, Base64.NO_WRAP)
        }
    }
}

