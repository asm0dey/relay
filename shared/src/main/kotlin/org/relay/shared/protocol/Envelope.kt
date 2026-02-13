package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Envelope for all protocol messages. Contains metadata and the payload.
 */
@Serializable
data class Envelope(
    val correlationId: String,
    val type: MessageType,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val payload: JsonElement
)
