package site.asm0dey.relay.server

import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CompletableDeferred
import site.asm0dey.relay.domain.WsUpgradeResponseX
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class WsTunnelManager {
    var maxTunnels: Int = 100
    private val tunnels = ConcurrentHashMap<String, WsTunnel>()
    private val externalConnections = ConcurrentHashMap<String, WebSocketConnection>()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<WsUpgradeResponseX>>()
    private val tunnelCountByDomain = ConcurrentHashMap<String, Int>()

    fun openTunnel(connectionId: String, domain: String, connection: WebSocketConnection): CompletableDeferred<WsUpgradeResponseX> {
        val currentCount = tunnelCountByDomain.getOrDefault(domain, 0)
        if (currentCount >= maxTunnels) {
            throw IllegalStateException("Maximum tunnels ($maxTunnels) reached for domain $domain")
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
            tunnels.remove(connectionId)
            externalConnections.remove(connectionId)
            tunnelCountByDomain.computeIfPresent(tunnels[connectionId]?.domain ?: "") { _, count -> (count - 1).coerceAtLeast(0) }
        }
    }

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
        val toRemove = tunnels.filterValues { it.domain == domain }.keys
        toRemove.forEach { connectionId ->
            closeTunnel(connectionId, 1001, "Connection closed")
        }
    }

    fun getAllTunnelsForDomain(domain: String): List<WsTunnel> =
        tunnels.values.filter { it.domain == domain }
}
