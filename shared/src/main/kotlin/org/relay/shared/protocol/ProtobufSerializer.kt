package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * Protobuf serialization utilities for the Relay protocol.
 *
 * Provides type-safe serialization/deserialization using kotlinx.serialization-protobuf.
 * All operations use the shared ProtoBufConfig for consistency.
 *
 * Metrics tracking (for T044):
 * - Message sizes tracked via Micrometer metrics in server/client layers
 * - Message counts tracked by type and direction
 */
@OptIn(ExperimentalSerializationApi::class)
object ProtobufSerializer {

    /**
     * Serializes a value to Protobuf binary format.
     *
     * @param serializer The serializer for type T
     * @param value The value to serialize
     * @return Protobuf binary representation
     * @throws SerializationException if serialization fails
     */
    fun <T> encodeToByteArray(serializer: KSerializer<T>, value: T): ByteArray {
        return try {
            ProtoBufConfig.format.encodeToByteArray(serializer, value)
        } catch (e: Exception) {
            throw SerializationException("Failed to encode to Protobuf: ${e.message}", e)
        }
    }

    /**
     * Deserializes a value from Protobuf binary format.
     *
     * @param serializer The serializer for type T
     * @param bytes The Protobuf binary data
     * @return Deserialized value
     * @throws SerializationException if deserialization fails
     */
    fun <T> decodeFromByteArray(serializer: KSerializer<T>, bytes: ByteArray): T {
        return try {
            if (bytes.isEmpty()) {
                throw SerializationException("Cannot decode Protobuf from empty byte array")
            }
            ProtoBufConfig.format.decodeFromByteArray(serializer, bytes)
        } catch (e: SerializationException) {
            throw e
        } catch (e: Exception) {
            throw SerializationException("Failed to decode from Protobuf: Invalid binary data (${e.javaClass.simpleName}: ${e.message})", e)
        }
    }

    /**
     * Convenience function: Serialize an Envelope to Protobuf bytes.
     *
     * @param envelope The envelope to serialize
     * @return Protobuf binary representation
     */
    fun encodeEnvelope(envelope: Envelope): ByteArray {
        return encodeToByteArray(Envelope.serializer(), envelope)
    }

    /**
     * Convenience function: Deserialize an Envelope from Protobuf bytes.
     *
     * @param bytes The Protobuf binary data
     * @return Deserialized envelope
     */
    fun decodeEnvelope(bytes: ByteArray): Envelope {
        return decodeFromByteArray(Envelope.serializer(), bytes)
    }
}
