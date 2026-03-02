package site.asm0dey.relay.server

import jakarta.inject.Singleton
import site.asm0dey.relay.domain.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class StreamManager {
    private val activeStreams = ConcurrentHashMap<String, StreamContext>()

    data class StreamContext(
        val correlationId: String,
        val clientId: String,
        val contentType: String?,
        val startTime: Instant = Clock.System.now(),
        val expectedContentLength: Long? = null,
        @Volatile var lastChunkIndex: Long = 0,
        @Volatile var bytesReceived: Long = 0,
        @Volatile var completed: Boolean = false
    )

    fun initiateStream(correlationId: String, clientId: String, init: StreamInit.StreamInitPayload): StreamContext {
        val context = StreamContext(
            correlationId = correlationId,
            clientId = clientId,
            contentType = init.contentType,
            expectedContentLength = init.contentLength
        )
        activeStreams[correlationId] = context
        return context
    }

    fun getStream(correlationId: String): StreamContext? {
        return activeStreams[correlationId]
    }

    fun cleanup(correlationId: String) {
        activeStreams.remove(correlationId)
    }

    fun receiveChunk(correlationId: String, chunk: StreamChunk.StreamChunkPayload): Result<StreamContext> {
        val context = activeStreams[correlationId] ?: return Result.failure(IllegalArgumentException("Stream not found"))

        context.lastChunkIndex = chunk.chunkIndex
        context.bytesReceived += chunk.data.size
        if (chunk.isLast) {
            context.completed = true
        }

        return Result.success(context)
    }

    fun cleanupForConnection(clientId: String) {
        activeStreams.keys
            .filter { activeStreams[it]?.clientId == clientId }
            .toList()
            .forEach { cleanup(it) }
    }

    fun cleanupAll() {
        activeStreams.clear()
    }
}
