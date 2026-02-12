package org.relay.server.tunnel

import jakarta.websocket.Session
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing an active tunnel connection.
 * Contains all metadata associated with a client's WebSocket tunnel.
 *
 * @property subdomain The unique subdomain assigned to this tunnel
 * @property session The WebSocket session for this tunnel
 * @property createdAt When the tunnel was established
 * @property metadata Optional metadata associated with the tunnel
 * @property pendingRequests Map of correlation IDs to pending requests awaiting responses
 * @property webSocketProxies Map of correlation IDs to active WebSocket proxy sessions
 */
data class TunnelConnection(
    val subdomain: String,
    val session: Session,
    val createdAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap(),
    val pendingRequests: MutableMap<String, PendingRequest> = ConcurrentHashMap(),
    val webSocketProxies: MutableMap<String, WebSocketProxySession> = ConcurrentHashMap()
) {
    /**
     * Checks if the tunnel session is still open and active.
     */
    fun isActive(): Boolean = session.isOpen

    /**
     * Closes the tunnel session if it's open.
     */
    fun close(reason: String = "Tunnel closed") {
        // Close all WebSocket proxies first
        webSocketProxies.values.forEach { it.close() }
        webSocketProxies.clear()
        
        if (session.isOpen) {
            session.close()
        }
    }
}
