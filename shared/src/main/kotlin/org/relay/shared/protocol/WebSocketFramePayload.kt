package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Payload for WebSocket frame messages.
 * Used for proxying WebSocket messages between external client and local application.
 */
@Serializable
data class WebSocketFramePayload(
    val type: String,  // TEXT, BINARY, CLOSE, PING, PONG
    val data: String? = null,  // Base64 encoded for BINARY, plain text for TEXT
    val isBinary: Boolean = false,
    val closeCode: Int? = null,
    val closeReason: String? = null
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_BINARY = "BINARY"
        const val TYPE_CLOSE = "CLOSE"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"

        // Standard WebSocket close codes
        const val CLOSE_NORMAL = 1000
        const val CLOSE_GOING_AWAY = 1001
        const val CLOSE_PROTOCOL_ERROR = 1002
        const val CLOSE_INTERNAL_ERROR = 1011
    }
}
