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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * US1 Core proxy scenarios: TS-001, TS-002, TS-003, TS-004.
 *
 * Uses a TestWsTunnelClient (real gRPC tunnel, no mocking — Constitution V).
 * LocalEchoWebSocket at / acts as the local application.
 * BasicWebSocketConnector simulates the external client.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class WsProxyCoreTest {

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

    // --- Helpers ---

    private fun connectExternalClient(
        domain: String = "example",
        onText: ((WebSocketClientConnection, String) -> Unit)? = null,
        onBinary: ((WebSocketClientConnection, io.vertx.core.buffer.Buffer) -> Unit)? = null,
        onClose: ((WebSocketClientConnection, io.quarkus.websockets.next.CloseReason) -> Unit)? = null,
    ): WebSocketClientConnection {
        var c = connectorInstance.get()
            .baseUri(URI("http://localhost:${testPort.get()}"))
            .path("/ws-upgrade/$domain")

        if (onText != null) c = c.onTextMessage(onText)
        if (onBinary != null) c = c.onBinaryMessage(onBinary)
        if (onClose != null) c = c.onClose(onClose)

        return c.connectAndAwait()
    }

    // --- TS-001: End-to-end WebSocket connection and text message exchange ---

    @Test
    @Order(1)
    fun `TS-001 external client establishes connection and exchanges text messages`() {
        val receivedMessages = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val connection = connectExternalClient(
            onText = { _, msg ->
                receivedMessages.add(msg)
                latch.countDown()
            }
        )

        // Wait for tunnel to be established
        Thread.sleep(500)

        connection.sendTextAndAwait("hello")

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive echo within 2 seconds (SC-001)")
        assertEquals("echo:hello", receivedMessages.first())

        connection.closeAndAwait()
    }

    // --- TS-002: Frame forwarding external→local (text + binary) ---

    @Test
    @Order(2)
    fun `TS-002 external client sends text frame and local app receives identical content`() {
        val latch = CountDownLatch(1)

        val connection = connectExternalClient(
            onText = { _, _ -> latch.countDown() }
        )
        Thread.sleep(500)

        connection.sendTextAndAwait("hello world")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains("hello world"))

        connection.closeAndAwait()
    }

    @Test
    @Order(3)
    fun `TS-002 external client sends binary frame and local app receives identical content`() {
        val latch = CountDownLatch(1)
        val binaryContent = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        val connection = connectExternalClient(
            onBinary = { _, _ -> latch.countDown() }
        )
        Thread.sleep(500)

        connection.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(binaryContent))

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(LocalEchoWebSocket.receivedBinaryMessages.any { it.contentEquals(binaryContent) })

        connection.closeAndAwait()
    }

    // --- TS-003: Frame forwarding local→external (text + binary) ---

    @Test
    @Order(4)
    fun `TS-003 local app sends text frame and external client receives identical content`() {
        val receivedMessages = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val connection = connectExternalClient(
            onText = { _, msg ->
                receivedMessages.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        connection.sendTextAndAwait("hello client")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals("echo:hello client", receivedMessages.first())

        connection.closeAndAwait()
    }

    @Test
    @Order(5)
    fun `TS-003 local app sends binary frame and external client receives identical content`() {
        val receivedBinary = CopyOnWriteArrayList<ByteArray>()
        val latch = CountDownLatch(1)
        val binaryContent = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())

        val connection = connectExternalClient(
            onBinary = { _, buf ->
                receivedBinary.add(buf.bytes)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        connection.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(binaryContent))

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(receivedBinary.any { it.contentEquals(binaryContent) })

        connection.closeAndAwait()
    }

    // --- TS-004: Unregistered domain rejected ---

    @Test
    @Order(6)
    fun `TS-004 upgrade request to unregistered domain is rejected`() {
        val exception = assertThrows<Exception> {
            connectExternalClient(domain = "unknown-domain")
        }
        assertTrue(exception != null, "Connection to unregistered domain should fail")
    }
}
