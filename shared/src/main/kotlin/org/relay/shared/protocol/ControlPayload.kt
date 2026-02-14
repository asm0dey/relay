package org.relay.shared.protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Payload for CONTROL messages for administrative and configuration actions.
 */
@Serializable
data class ControlPayload(
    @ProtoNumber(1)
    val action: String,

    @ProtoNumber(2)
    val subdomain: String? = null,

    @ProtoNumber(3)
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
