package org.relay.server.tunnel

import jakarta.websocket.CloseReason
import jakarta.websocket.Session
import org.slf4j.LoggerFactory

/**
 * Manages a proxied WebSocket connection between external client and tunnel client.
 * Tracks the external WebSocket session and correlates it with the tunnel.
 *
 * @property correlationId The correlation ID for this WebSocket proxy connection
 * @property externalSession The external WebSocket session
 * @property subdomain The subdomain this proxy belongs to
 */
class WebSocketProxySession(
    val correlationId: String,
    val externalSession: Session,
    val subdomain: String
) {
    private val logger = LoggerFactory.getLogger(WebSocketProxySession::class.java)

    @Volatile
    var isOpen: Boolean = true
        private set

    /**
     * Sends a text message to the external WebSocket client.
     */
    fun sendText(message: String): Boolean {
        return try {
            if (externalSession.isOpen) {
                externalSession.asyncRemote.sendText(message)
                true
            } else {
                logger.warn("Cannot send text message, external session closed: correlationId={}", correlationId)
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send text message to external session: correlationId={}", correlationId, e)
            false
        }
    }

    /**
     * Sends binary data to the external WebSocket client.
     */
    fun sendBinary(data: ByteArray): Boolean {
        return try {
            if (externalSession.isOpen) {
                externalSession.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(data))
                true
            } else {
                logger.warn("Cannot send binary message, external session closed: correlationId={}", correlationId)
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send binary message to external session: correlationId={}", correlationId, e)
            false
        }
    }

    /**
     * Sends a ping message to the external WebSocket client.
     */
    fun sendPing(data: ByteArray): Boolean {
        return try {
            if (externalSession.isOpen) {
                externalSession.asyncRemote.sendPing(java.nio.ByteBuffer.wrap(data))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send ping to external session: correlationId={}", correlationId, e)
            false
        }
    }

    /**
     * Sends a pong message to the external WebSocket client.
     */
    fun sendPong(data: ByteArray): Boolean {
        return try {
            if (externalSession.isOpen) {
                externalSession.asyncRemote.sendPong(java.nio.ByteBuffer.wrap(data))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send pong to external session: correlationId={}", correlationId, e)
            false
        }
    }

    /**
     * Closes the external WebSocket session.
     */
    fun close(closeCode: Int = CloseReason.CloseCodes.NORMAL_CLOSURE.code, reason: String = "Proxy closed") {
        isOpen = false
        try {
            if (externalSession.isOpen) {
                externalSession.close(CloseReason(CloseReason.CloseCodes.getCloseCode(closeCode), reason))
            }
        } catch (e: Exception) {
            logger.debug("Error closing external session: correlationId={}", correlationId, e)
        }
    }
}
