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
 * Text message scenarios for WebSocket proxy.
 *
 * Validates UTF-8 handling, empty messages, large payloads,
 * message ordering, and text/binary type preservation through the tunnel.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class WsProxyTextTest {

    @Inject
    lateinit var connectorInstance: Instance<BasicWebSocketConnector>

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
        onBinary: ((WebSocketClientConnection, io.vertx.core.buffer.Buffer) -> Unit)? = null,
        onClose: ((WebSocketClientConnection, CloseReason) -> Unit)? = null,
    ): WebSocketClientConnection {
        var c = connectorInstance.get()
            .baseUri(URI("http://localhost:${testPort.get()}"))
            .path("/ws-upgrade/$domain")

        if (onText != null) c = c.onTextMessage(onText)
        if (onBinary != null) c = c.onBinaryMessage(onBinary)
        if (onClose != null) c = c.onClose(onClose)

        return c.connectAndAwait()
    }

    // --- UTF-8 special characters ---

    @Test
    @Order(1)
    fun `text message with emoji characters is forwarded and echoed correctly`() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)
        val message = "Hello \uD83D\uDE00\uD83C\uDF0D\uD83D\uDE80" // Hello 😀🌍🚀

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        conn.sendTextAndAwait(message)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive echo")
        assertEquals("echo:$message", received.first())
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains(message))

        conn.closeAndAwait()
    }

    @Test
    @Order(2)
    fun `text message with CJK and accented characters is preserved`() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)
        val message = "日本語テスト café résumé Ñoño"

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        conn.sendTextAndAwait(message)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive echo")
        assertEquals("echo:$message", received.first())
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains(message))

        conn.closeAndAwait()
    }

    // --- Empty text message ---

    @Test
    @Order(3)
    fun `empty text message is forwarded and echoed`() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        conn.sendTextAndAwait("")

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive echo for empty message")
        assertEquals("echo:", received.first())
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains(""))

        conn.closeAndAwait()
    }

    // --- Large text message ---

    @Test
    @Order(4)
    fun `large text message is forwarded intact`() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)
        val message = "A".repeat(64 * 1024) // 64 KB text

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        conn.sendTextAndAwait(message)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive echo for large message")
        assertEquals("echo:$message", received.first())
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains(message))

        conn.closeAndAwait()
    }

    // --- Multiple text messages preserve ordering ---

    @Test
    @Order(5)
    fun `multiple text messages arrive in order`() {
        val received = CopyOnWriteArrayList<String>()
        val messageCount = 10
        val latch = CountDownLatch(messageCount)

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        for (i in 0 until messageCount) {
            conn.sendTextAndAwait("msg-$i")
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive all $messageCount echoes")
        assertEquals(messageCount, received.size)
        for (i in 0 until messageCount) {
            assertEquals("echo:msg-$i", received[i], "Message $i should be in order")
        }

        conn.closeAndAwait()
    }

    // --- Interleaved text and binary preserve frame types ---

    @Test
    @Order(6)
    fun `interleaved text and binary messages preserve their frame types`() {
        val receivedText = CopyOnWriteArrayList<String>()
        val receivedBinary = CopyOnWriteArrayList<ByteArray>()
        val textLatch = CountDownLatch(2)
        val binaryLatch = CountDownLatch(2)

        val conn = connectExternalClient(
            onText = { _, msg ->
                receivedText.add(msg)
                textLatch.countDown()
            },
            onBinary = { _, buf ->
                receivedBinary.add(buf.bytes)
                binaryLatch.countDown()
            }
        )
        Thread.sleep(500)

        val binaryA = byteArrayOf(0x01, 0x02, 0x03)
        val binaryB = byteArrayOf(0x04, 0x05, 0x06)

        // Send interleaved: text, binary, text, binary
        conn.sendTextAndAwait("text-1")
        conn.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(binaryA))
        conn.sendTextAndAwait("text-2")
        conn.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(binaryB))

        assertTrue(textLatch.await(3, TimeUnit.SECONDS), "Should receive 2 text echoes")
        assertTrue(binaryLatch.await(3, TimeUnit.SECONDS), "Should receive 2 binary echoes")

        // Text messages should arrive as text frames (via onText handler)
        assertTrue(receivedText.contains("echo:text-1"))
        assertTrue(receivedText.contains("echo:text-2"))

        // Binary messages should arrive as binary frames (via onBinary handler)
        assertTrue(receivedBinary.any { it.contentEquals(binaryA) })
        assertTrue(receivedBinary.any { it.contentEquals(binaryB) })

        // Local app should have received both types separately
        assertTrue(LocalEchoWebSocket.receivedTextMessages.containsAll(listOf("text-1", "text-2")))
        assertEquals(2, LocalEchoWebSocket.receivedBinaryMessages.size)

        conn.closeAndAwait()
    }

    // --- Text with special whitespace and control-adjacent characters ---

    @Test
    @Order(7)
    fun `text message with newlines tabs and special whitespace is preserved`() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)
        val message = "line1\nline2\ttab\r\nwindows-line"

        val conn = connectExternalClient(
            onText = { _, msg ->
                received.add(msg)
                latch.countDown()
            }
        )
        Thread.sleep(500)

        conn.sendTextAndAwait(message)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive echo")
        assertEquals("echo:$message", received.first())
        assertTrue(LocalEchoWebSocket.receivedTextMessages.contains(message))

        conn.closeAndAwait()
    }
}
