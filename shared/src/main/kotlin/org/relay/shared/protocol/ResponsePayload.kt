package org.relay.shared.protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Payload for RESPONSE messages containing HTTP response data.
 */
@Serializable
data class ResponsePayload(
    @ProtoNumber(1)
    val statusCode: Int,

    @ProtoNumber(2)
    val headers: Map<String, String>,

    @ProtoNumber(3)
    val body: ByteArray? = null
)
