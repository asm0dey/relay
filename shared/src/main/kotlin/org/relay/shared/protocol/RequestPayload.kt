package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Payload for REQUEST messages containing HTTP request data.
 */
@Serializable
data class RequestPayload(
    val method: String,
    val path: String,
    val query: Map<String, String>? = null,
    val headers: Map<String, String>,
    val body: String? = null,
    val webSocketUpgrade: Boolean = false
)
