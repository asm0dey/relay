package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Payload for RESPONSE messages containing HTTP response data.
 */
@Serializable
data class ResponsePayload(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String? = null
)
