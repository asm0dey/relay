package site.asm0dey.relay.client

import io.smallrye.mutiny.coroutines.awaitSuspending
import org.eclipse.microprofile.config.inject.ConfigProperty
import site.asm0dey.relay.domain.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class StreamingSender(
    private val wsClient: WsClient,
    streamId: String,

    @ConfigProperty(name = "relay.max-in-flight-chunks", defaultValue = "10")
    private val maxInFlightChunks: Int,

    @ConfigProperty(name = "relay.chunk-timeout", defaultValue = "30s")
    private val chunkTimeout: Duration
) {
    private val sender = StreamChunkSender(
        streamId,
        maxInFlightChunks,
        sendBinary = { wsClient.connection.sendBinary(it).awaitSuspending() }
    )

    fun onAck(ack: StreamAck.StreamAckPayload) {
        sender.onAck(ack)
    }

    fun onError() {
        sender.cleanup()
    }
}
