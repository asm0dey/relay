package site.asm0dey.relay.server

import io.quarkus.websockets.next.OnBinaryMessage
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnPingMessage
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import io.vertx.core.buffer.Buffer
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test-scope WebSocket echo server that acts as the local application.
 * Echoes text messages with "echo:" prefix, echoes binary messages as-is,
 * and forwards PING payloads back as binary frames.
 *
 * This endpoint is registered at /local-ws for integration testing.
 * The TunnelClient's WsLocalConnector connects here to simulate the local app.
 */
@WebSocket(path = "/")
class LocalEchoWebSocket {
    private val log = LoggerFactory.getLogger(LocalEchoWebSocket::class.java)

    companion object {
        val receivedTextMessages = CopyOnWriteArrayList<String>()
        val receivedBinaryMessages = CopyOnWriteArrayList<ByteArray>()
        val receivedPingPayloads = CopyOnWriteArrayList<ByteArray>()
        val closeCodes = CopyOnWriteArrayList<Int>()
        val connections = CopyOnWriteArrayList<WebSocketConnection>()

        fun reset() {
            receivedTextMessages.clear()
            receivedBinaryMessages.clear()
            receivedPingPayloads.clear()
            closeCodes.clear()
            connections.clear()
        }
    }

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        log.debug("LocalEchoWebSocket opened: {}", connection.id())
        connections.add(connection)
    }

    @OnTextMessage
    fun onTextMessage(connection: WebSocketConnection, message: String): String {
        log.debug("LocalEchoWebSocket received text: {}", message)
        receivedTextMessages.add(message)
        return "echo:$message"
    }

    @OnBinaryMessage
    fun onBinaryMessage(connection: WebSocketConnection, message: Buffer): Buffer {
        log.debug("LocalEchoWebSocket received binary: {} bytes", message.length())
        receivedBinaryMessages.add(message.bytes)
        return message
    }

    @OnPingMessage
    fun onPingMessage(connection: WebSocketConnection, message: Buffer) {
        log.debug("LocalEchoWebSocket received PING: {} bytes", message.length())
        receivedPingPayloads.add(message.bytes)
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        log.debug("LocalEchoWebSocket closed")
        connections.remove(connection)
    }
}
