package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Payload for ERROR messages containing error details.
 */
@Serializable
data class ErrorPayload(
    val code: ErrorCode,
    val message: String
)
