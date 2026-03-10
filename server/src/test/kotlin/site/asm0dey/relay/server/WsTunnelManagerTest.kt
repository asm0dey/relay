package site.asm0dey.relay.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WsTunnelManagerTest {
    private lateinit var manager: WsTunnelManager

    @BeforeEach
    fun setup() {
        manager = WsTunnelManager()
        manager.maxTunnels = 10
    }

    @Test
    fun `openTunnel registers and returns tunnel`() {
        val tunnel = manager.openTunnel(
            wsId = "ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        assertNotNull(tunnel)
        assertEquals("ws-123", tunnel.wsId)
        assertEquals("test-domain", tunnel.domain)
    }

    @Test
    fun `getTunnel returns existing tunnel`() {
        manager.openTunnel(
            wsId = "ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        val tunnel = manager.getTunnel("ws-123")
        assertNotNull(tunnel)
        assertEquals("ws-123", tunnel?.wsId)
    }

    @Test
    fun `getTunnel returns null for non-existent tunnel`() {
        val tunnel = manager.getTunnel("non-existent")
        assertNull(tunnel)
    }

    @Test
    fun `closeTunnel removes tunnel`() {
        manager.openTunnel(
            wsId = "ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        manager.closeTunnel("ws-123", 1000, "Test")

        val tunnel = manager.getTunnel("ws-123")
        assertNull(tunnel)
    }

    @Test
    fun `cleanupForConnection removes all tunnels for connection`() {
        manager.openTunnel("ws-1", "local-conn-1", "domain1")
        manager.openTunnel("ws-2", "local-conn-1", "domain1")
        manager.openTunnel("ws-3", "local-conn-2", "domain2")

        manager.cleanupForConnection("local-conn-1")

        assertNull(manager.getTunnel("ws-1"))
        assertNull(manager.getTunnel("ws-2"))
        assertNotNull(manager.getTunnel("ws-3"))
    }

    @Test
    fun `openTunnel throws when max tunnels reached for connection`() {
        repeat(10) {
            manager.openTunnel("ws-$it", "local-conn-1", "domain1")
        }

        var threw = false
        try {
            manager.openTunnel("ws-11", "local-conn-1", "domain1")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }
}
