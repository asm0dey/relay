package org.relay.client.websocket

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.*
import org.relay.client.config.ClientConfig
import org.relay.shared.protocol.WebSocketFramePayload
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages local WebSocket connections and proxies messages between
 * the server (via tunnel) and local WebSocket applications.
 */
@ApplicationScoped
class LocalWebSocketProxy @Inject constructor(
    private val clientConfig: ClientConfig
) : java.io.Closeable {

    private val logger = LoggerFactory.getLogger(LocalWebSocketProxy::class.java)

    // Map of correlation IDs to local WebSocket sessions
    private val localSessions = ConcurrentHashMap<String, LocalWebSocketSession>()

    /**
     * Handles a WebSocket upgrade request from the server.
     * Establishes a connection to the local WebSocket application.
     */
    fun handleWebSocketUpgrade(
        correlationId: String,
        path: String,
        query: Map<String, String>?,
        onMessageFromLocal: (WebSocketFramePayload) -> Unit
    ): Boolean {
        logger.info("Handling WebSocket upgrade: correlationId={}, path={}", correlationId, path)

        try {
            val localUrl = buildLocalWebSocketUrl(path, query)
            logger.debug("Connecting to local WebSocket: {}", localUrl)

            val container = ContainerProvider.getWebSocketContainer()

            val clientEndpoint = LocalWebSocketClientEndpoint(
                correlationId = correlationId,
                onMessage = onMessageFromLocal,
                onClose = { code: Int, reason: String ->
                    logger.debug("Local WebSocket closed: correlationId={}, code={}, reason={}",
                        correlationId, code, reason)
                    localSessions.remove(correlationId)
                },
                onError = { error: Throwable ->
                    logger.error("Local WebSocket error: correlationId={}", correlationId, error)
                    localSessions.remove(correlationId)
                }
            )

            val session = container.connectToServer(clientEndpoint, URI(localUrl))

            localSessions[correlationId] = LocalWebSocketSession(
                correlationId = correlationId,
                session = session,
                endpoint = clientEndpoint
            )

            logger.info("Local WebSocket connection established: correlationId={}", correlationId)
            return true

        } catch (e: Exception) {
            logger.error("Failed to connect to local WebSocket: correlationId={}, path={}",
                correlationId, path, e)
            return false
        }
    }

    /**
     * Handles a WebSocket frame message from the server (to be forwarded to local app).
     */
    fun handleFrameFromServer(correlationId: String, framePayload: WebSocketFramePayload) {
        val localSession = localSessions[correlationId]

        if (localSession == null) {
            logger.warn("No local WebSocket session found for correlationId={}", correlationId)
            return
        }

        when (framePayload.type) {
            WebSocketFramePayload.TYPE_TEXT -> {
                framePayload.data?.let { data ->
                    try {
                        localSession.session.asyncRemote.sendText(data)
                    } catch (e: Exception) {
                        logger.error("Failed to send text to local WebSocket: correlationId={}", correlationId, e)
                    }
                }
            }
            WebSocketFramePayload.TYPE_BINARY -> {
                framePayload.data?.let { data ->
                    try {
                        val bytes = Base64.getDecoder().decode(data)
                        localSession.session.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(bytes))
                    } catch (e: Exception) {
                        logger.error("Failed to send binary to local WebSocket: correlationId={}", correlationId, e)
                    }
                }
            }
            WebSocketFramePayload.TYPE_CLOSE -> {
                val code = framePayload.closeCode ?: WebSocketFramePayload.CLOSE_NORMAL
                val reason = framePayload.closeReason ?: "Closed by server"
                closeLocalSession(correlationId, code, reason)
            }
            WebSocketFramePayload.TYPE_PING -> {
                framePayload.data?.let { data ->
                    try {
                        val bytes = Base64.getDecoder().decode(data)
                        localSession.session.asyncRemote.sendPing(java.nio.ByteBuffer.wrap(bytes))
                    } catch (e: Exception) {
                        logger.error("Failed to send ping to local WebSocket: correlationId={}", correlationId, e)
                    }
                }
            }
            WebSocketFramePayload.TYPE_PONG -> {
                framePayload.data?.let { data ->
                    try {
                        val bytes = Base64.getDecoder().decode(data)
                        localSession.session.asyncRemote.sendPong(java.nio.ByteBuffer.wrap(bytes))
                    } catch (e: Exception) {
                        logger.error("Failed to send pong to local WebSocket: correlationId={}", correlationId, e)
                    }
                }
            }
            else -> {
                logger.warn("Unknown WebSocket frame type: {}", framePayload.type)
            }
        }
    }

    /**
     * Closes all local WebSocket connections.
     */
    override fun close() {
        logger.info("Closing all local WebSocket connections: count={}", localSessions.size)

        localSessions.values.forEach { session ->
            try {
                if (session.session.isOpen) {
                    session.session.close(CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Client shutting down"))
                }
            } catch (e: Exception) {
                logger.debug("Error closing local WebSocket session: {}", session.correlationId, e)
            }
        }

        localSessions.clear()
    }

    /**
     * Closes a specific local WebSocket session.
     */
    fun closeLocalSession(correlationId: String, code: Int, reason: String) {
        val session = localSessions.remove(correlationId) ?: return

        try {
            if (session.session.isOpen) {
                session.session.close(CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason))
            }
        } catch (e: Exception) {
            logger.debug("Error closing local WebSocket session: {}", correlationId, e)
        }
    }

    /**
     * Builds the local WebSocket URL from the configuration and path.
     */
    fun buildLocalWebSocketUrl(path: String, query: Map<String, String>?): String {
        val baseUrl = clientConfig.localUrl()
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val cleanBase = baseUrl.removeSuffix("/")
        val cleanPath = if (path.startsWith("/")) path else "/$path"

        val queryString = query?.let { params ->
            if (params.isEmpty()) "" else "?" + params.map { "${it.key}=${it.value}" }.joinToString("&")
        } ?: ""

        return "$cleanBase$cleanPath$queryString"
    }

    /**
     * Data class for tracking a local WebSocket session.
     */
    data class LocalWebSocketSession(
        val correlationId: String,
        val session: Session,
        val endpoint: LocalWebSocketClientEndpoint
    )
}
