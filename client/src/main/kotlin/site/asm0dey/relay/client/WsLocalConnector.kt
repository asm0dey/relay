package site.asm0dey.relay.client

import com.google.protobuf.ByteString
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.quarkus.websockets.next.CloseReason
import io.quarkus.websockets.next.WebSocketClientConnection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import site.asm0dey.relay.domain.*
import java.net.URI
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages local WebSocket connections per connectionId.
 * Connects to the local application's WebSocket server and bridges frames
 * to/from the gRPC tunnel via the outgoing queue.
 */
@ApplicationScoped
class WsLocalConnector {
    private val log = LoggerFactory.getLogger(WsLocalConnector::class.java)

    @Inject
    lateinit var connector: BasicWebSocketConnector

    private val connections = ConcurrentHashMap<String, WebSocketClientConnection>()

    data class ConnectResult(
        val accepted: Boolean,
        val subprotocol: String = "",
        val statusCode: Int = 0,
    )

    fun connect(
        connectionId: String,
        path: String,
        headers: Map<String, String>,
        localHost: String,
        localPort: Int,
        outgoingQueue: BlockingQueue<ClientMessage>,
    ): ConnectResult {
        log.debug("Connecting to local WS: ws://{}:{}{}, connectionId={}", localHost, localPort, path, connectionId)

        try {
            var c = connector
                .baseUri(URI("http://$localHost:$localPort"))
                .path(path)

            // Forward subprotocol if present
            val subprotocols = headers["sec-websocket-protocol"]
            if (subprotocols != null) {
                subprotocols.split(",").map { it.trim() }.forEach { c = c.addSubprotocol(it) }
            }

            c = c.onTextMessage { conn, msg ->
                log.trace("Local WS text for connectionId={}: {}", connectionId, msg)
                outgoingQueue.add(clientMessage {
                    correlationId = connectionId
                    wsFrame = wsFrame {
                        this.connectionId = connectionId
                        data = ByteString.copyFromUtf8(msg)
                        isBinary = false
                    }
                })
            }

            c = c.onBinaryMessage { conn, buf ->
                log.trace("Local WS binary for connectionId={}: {} bytes", connectionId, buf.length())
                outgoingQueue.add(clientMessage {
                    correlationId = connectionId
                    wsFrame = wsFrame {
                        this.connectionId = connectionId
                        data = ByteString.copyFrom(buf.bytes)
                        isBinary = true
                    }
                })
            }

            c = c.onClose { conn, reason ->
                log.debug("Local WS closed for connectionId={}, code={}", connectionId, reason.code)
                connections.remove(connectionId)
                outgoingQueue.add(clientMessage {
                    correlationId = connectionId
                    wsClose = wsCloseX {
                        this.connectionId = connectionId
                        this.code = reason.code
                        this.reason = reason.message ?: ""
                    }
                })
            }

            val connection = c.connectAndAwait()
            connections[connectionId] = connection

            val selectedSubprotocol = connection.subprotocol() ?: ""
            log.debug("Local WS connected for connectionId={}, subprotocol={}", connectionId, selectedSubprotocol)

            return ConnectResult(accepted = true, subprotocol = selectedSubprotocol)
        } catch (e: Exception) {
            log.warn("Local WS connection failed for connectionId={}: {}", connectionId, e.message)
            return ConnectResult(accepted = false, statusCode = 403)
        }
    }

    fun sendFrame(connectionId: String, data: ByteArray, isBinary: Boolean) {
        val conn = connections[connectionId] ?: run {
            log.trace("sendFrame: no local connection for connectionId={}, dropped", connectionId)
            return
        }
        if (isBinary) {
            conn.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(data))
        } else {
            conn.sendTextAndAwait(String(data, Charsets.UTF_8))
        }
    }

    fun close(connectionId: String, code: Int, reason: String) {
        val conn = connections.remove(connectionId) ?: return
        log.debug("Closing local WS for connectionId={}, code={}", connectionId, code)
        try {
            conn.closeAndAwait(CloseReason(code, reason))
        } catch (e: Exception) {
            log.trace("Error closing local WS: {}", e.message)
        }
    }

    fun closeAll() {
        connections.keys.toList().forEach { close(it, 1001, "Going away") }
    }
}
