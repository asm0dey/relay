package org.relay.shared.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import java.time.Instant

/**
 * Serializer for java.time.Instant.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * Global JSON configuration for the Relay protocol.
 */
val RelayJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/**
 * Extension functions for easy serialization.
 */
inline fun <reified T> T.toJsonElement(): JsonElement = RelayJson.encodeToJsonElement(this)
inline fun <reified T> JsonElement.toObject(): T = RelayJson.decodeFromJsonElement(this)
fun String.toEnvelope(): Envelope = RelayJson.decodeFromString(this)
fun Envelope.toJson(): String = RelayJson.encodeToString(this)
