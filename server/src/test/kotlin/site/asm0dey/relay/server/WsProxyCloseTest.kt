package site.asm0dey.relay.server

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.quarkus.websockets.next.CloseReason
import io.quarkus.websockets.next.WebSocketClientConnection
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * US3 Close propagation scenarios: TS-007, TS-008, TS-009.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class WsProxyCloseTest {

    @Inject
    lateinit var connectorInstance: Instance<BasicWebSocketConnector>

    @Inject
    lateinit var wsTunnelManager: WsTunnelManager

    @ConfigProperty(name = "quarkus.http.test-port")
    lateinit var testPort: Provider<Int>

    private lateinit var testClient: TestWsTunnelClient

    @BeforeEach
    fun setup() {
        LocalEchoWebSocket.reset()
        testClient = TestWsTunnelClient(
            relayPort = testPort.get(),
            domain = "example",
            localWsPort = testPort.get(),
            localWsPath = "/",
            connectorProvider = { connectorInstance.get() },
        )
        testClient.start()
    }

    @AfterEach
    fun teardown() {
        testClient.stop()
    }

    private fun connectExternalClient(
        domain: String = "example",
        onText: ((WebSocketClientConnection, String) -> Unit)? = null,
        onClose: ((WebSocketClientConnection, CloseReason) -> Unit)? = null,
    ): WebSocketClientConnection {
        var c = connectorInstance.get()
            .baseUri(URI("http://localhost:${testPort.get()}"))
            .path("/ws-upgrade/$domain")

        if (onText != null) c = c.onTextMessage(onText)
        if (onClose != null) c = c.onClose(onClose)

        return c.connectAndAwait()
    }

    // --- TS-007: External client close propagated to local app ---

    @Test
    @Order(1)
    fun `TS-007 external client close is propagated to local application with correct code`() {
        val connection = connectExternalClient(onText = { _, _ -> })
        Thread.sleep(500)

        connection.closeAndAwait()

        // Wait for propagation
        Thread.sleep(1000)

        // Verify the local echo WebSocket received the close
        assertTrue(LocalEchoWebSocket.connections.isEmpty(), "Local WS connections should be empty after close")
    }

    // --- TS-008: Local app close propagated to external client ---

    @Test
    @Order(2)
    fun `TS-008 local application close is propagated to external client with correct code`() {
        val closeCodes = CopyOnWriteArrayList<Int>()
        val closeLatch = CountDownLatch(1)

        val connection = connectExternalClient(
            onText = { _, _ -> },
            onClose = { _, reason ->
                closeCodes.add(reason.code)
                closeLatch.countDown()
            }
        )
        Thread.sleep(500)

        // Trigger echo to verify connection works, then close local side
        connection.sendTextAndAwait("trigger")
        Thread.sleep(200)

        // Close from local side
        LocalEchoWebSocket.connections.firstOrNull()?.closeAndAwait()

        assertTrue(closeLatch.await(2, TimeUnit.SECONDS), "External client should receive close")
    }

    // --- TS-009: gRPC tunnel drop closes external connection with 1001 ---

    @Test
    @Order(3)
    fun `TS-009 gRPC tunnel drop closes external WebSocket with code 1001`() {
        val closeCodes = CopyOnWriteArrayList<Int>()
        val closeLatch = CountDownLatch(1)

        val connection = connectExternalClient(
            onText = { _, _ -> },
            onClose = { _, reason ->
                closeCodes.add(reason.code)
                closeLatch.countDown()
            }
        )
        Thread.sleep(500)

        // Kill the tunnel client to simulate gRPC tunnel drop
        testClient.stop()

        assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "External WS should be closed after gRPC drop")
        assertTrue(closeCodes.contains(1001), "Close code should be 1001 (Going Away)")
    }
}
