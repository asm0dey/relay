package org.relay.shared.protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Payload for ERROR messages containing error details.
 */
@Serializable
data class ErrorPayload(
    @ProtoNumber(1)
    val code: ErrorCode,

    @ProtoNumber(2)
    val message: String
)
