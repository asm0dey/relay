package org.relay.shared.protocol

import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Centralized ProtoBuf configuration for Relay protocol serialization.
 *
 * This configuration is used across all protocol message serialization/deserialization
 * to ensure consistent behavior between client and server.
 *
 * Configuration:
 * - encodeDefaults: false - Omit default values to reduce message size
 */
object ProtoBufConfig {
    /**
     * Shared ProtoBuf format instance for all protocol serialization.
     *
     * Uses compact binary format optimized for network transmission.
     */
    val format: ProtoBuf = ProtoBuf {
        encodeDefaults = false  // Omit default values for bandwidth optimization
    }
}
