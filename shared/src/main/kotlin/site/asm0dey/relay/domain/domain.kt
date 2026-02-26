@file:OptIn(ExperimentalSerializationApi::class)

package site.asm0dey.relay.domain

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf
import kotlin.time.Clock
import kotlin.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())

    override fun deserialize(decoder: Decoder) = Instant.fromEpochMilliseconds(decoder.decodeLong())

}

@Serializable
data class Envelope(
    @ProtoNumber(1)
    val correlationId: String,
    @ProtoNumber(3)
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Clock.System.now(),
    @ProtoNumber(4)
    @ProtoOneOf
    val payload: Payload
)

@Serializable
enum class MessageType {
    REQUEST, RESPONSE, ERROR, CONTROL

}

@Serializable
sealed interface Payload

@Serializable
data class Response(@ProtoNumber(1) val payload: Payload) : Payload {
    @Serializable
    data class Payload(
        @ProtoNumber(1)
        val statusCode: Int,
        @ProtoNumber(2)
        val headers: Map<String, String>,
        @ProtoNumber(3)
        val body: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Payload

            if (statusCode != other.statusCode) return false
            if (headers != other.headers) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = statusCode
            result = 31 * result + headers.hashCode()
            result = 31 * result + (body?.contentHashCode() ?: 0)
            return result
        }
    }

}

@Serializable
data class Request(@ProtoNumber(1) val payload: Payload) : Payload {
    @Serializable
    data class Payload(
        @ProtoNumber(1)
        val method: String,
        @ProtoNumber(2)
        val path: String,
        @ProtoNumber(3)
        val query: Map<String, String>? = null,
        @ProtoNumber(4)
        val headers: Map<String, String>? = null,
        @ProtoNumber(5)
        val body: ByteArray? = null,
        @ProtoNumber(6)
        val websocketUpgrade: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Payload

            if (websocketUpgrade != other.websocketUpgrade) return false
            if (method != other.method) return false
            if (path != other.path) return false
            if (query != other.query) return false
            if (headers != other.headers) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = websocketUpgrade.hashCode()
            result = 31 * result + method.hashCode()
            result = 31 * result + path.hashCode()
            result = 31 * result + (query?.hashCode() ?: 0)
            result = 31 * result + (headers?.hashCode() ?: 0)
            result = 31 * result + (body?.contentHashCode() ?: 0)
            return result
        }
    }

}

@Serializable
data class Error(@ProtoNumber(1) val payload: Payload) : Payload {
    @Serializable
    data class Payload(
        @ProtoNumber(1) val code: ErrorCode,
        @ProtoNumber(2) val message: String
    )

}

@Serializable
enum class ErrorCode {
    TIMEOUT, UPSTREAM_ERROR, INVALID_REQUEST, SERVER_ERROR, RATE_LIMITED, PROTOCOL_ERROR
}

@Serializable
data class Control(@ProtoNumber(1) val payload: Payload) : Payload {
    @Serializable
    data class Payload(
        @ProtoNumber(1)
        val action: ControlAction,
        @ProtoNumber(2)
        val subdomain: String? = null,
        @ProtoNumber(3)
        val publicUrl: String? = null
    ) {
        @Serializable
        enum class ControlAction {
            REGISTER, REGISTERED, UNREGISTER, HEARTBEAT, STATUS, SHUTDOWN
        }
    }

}

private val proto = ProtoBuf {}

@Suppress("unused")
fun Envelope.toByteArray(): ByteArray = proto.encodeToByteArray(this)

@Suppress("unused")
fun Envelope.Companion.fromByteArray(bytes: ByteArray): Envelope = proto.decodeFromByteArray(bytes)

@Suppress("unused")
fun ByteArray.toEnvelope(): Envelope = proto.decodeFromByteArray(this)