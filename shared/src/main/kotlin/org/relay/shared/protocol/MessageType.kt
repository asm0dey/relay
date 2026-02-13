package org.relay.shared.protocol
import kotlinx.serialization.Serializable

/**
 * Enumeration of message types used in the Relay protocol.
 */
@Serializable
enum class MessageType(
    val type: String
) {
    REQUEST("REQUEST"),
    RESPONSE("RESPONSE"),
    ERROR("ERROR"),
    CONTROL("CONTROL");

    companion object {
        /**
         * Finds a MessageType by its string value.
         */
        fun fromString(value: String): MessageType? {
            return entries.find { it.type == value }
        }
    }

    override fun toString(): String {
        return type
    }
}
