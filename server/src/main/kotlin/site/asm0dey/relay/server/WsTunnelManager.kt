package site.asm0dey.relay.server

import io.quarkus.websockets.next.CloseReason
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CompletableDeferred
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import site.asm0dey.relay.domain.WsUpgradeResponseX
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class WsTunnelManager(
    @ConfigProperty(name = "relay.websocket.max-tunnels-per-domain", defaultValue = "100")
    private val maxTunnelsPerDomain: Int,
    @ConfigProperty(name = "relay.websocket.upgrade-timeout", defaultValue = "30s")
    val upgradeTimeout: Duration,
) {
    private val tunnels = ConcurrentHashMap<String, WsTunnel>()
    private val externalConnections = ConcurrentHashMap<String, WebSocketConnection>()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<WsUpgradeResponseX>>()
    private val tunnelCountByDomain = ConcurrentHashMap<String, Int>()

    fun openTunnel(connectionId: String, domain: String, connection: WebSocketConnection): CompletableDeferred<WsUpgradeResponseX> {
        val currentCount = tunnelCountByDomain.getOrDefault(domain, 0)
        if (currentCount >= maxTunnelsPerDomain) {
            throw IllegalStateException("Maximum tunnels ($maxTunnelsPerDomain) reached for domain $domain")
        }

        val tunnel = WsTunnel(
            connectionId = connectionId,
            domain = domain
        )
        val deferred = CompletableDeferred<WsUpgradeResponseX>()
        tunnels[connectionId] = tunnel
        externalConnections[connectionId] = connection
        pendingResponses[connectionId] = deferred
        tunnelCountByDomain[domain] = currentCount + 1

        return deferred
    }

    fun completeResponse(connectionId: String, response: WsUpgradeResponseX) {
        pendingResponses.remove(connectionId)?.complete(response)
        if (response.accepted) {
            tunnels[connectionId]?.establish()
        } else {
            val tunnel = tunnels.remove(connectionId)
            externalConnections.remove(connectionId)
            val domain = tunnel?.domain ?: return
            tunnelCountByDomain.computeIfPresent(domain) { _, count -> (count - 1).coerceAtLeast(0) }
        }
    }

    fun canAcceptTunnel(domain: String): Boolean =
        tunnelCountByDomain.getOrDefault(domain, 0) < maxTunnelsPerDomain

    fun getTunnel(connectionId: String): WsTunnel? = tunnels[connectionId]

    fun getExternalConnection(connectionId: String): WebSocketConnection? = externalConnections[connectionId]

    fun closeTunnel(connectionId: String, code: Int, reason: String) {
        val tunnel = tunnels.remove(connectionId) ?: return
        pendingResponses.remove(connectionId)?.cancel()
        externalConnections.remove(connectionId)
        
        // Ensure tunnel is in CLOSING state before closing
        if (tunnel.state == WsTunnel.TunnelState.OPEN) {
            tunnel.initiateClose(code, reason)
        }
        if (tunnel.state == WsTunnel.TunnelState.CLOSING) {
            tunnel.close()
        }
        tunnelCountByDomain.computeIfPresent(tunnel.domain) { _, count -> (count - 1).coerceAtLeast(0) }
    }

    fun cleanupForDomain(domain: String) {
        val log = LoggerFactory.getLogger(WsTunnelManager::class.java)
        val toRemove = tunnels.filterValues { it.domain == domain }.keys
        toRemove.forEach { connectionId ->
            // Get external connection BEFORE closeTunnel removes it
            val conn = externalConnections[connectionId]
            closeTunnel(connectionId, 1001, "Going Away")
            if (conn != null) {
                try {
                    conn.close(CloseReason(1001, "Going Away"))
                        .subscribe().with(
                            { log.debug("Closed external WS for connectionId={}", connectionId) },
                            { err -> log.debug("Error closing external WS: {}", err.message) }
                        )
                } catch (e: Exception) {
                    log.debug("Error closing external WS for connectionId={}: {}", connectionId, e.message)
                }
            }
        }
    }

    fun getAllTunnelsForDomain(domain: String): List<WsTunnel> =
        tunnels.values.filter { it.domain == domain }
}
