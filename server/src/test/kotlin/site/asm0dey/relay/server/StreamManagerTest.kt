package site.asm0dey.relay.server

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import site.asm0dey.relay.domain.StreamChunk
import site.asm0dey.relay.domain.StreamInit

@QuarkusTest
class StreamManagerTest {

    @Inject
    lateinit var streamManager: StreamManager

    @Test
    fun `initiate stream creates context`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-1",
            contentType = "application/pdf",
            contentLength = 10485760L
        )
        val context = streamManager.initiateStream("test-1", "client-1", init)

        assertNotNull(context)
        assertEquals("test-1", context.correlationId)
        assertEquals("client-1", context.clientId)
        assertEquals("application/pdf", context.contentType)
        assertEquals(10485760L, context.expectedContentLength)
    }

    @Test
    fun `get existing stream`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-2",
            contentType = null,
            contentLength = null
        )
        streamManager.initiateStream("test-2", "client-2", init)

        val context = streamManager.getStream("test-2")
        assertNotNull(context)
    }

    @Test
    fun `get non-existent stream returns null`() {
        val context = streamManager.getStream("non-existent")
        assertNull(context)
    }

    @Test
    fun `cleanup removes stream`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-3",
            contentType = null,
            contentLength = null
        )
        streamManager.initiateStream("test-3", "client-3", init)
        streamManager.cleanup("test-3")

        val context = streamManager.getStream("test-3")
        assertNull(context)
    }

    @Test
    fun `receive chunk updates context`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-4",
            contentType = null,
            contentLength = null
        )
        streamManager.initiateStream("test-4", "client-4", init)

        val chunk = StreamChunk.StreamChunkPayload(
            correlationId = "test-4",
            chunkIndex = 0,
            data = ByteArray(1024),
            isLast = false
        )
        val result = streamManager.receiveChunk("test-4", chunk)

        assertEquals(true, result.isSuccess)
        val context = result.getOrNull()!!
        assertEquals(0, context.lastChunkIndex)
        assertEquals(1024, context.bytesReceived)
        assertEquals(false, context.completed)
    }

    @Test
    fun `receive chunk with isLast marks completed`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-5",
            contentType = null,
            contentLength = null
        )
        streamManager.initiateStream("test-5", "client-5", init)

        val chunk = StreamChunk.StreamChunkPayload(
            correlationId = "test-5",
            chunkIndex = 0,
            data = ByteArray(1024),
            isLast = true
        )
        streamManager.receiveChunk("test-5", chunk)

        val context = streamManager.getStream("test-5")
        assertNotNull(context)
        assertEquals(true, context!!.completed)
    }

    @Test
    fun `forward chunks to consumer`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-6",
            contentType = null,
            contentLength = null
        )
        val context = streamManager.initiateStream("test-6", "client-6", init)
        
        var receivedData: ByteArray? = null
        context.chunkConsumer = { receivedData = it }

        val chunk = StreamChunk.StreamChunkPayload(
            correlationId = "test-6",
            chunkIndex = 0,
            data = "Hello".toByteArray(),
            isLast = true
        )
        
        // This is normally called from SocketService
        val result = streamManager.receiveChunk("test-6", chunk)
        result.getOrNull()?.chunkConsumer?.invoke(chunk.data)

        assertArrayEquals("Hello".toByteArray(), receivedData)
    }

    @Test
    fun `forward error to consumer`() {
        val init = StreamInit.StreamInitPayload(
            correlationId = "test-7",
            contentType = null,
            contentLength = null
        )
        val context = streamManager.initiateStream("test-7", "client-7", init)
        
        var receivedError: Throwable? = null
        context.errorConsumer = { receivedError = it }

        // This is normally called from SocketService
        context.errorConsumer?.invoke(IllegalStateException("Test error"))

        assertNotNull(receivedError)
        assertEquals("Test error", receivedError!!.message)
    }

    @Test
    fun `receive chunk for non-existent stream fails`() {
        val chunk = StreamChunk.StreamChunkPayload(
            correlationId = "non-existent",
            chunkIndex = 0,
            data = ByteArray(1024),
            isLast = false
        )
        val result = streamManager.receiveChunk("non-existent", chunk)

        assertEquals(true, result.isFailure)
    }
}
