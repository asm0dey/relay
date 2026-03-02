package site.asm0dey.relay.client

import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.core.buffer.Buffer
import kotlinx.coroutines.delay
import kotlinx.serialization.protobuf.ProtoBuf
import org.eclipse.microprofile.config.inject.ConfigProperty
import site.asm0dey.relay.domain.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class StreamingSender(
    private val wsClient: WsClient,
    private val streamId: String,

    @ConfigProperty(name = "relay.max-in-flight-chunks", defaultValue = "10")
    private val maxInFlightChunks: Int,

    @ConfigProperty(name = "relay.chunk-timeout", defaultValue = "30s")
    private val chunkTimeout: Duration
) {
    private val sender = StreamChunkSender(wsClient, streamId, maxInFlightChunks, chunkTimeout)
    private var chunkIndex = 0L

    suspend fun sendInit(contentType: String?, contentLength: Long?, headers: Map<String, String>) {
        val init = StreamInit.StreamInitPayload(
            correlationId = streamId,
            contentType = contentType,
            contentLength = contentLength,
            headers = headers
        )
        wsClient.connection.sendBinary(
            Envelope(correlationId = streamId, payload = StreamInit(init)).toByteArray()
        ).awaitSuspending()
    }

    suspend fun sendChunk(data: ByteArray, isLast: Boolean) {
        sender.sendChunk(StreamChunk.StreamChunkPayload(
            correlationId = streamId,
            chunkIndex = chunkIndex++,
            data = data,
            isLast = isLast
        ))
    }

    fun onAck(ack: StreamAck.StreamAckPayload) {
        sender.onAck(ack)
    }

    fun onError(error: StreamError.StreamErrorPayload) {
        sender.cleanup()
    }

    class StreamChunkSender(
        private val wsClient: WsClient,
        private val streamId: String,
        private val maxInFlight: Int,
        private val timeout: Duration
    ) {
        private val inflightChunks = ConcurrentHashMap<Long, TimeSource.Monotonic.ValueTimeMark>()
        private val timeSource = TimeSource.Monotonic

        suspend fun sendChunk(chunk: StreamChunk.StreamChunkPayload) {
            while (inflightChunks.size >= maxInFlight) {
                delay(10)
            }
            wsClient.connection.sendBinary(
                Envelope(correlationId = streamId, payload = StreamChunk(chunk)).toByteArray()
            ).awaitSuspending()
            inflightChunks.put(chunk.chunkIndex, timeSource.markNow())
        }

        fun onAck(ack: StreamAck.StreamAckPayload) {
            inflightChunks.remove(ack.chunkIndex)
        }

        fun cleanup() {
            inflightChunks.clear()
        }
    }
}
