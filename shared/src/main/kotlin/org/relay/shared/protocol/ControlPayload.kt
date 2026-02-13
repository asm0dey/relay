package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Payload for CONTROL messages for administrative and configuration actions.
 */
@Serializable
data class ControlPayload(
    val action: String,
    val subdomain: String? = null,
    val publicUrl: String? = null
) {
    companion object {
        // Common control actions
        const val ACTION_REGISTER = "REGISTER"
        const val ACTION_REGISTERED = "REGISTERED"
        const val ACTION_UNREGISTER = "UNREGISTER"
        const val ACTION_HEARTBEAT = "HEARTBEAT"
        const val ACTION_STATUS = "STATUS"
    }
}
