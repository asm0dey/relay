package org.relay.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Sealed class for protocol payload union type (Protobuf oneof equivalent).
 *
 * Each message envelope contains exactly one payload variant corresponding to the message type.
 * This provides type-safe payload handling and efficient Protobuf encoding.
 */
@Serializable
sealed class Payload {

    /**
     * REQUEST payload wrapper
     */
    @Serializable
    @SerialName("request")
    data class Request(
        @ProtoNumber(1)
        val data: RequestPayload
    ) : Payload()

    /**
     * RESPONSE payload wrapper
     */
    @Serializable
    @SerialName("response")
    data class Response(
        @ProtoNumber(1)
        val data: ResponsePayload
    ) : Payload()

    /**
     * ERROR payload wrapper
     */
    @Serializable
    @SerialName("error")
    data class Error(
        @ProtoNumber(1)
        val data: ErrorPayload
    ) : Payload()

    /**
     * CONTROL payload wrapper
     */
    @Serializable
    @SerialName("control")
    data class Control(
        @ProtoNumber(1)
        val data: ControlPayload
    ) : Payload()

    /**
     * WEBSOCKET_FRAME payload wrapper
     * Used for proxying WebSocket frames between external client and local application
     */
    @Serializable
    @SerialName("websocket_frame")
    data class WebSocketFrame(
        @ProtoNumber(1)
        val data: WebSocketFramePayload
    ) : Payload()
}
