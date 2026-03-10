package site.asm0dey.relay.server

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class WsTunnelManager {
    var maxTunnels: Int = 100
    private val tunnels = ConcurrentHashMap<String, WsTunnel>()
    private val tunnelCountByConnection = ConcurrentHashMap<String, Int>()

    fun openTunnel(wsId: String, localConnectionId: String, domain: String): WsTunnel {
        val currentCount = tunnelCountByConnection.getOrDefault(localConnectionId, 0)
        if (currentCount >= maxTunnels) {
            throw IllegalStateException("Maximum tunnels ($maxTunnels) reached for connection $localConnectionId")
        }

        val tunnel = WsTunnel(
            wsId = wsId,
            localConnectionId = localConnectionId,
            domain = domain
        )
        tunnels[wsId] = tunnel
        tunnelCountByConnection[localConnectionId] = currentCount + 1

        return tunnel
    }

    fun getTunnel(wsId: String): WsTunnel? = tunnels[wsId]

    fun closeTunnel(wsId: String, code: Int, reason: String) {
        val tunnel = tunnels.remove(wsId) ?: return
        // Ensure tunnel is in CLOSING state before closing
        if (tunnel.state == WsTunnel.TunnelState.OPEN) {
            tunnel.initiateClose(code, reason)
        }
        if (tunnel.state == WsTunnel.TunnelState.CLOSING) {
            tunnel.close()
        }
        tunnelCountByConnection.computeIfPresent(tunnel.localConnectionId) { _, count -> count - 1 }
    }

    fun cleanupForConnection(localConnectionId: String) {
        val toRemove = tunnels.filterValues { it.localConnectionId == localConnectionId }.keys
        toRemove.forEach { wsId ->
            closeTunnel(wsId, 1001, "Connection closed")
        }
    }

    fun getAllTunnelsForConnection(localConnectionId: String): List<WsTunnel> =
        tunnels.values.filter { it.localConnectionId == localConnectionId }
}
