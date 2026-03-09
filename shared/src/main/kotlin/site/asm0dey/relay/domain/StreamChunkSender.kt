package site.asm0dey.relay.domain

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class StreamChunkSender(
    private val streamId: String,
    private val maxInFlight: Int,
    private val timeout: Duration,
    private val sendBinary: suspend (ByteArray) -> Unit
) {
    private val inflightChunks = ConcurrentHashMap<Long, TimeSource.Monotonic.ValueTimeMark>()
    private val timeSource = TimeSource.Monotonic

    suspend fun sendChunk(chunk: StreamChunk.StreamChunkPayload) {
        while (inflightChunks.size >= maxInFlight) {
            delay(10)
        }
        sendBinary(
            Envelope(correlationId = streamId, payload = StreamChunk(chunk)).toByteArray()
        )
        inflightChunks.put(chunk.chunkIndex, timeSource.markNow())
    }

    fun onAck(ack: StreamAck.StreamAckPayload) {
        inflightChunks.remove(ack.chunkIndex)
    }

    fun cleanup() {
        inflightChunks.clear()
    }
}
