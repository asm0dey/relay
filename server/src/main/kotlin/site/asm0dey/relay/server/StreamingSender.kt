package site.asm0dey.relay.server

import io.smallrye.mutiny.coroutines.awaitSuspending
import site.asm0dey.relay.domain.*
import kotlin.time.toKotlinDuration

class StreamingSender(
    socketService: SocketService,
    host: String,
    private val streamId: String,
    config: ServerConfig,
    private val chunkSize: Int
) {
    private val connection = socketService.getConnectionForHost(host)
    private val sender = StreamChunkSender(
        streamId,
        config.maxInflightChunks,
        config.chunkTimeout.toKotlinDuration(),
        sendBinary = { connection.sendBinary(it).awaitSuspending() }
    )
    private var chunkIndex = 0L

    suspend fun sendInit(method: String, path: String, contentType: String?, contentLength: Long?, headers: Map<String, String>) {
        val init = StreamInit.StreamInitPayload(
            correlationId = streamId,
            method = method,
            path = path,
            contentType = contentType,
            contentLength = contentLength,
            headers = headers
        )
        connection.sendBinary(
            Envelope(correlationId = streamId, payload = StreamInit(init)).toByteArray()
        ).awaitSuspending()
    }

    suspend fun sendChunk(data: ByteArray, isLast: Boolean) {
        if (data.size <= chunkSize) {
            sender.sendChunk(
                StreamChunk.StreamChunkPayload(
                    correlationId = streamId,
                    chunkIndex = chunkIndex++,
                    data = data,
                    isLast = isLast
                )
            )
        } else {
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + chunkSize, data.size)
                val chunkData = data.sliceArray(offset until end)
                offset = end
                val last = isLast && offset == data.size
                sender.sendChunk(
                    StreamChunk.StreamChunkPayload(
                        correlationId = streamId,
                        chunkIndex = chunkIndex++,
                        data = chunkData,
                        isLast = last
                    )
                )
            }
        }
    }

    fun onAck(ack: StreamAck.StreamAckPayload) {
        sender.onAck(ack)
    }

    fun cleanup() {
        sender.cleanup()
    }
}
