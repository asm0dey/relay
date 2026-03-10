package site.asm0dey.relay.server

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class WsTunnelTest {
    @Test
    fun `WsTunnel initializes with correct state`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        assertEquals("test-ws-123", tunnel.wsId)
        assertEquals("local-conn-1", tunnel.localConnectionId)
        assertEquals("test-domain", tunnel.domain)
        assertEquals(WsTunnel.TunnelState.CONNECTING, tunnel.state)
        assertFalse(tunnel.isEstablished)
    }

    @Test
    fun `WsTunnel transitions to OPEN when established`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        tunnel.establish()
        assertEquals(WsTunnel.TunnelState.OPEN, tunnel.state)
        assertTrue(tunnel.isEstablished)
    }

    @Test
    fun `WsTunnel transitions to CLOSING when close initiated`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )
        tunnel.establish()

        tunnel.initiateClose(1000, "Normal closure")
        assertEquals(WsTunnel.TunnelState.CLOSING, tunnel.state)
        assertEquals(1000, tunnel.closeCode)
        assertEquals("Normal closure", tunnel.closeReason)
    }

    @Test
    fun `WsTunnel transitions to CLOSED when closed`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )
        tunnel.establish()
        tunnel.initiateClose(1000, "Normal closure")

        tunnel.close()
        assertEquals(WsTunnel.TunnelState.CLOSED, tunnel.state)
    }

    @Test
    fun `WsTunnel throws exception when calling establish multiple times`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        tunnel.establish()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            tunnel.establish()
        }

        assertTrue(exception.message!!.contains("Cannot establish tunnel from state:"))
        assertEquals(WsTunnel.TunnelState.OPEN, tunnel.state)
    }

    @Test
    fun `WsTunnel throws exception when calling initiateClose multiple times`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        tunnel.establish()
        tunnel.initiateClose(1000, "Normal closure")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            tunnel.initiateClose(1001, "Another reason")
        }

        assertTrue(exception.message!!.contains("Cannot initiate close from state:"))
        assertEquals(WsTunnel.TunnelState.CLOSING, tunnel.state)
        assertEquals(1000, tunnel.closeCode)
        assertEquals("Normal closure", tunnel.closeReason)
    }

    @Test
    fun `WsTunnel throws exception when calling close before initiateClose`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        tunnel.establish()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            tunnel.close()
        }

        assertTrue(exception.message!!.contains("Cannot close from state:"))
        assertEquals(WsTunnel.TunnelState.OPEN, tunnel.state)
    }

    @Test
    fun `WsTunnel throws exception when calling establish after close`() {
        val tunnel = WsTunnel(
            wsId = "test-ws-123",
            localConnectionId = "local-conn-1",
            domain = "test-domain"
        )

        tunnel.establish()
        tunnel.initiateClose(1000, "Normal closure")
        tunnel.close()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            tunnel.establish()
        }

        assertTrue(exception.message!!.contains("Cannot establish tunnel from state:"))
        assertEquals(WsTunnel.TunnelState.CLOSED, tunnel.state)
    }
}
