package site.asm0dey.relay.server

import java.io.InputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class ChunkInputStream(private val queue: BlockingQueue<RequestResult>) : InputStream() {
    private var currentChunk: ByteArray? = null
    private var index = 0
    private var done = false

    override fun read(): Int {
        if (isComplete()) return -1

        if (processChunkQueue()) return -1

        return currentChunk!![index++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isComplete()) return -1

        if (processChunkQueue()) return -1

        val available = currentChunk!!.size - index
        val toCopy = minOf(len, available)
        System.arraycopy(currentChunk!!, index, b, off, toCopy)
        index += toCopy
        return toCopy
    }

    private fun processChunkQueue(): Boolean {
        if (isChunkExhausted()) {
            val result = queue.poll(30, TimeUnit.SECONDS) ?: throw RuntimeException("Timeout waiting for chunk")
            when (result) {
                is RequestResult.Metadata -> throw RuntimeException("Received unexpected metadata")
                is RequestResult.Chunk -> {
                    currentChunk = result.chunk.data.toByteArray()
                    index = 0
                }

                RequestResult.Done -> {
                    done = true
                    return true
                }
            }
        }
        return false
    }

    private fun isComplete(): Boolean = done && isChunkExhausted()
    private fun isChunkExhausted(): Boolean = currentChunk == null || index >= currentChunk!!.size


}