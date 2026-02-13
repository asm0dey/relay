package org.relay.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.relay.client.websocket.WebSocketClientEndpoint
import org.relay.client.config.ClientConfig
import org.relay.shared.protocol.*
import org.mockito.Mockito.*

/**
 * T022: Connection Feedback Tests
 *
 * Tests that verify user-facing feedback messages when connection succeeds.
 *
 * Test Scenarios:
 * - TS-007: Connection success shows "Tunnel ready: {publicUrl} -> localhost:{port}"
 *
 * Constitutional compliance: Test-First, Observable operations
 *
 * Note: These tests simulate receiving REGISTERED control messages and verify
 * the connection feedback is displayed correctly.
 */
class ConnectionFeedbackTest {

    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger
    private lateinit var clientEndpoint: WebSocketClientEndpoint
    private lateinit var clientConfig: ClientConfig

    @BeforeEach
    fun setup() {
        // Setup log capturing for WebSocketClientEndpoint
        logger = LoggerFactory.getLogger(WebSocketClientEndpoint::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>()
        logAppender.start()
        logger.addAppender(logAppender)

        // Setup mock config
        clientConfig = mock(ClientConfig::class.java)
        `when`(clientConfig.localUrl()).thenReturn("http://localhost:3000")
        `when`(clientConfig.serverUrl()).thenReturn("wss://tun.example.com/ws")
        `when`(clientConfig.secretKey()).thenReturn(java.util.Optional.of("test-secret"))

        val reconnectConfig = mock(ClientConfig.ReconnectConfig::class.java)
        `when`(reconnectConfig.enabled()).thenReturn(false)
        `when`(clientConfig.reconnect()).thenReturn(reconnectConfig)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    @Test
    fun `TS-007 connection success shows tunnel ready message with public URL and local port`() {
        // Given: A WebSocketClientEndpoint instance
        val localWebSocketProxy = mock(org.relay.client.websocket.LocalWebSocketProxy::class.java)
        val localHttpProxy = mock(org.relay.client.proxy.LocalHttpProxy::class.java)
        val reconnectionHandler = mock(org.relay.client.retry.ReconnectionHandler::class.java)

        clientEndpoint = WebSocketClientEndpoint(
            clientConfig,
            reconnectionHandler,
            localHttpProxy,
            localWebSocketProxy
        )

        // When: A REGISTERED control message is received
        val controlPayload = ControlPayload(
            action = ControlPayload.ACTION_REGISTERED,
            subdomain = "abc123",
            publicUrl = "https://abc123.tun.example.com"
        )

        val envelope = Envelope(
            correlationId = "test-123",
            type = MessageType.CONTROL,
            payload = controlPayload.toJsonElement()
        )

        val message = envelope.toJson()

        // Simulate message reception (bypassing WebSocket session requirement)
        clientEndpoint.onMessage(message, mock(jakarta.websocket.Session::class.java))

        // Then: The tunnel ready message should be logged
        val logMessages = logAppender.list.map { it.formattedMessage }

        val hasTunnelReadyMessage = logMessages.any {
            it.contains("Tunnel ready: https://abc123.tun.example.com -> localhost:3000")
        }

        assertTrue(
            hasTunnelReadyMessage,
            "Expected tunnel ready message to be logged with format: 'Tunnel ready: {publicUrl} -> localhost:{port}'\n" +
            "Actual log messages:\n${logMessages.joinToString("\n")}"
        )
    }

    @Test
    fun `connection ready message uses INFO log level`() {
        // Given: A WebSocketClientEndpoint instance
        val localWebSocketProxy = mock(org.relay.client.websocket.LocalWebSocketProxy::class.java)
        val localHttpProxy = mock(org.relay.client.proxy.LocalHttpProxy::class.java)
        val reconnectionHandler = mock(org.relay.client.retry.ReconnectionHandler::class.java)

        clientEndpoint = WebSocketClientEndpoint(
            clientConfig,
            reconnectionHandler,
            localHttpProxy,
            localWebSocketProxy
        )

        // When: A REGISTERED control message is received
        val controlPayload = ControlPayload(
            action = ControlPayload.ACTION_REGISTERED,
            subdomain = "test",
            publicUrl = "https://test.tun.example.com"
        )

        val envelope = Envelope(
            correlationId = "test-456",
            type = MessageType.CONTROL,
            payload = controlPayload.toJsonElement()
        )

        val message = envelope.toJson()
        clientEndpoint.onMessage(message, mock(jakarta.websocket.Session::class.java))

        // Then: The message should be at INFO level
        val infoLevelLogs = logAppender.list.filter {
            it.level == Level.INFO
        }

        val hasTunnelReadyAtInfo = infoLevelLogs.any {
            it.formattedMessage.contains("Tunnel ready:")
        }

        assertTrue(
            hasTunnelReadyAtInfo,
            "Tunnel ready message should be logged at INFO level for user visibility.\n" +
            "INFO logs: ${infoLevelLogs.map { it.formattedMessage }.joinToString("\n")}"
        )
    }

    @Test
    fun `connection ready message format includes arrow separator`() {
        // Given: A WebSocketClientEndpoint with different port
        `when`(clientConfig.localUrl()).thenReturn("http://localhost:8080")

        val localWebSocketProxy = mock(org.relay.client.websocket.LocalWebSocketProxy::class.java)
        val localHttpProxy = mock(org.relay.client.proxy.LocalHttpProxy::class.java)
        val reconnectionHandler = mock(org.relay.client.retry.ReconnectionHandler::class.java)

        clientEndpoint = WebSocketClientEndpoint(
            clientConfig,
            reconnectionHandler,
            localHttpProxy,
            localWebSocketProxy
        )

        // When: A REGISTERED control message is received
        val controlPayload = ControlPayload(
            action = ControlPayload.ACTION_REGISTERED,
            subdomain = "myapp",
            publicUrl = "https://myapp.tun.example.com"
        )

        val envelope = Envelope(
            correlationId = "test-789",
            type = MessageType.CONTROL,
            payload = controlPayload.toJsonElement()
        )

        val message = envelope.toJson()
        clientEndpoint.onMessage(message, mock(jakarta.websocket.Session::class.java))

        // Then: Message format should be correct
        val logMessages = logAppender.list.map { it.formattedMessage }
        val tunnelReadyMessages = logMessages.filter { it.contains("Tunnel ready:") }

        assertFalse(tunnelReadyMessages.isEmpty(), "Should have at least one 'Tunnel ready:' message")

        // Verify format includes the arrow separator
        val messageMatches = tunnelReadyMessages.any { message ->
            message.matches(Regex("Tunnel ready: https://[^ ]+ -> localhost:\\d+"))
        }

        assertTrue(
            messageMatches,
            "Message should match format 'Tunnel ready: {publicUrl} -> localhost:{port}'\n" +
            "Actual messages: ${tunnelReadyMessages.joinToString("\n")}"
        )
    }
}
