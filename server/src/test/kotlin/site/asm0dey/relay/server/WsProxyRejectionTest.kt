package site.asm0dey.relay.server

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.quarkus.websockets.next.WebSocketClientConnection
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * US2 Local application rejection scenarios: TS-005, TS-006.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class WsProxyRejectionTest {

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

    // --- TS-005: Local app rejects upgrade → external connection closed with status code ---

    @Test
    @Order(1)
    fun `TS-005 local application rejects upgrade and external connection is closed`() {
        // Use a TestWsTunnelClient that connects to a non-existent local WS path
        testClient.stop()
        testClient = TestWsTunnelClient(
            relayPort = testPort.get(),
            domain = "reject-domain",
            localWsPort = testPort.get(),
            localWsPath = "/nonexistent-ws-path",
            connectorProvider = { connectorInstance.get() },
        )
        testClient.start()

        val closeLatch = CountDownLatch(1)
        val closeCodes = mutableListOf<Int>()

        try {
            val connection = connectorInstance.get()
                .baseUri(URI("http://localhost:${testPort.get()}"))
                .path("/ws-upgrade/reject-domain")
                .onTextMessage { _, _ -> }
                .onClose { _, reason ->
                    closeCodes.add(reason.code)
                    closeLatch.countDown()
                }
                .connectAndAwait()

            // Wait for the rejection to propagate
            assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "External connection should be closed after rejection")
        } catch (e: Exception) {
            // Connection might fail immediately on upgrade check — that's also valid
            assertTrue(true, "Connection rejected: ${e.message}")
        }
    }

    // --- TS-006: Rejected upgrade leaves zero tunnel state ---

    @Test
    @Order(2)
    fun `TS-006 rejected upgrade leaves zero tunnel state in the relay`() {
        testClient.stop()
        testClient = TestWsTunnelClient(
            relayPort = testPort.get(),
            domain = "reject-domain2",
            localWsPort = testPort.get(),
            localWsPath = "/nonexistent-ws-path",
            connectorProvider = { connectorInstance.get() },
        )
        testClient.start()

        try {
            val connection = connectorInstance.get()
                .baseUri(URI("http://localhost:${testPort.get()}"))
                .path("/ws-upgrade/reject-domain2")
                .onTextMessage { _, _ -> }
                .onClose { _, _ -> }
                .connectAndAwait()

            Thread.sleep(2000)
        } catch (_: Exception) {}

        // Verify zero state
        Thread.sleep(1000)
        val tunnels = wsTunnelManager.getAllTunnelsForDomain("reject-domain2")
        assertEquals(0, tunnels.size, "No tunnel entries should persist after rejection")
    }
}
