package org.relay.client.websocket

import jakarta.enterprise.inject.Vetoed
import jakarta.websocket.*
import org.relay.shared.protocol.WebSocketFramePayload
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Client endpoint for connecting to the local WebSocket application.
 * This is a non-CDI managed class that is created programmatically.
 */
@Vetoed
class LocalWebSocketClientEndpoint(
    val correlationId: String,
    val onMessage: (WebSocketFramePayload) -> Unit,
    val onClose: (Int, String) -> Unit,
    val onError: (Throwable) -> Unit
) {
    private val logger = LoggerFactory.getLogger(LocalWebSocketClientEndpoint::class.java)

    @OnOpen
    fun onOpen(session: Session) {
        logger.debug("Local WebSocket opened: correlationId={}", correlationId)
    }

    @OnMessage
    fun onTextMessage(message: String, session: Session) {
        logger.debug("Received text from local WebSocket: correlationId={}, length={}",
            correlationId, message.length)

        val framePayload = WebSocketFramePayload(
            type = WebSocketFramePayload.TYPE_TEXT,
            data = message,
            isBinary = false
        )
        onMessage(framePayload)
    }

    @OnMessage
    fun onBinaryMessage(data: ByteArray, session: Session) {
        logger.debug("Received binary from local WebSocket: correlationId={}, size={}",
            correlationId, data.size)

        val base64Data = Base64.getEncoder().encodeToString(data)
        val framePayload = WebSocketFramePayload(
            type = WebSocketFramePayload.TYPE_BINARY,
            data = base64Data,
            isBinary = true
        )
        onMessage(framePayload)
    }

    @OnClose
    fun onClose(session: Session, closeReason: CloseReason) {
        logger.debug("Local WebSocket closed: correlationId={}, code={}, reason={}",
            correlationId, closeReason.closeCode.code, closeReason.reasonPhrase)
        onClose(closeReason.closeCode.code, closeReason.reasonPhrase)
    }

    @OnError
    fun onError(session: Session, throwable: Throwable) {
        logger.error("Local WebSocket error: correlationId={}", correlationId, throwable)
        onError(throwable)
    }
}
