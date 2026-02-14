package org.relay.shared.protocol

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.time.Instant

/**
 * Envelope for all protocol messages. Contains metadata and the payload.
 *
 * Protobuf v2.0.0 format with explicit field numbering for forward compatibility.
 */
@Serializable
data class Envelope(
    @ProtoNumber(1)
    val correlationId: String,

    @ProtoNumber(2)
    val type: MessageType,

    @ProtoNumber(3)
    @Contextual
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),

    @ProtoNumber(4)
    val payload: Payload
)
