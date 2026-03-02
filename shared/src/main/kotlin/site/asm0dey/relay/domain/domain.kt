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
    @ProtoNumber(2)
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Clock.System.now(),
    @ProtoOneOf
    val payload: Payload
)

@Serializable
sealed interface Payload

@Serializable
data class Control(@ProtoNumber(11) val value: ControlPayload) : Payload {
    @Serializable
    data class ControlPayload(
        @ProtoNumber(1) val action: ControlAction,
        @ProtoNumber(2) val subdomain: String? = null,
        @ProtoNumber(3) val publicUrl: String? = null
    ) {
        @Serializable
        enum class ControlAction {
            REGISTER, REGISTERED, UNREGISTER, HEARTBEAT, STATUS, SHUTDOWN
        }
    }
}

@Serializable
data class Request(@ProtoNumber(12) val value: RequestPayload) : Payload {
    @Serializable
    data class RequestPayload(
        @ProtoNumber(1) val method: String,
        @ProtoNumber(2) val path: String,
        @ProtoNumber(3) val query: Map<String, String> = hashMapOf(),
        @ProtoNumber(4) val headers: Map<String, String> = hashMapOf(),
        @ProtoNumber(5) val body: ByteArray? = null,
        @ProtoNumber(6) val websocketUpgrade: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RequestPayload

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
            result = 31 * result + query.hashCode()
            result = 31 * result + headers.hashCode()
            result = 31 * result + (body?.contentHashCode() ?: 0)
            return result
        }

    }
}

@Serializable
data class Response(@ProtoNumber(13) val value: ResponsePayload) : Payload {
    @Serializable
    data class ResponsePayload(
        @ProtoNumber(1) val statusCode: Int,
        @ProtoNumber(2) val headers: Map<String, String>,
        @ProtoNumber(3) val body: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResponsePayload

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
data class Error(@ProtoNumber(14) val value: ErrorPayload) : Payload {
    @Serializable
    data class ErrorPayload(
        @ProtoNumber(1) val code: ErrorCode,
        @ProtoNumber(2) val message: String
    )
}

@Serializable
data class StreamInit(@ProtoNumber(15) val value: StreamInitPayload) : Payload {
    @Serializable
    data class StreamInitPayload(
        @ProtoNumber(1) val correlationId: String,
        @ProtoNumber(2) val contentType: String? = null,
        @ProtoNumber(3) val contentLength: Long? = null,
        @ProtoNumber(4) val headers: Map<String, String> = hashMapOf()
    )
}

@Serializable
data class StreamChunk(@ProtoNumber(16) val value: StreamChunkPayload) : Payload {
    @Serializable
    data class StreamChunkPayload(
        @ProtoNumber(1) val correlationId: String,
        @ProtoNumber(2) val chunkIndex: Long,
        @ProtoNumber(3) val data: ByteArray,
        @ProtoNumber(4) val isLast: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StreamChunkPayload

            if (correlationId != other.correlationId) return false
            if (chunkIndex != other.chunkIndex) return false
            if (!data.contentEquals(other.data)) return false
            if (isLast != other.isLast) return false

            return true
        }

        override fun hashCode(): Int {
            var result = correlationId.hashCode()
            result = 31 * result + chunkIndex.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + isLast.hashCode()
            return result
        }
    }
}

@Serializable
data class StreamAck(@ProtoNumber(18) val value: StreamAckPayload) : Payload {
    @Serializable
    data class StreamAckPayload(
        @ProtoNumber(1) val correlationId: String,
        @ProtoNumber(2) val chunkIndex: Long
    )
}

@Serializable
data class StreamError(@ProtoNumber(17) val value: StreamErrorPayload) : Payload {
    @Serializable
    data class StreamErrorPayload(
        @ProtoNumber(1) val correlationId: String,
        @ProtoNumber(2) val code: StreamErrorCode,
        @ProtoNumber(3) val message: String
    )
}

private val proto = ProtoBuf {}

@Suppress("unused")
fun Envelope.toByteArray(): ByteArray = proto.encodeToByteArray(this)

@Suppress("unused")
fun Envelope.Companion.fromByteArray(bytes: ByteArray): Envelope = proto.decodeFromByteArray(bytes)

@Suppress("unused")
fun ByteArray.toEnvelope(): Envelope = proto.decodeFromByteArray(this)

@Serializable
enum class ErrorCode {
    TIMEOUT, UPSTREAM_ERROR, INVALID_REQUEST, SERVER_ERROR, RATE_LIMITED, PROTOCOL_ERROR
}

@Serializable
enum class StreamErrorCode {
    CHUNK_OUT_OF_ORDER,
    CHUNK_MISSING,
    STREAM_CANCELLED,
    TIMEOUT,
    INVALID_REQUEST,
    PROTOCOL_ERROR,
    UPSTREAM_TIMEOUT,
    UPSTREAM_ERROR
}
