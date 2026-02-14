package org.relay.shared.protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Payload for REQUEST messages containing HTTP request data.
 */
@Serializable
data class RequestPayload(
    @ProtoNumber(1)
    val method: String,

    @ProtoNumber(2)
    val path: String,

    @ProtoNumber(3)
    val query: Map<String, String>? = null,

    @ProtoNumber(4)
    val headers: Map<String, String>,

    @ProtoNumber(5)
    val body: ByteArray? = null,

    @ProtoNumber(6)
    val webSocketUpgrade: Boolean = false
)
