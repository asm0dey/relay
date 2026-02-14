package org.relay.shared.protocol
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Payload for WebSocket frame messages.
 * Used for proxying WebSocket messages between external client and local application.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WebSocketFramePayload(
    @ProtoNumber(1)
    val type: String,  // TEXT, BINARY, CLOSE, PING, PONG

    @ProtoNumber(2)
    val data: String? = null,  // Base64 encoded for BINARY, plain text for TEXT

    @ProtoNumber(3)
    val isBinary: Boolean = false,

    @ProtoNumber(4)
    val closeCode: Int? = null,

    @ProtoNumber(5)
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
