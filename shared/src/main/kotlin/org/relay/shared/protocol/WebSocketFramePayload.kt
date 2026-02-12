package org.relay.shared.protocol

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payload for WebSocket frame messages.
 * Used for proxying WebSocket messages between external client and local application.
 */
data class WebSocketFramePayload(
    @JsonProperty("type")
    val type: String,  // TEXT, BINARY, CLOSE, PING, PONG

    @JsonProperty("data")
    val data: String? = null,  // Base64 encoded for BINARY, plain text for TEXT

    @JsonProperty("isBinary")
    val isBinary: Boolean = false,

    @JsonProperty("closeCode")
    val closeCode: Int? = null,

    @JsonProperty("closeReason")
    val closeReason: String? = null
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_BINARY = "BINARY"
        const val TYPE_CLOSE = "CLOSE"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"

        const val TYPE_FIELD = "type"
        const val DATA_FIELD = "data"
        const val IS_BINARY_FIELD = "isBinary"
        const val CLOSE_CODE_FIELD = "closeCode"
        const val CLOSE_REASON_FIELD = "closeReason"

        // Standard WebSocket close codes
        const val CLOSE_NORMAL = 1000
        const val CLOSE_GOING_AWAY = 1001
        const val CLOSE_PROTOCOL_ERROR = 1002
        const val CLOSE_UNSUPPORTED_DATA = 1003
        const val CLOSE_INVALID_MESSAGE = 1008
        const val CLOSE_POLICY_VIOLATION = 1009
        const val CLOSE_TOO_BIG = 1009
        const val CLOSE_INTERNAL_ERROR = 1011
    }

    override fun toString(): String {
        return "WebSocketFramePayload(type='$type', isBinary=$isBinary, dataLength=${data?.length ?: 0})"
    }
}
