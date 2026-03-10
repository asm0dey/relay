package site.asm0dey.relay.server

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.websocket.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.server.mock.MockScenario
import site.asm0dey.relay.server.mock.MockScenarioRegistry
import site.asm0dey.relay.server.mock.WebSocketSessionStore
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
class WebSocketIntegrationTest {

    @Inject
    lateinit var scenarioRegistry: MockScenarioRegistry

    @Inject
    lateinit var sessionStore: WebSocketSessionStore

    @TestHTTPResource("/ws-upgrade/test-domain")
    lateinit var relayWsUri: URI

    @TestHTTPResource("/ws/Secret")
    lateinit var localAppWsUri: URI

    @TestHTTPResource("/mock-ws/test-domain")
    lateinit var mockWsUri: URI

    @BeforeEach
    fun setup() {
        scenarioRegistry.clearAll()
        sessionStore.reset()
    }

    @Test
    fun `WebSocket upgrade and message proxying`() = runBlocking {
        // 1. Setup mock scenario for the target
        scenarioRegistry.register(
            MockScenario(
                channel = "test-domain",
                greeting = "Hello from Mock!",
                replies = mapOf("ping" to "pong")
            )
        )

        // 2. Simulate local app connecting to relay
        val container = ContainerProvider.getWebSocketContainer()
        val localAppLatch = CountDownLatch(1)
        val targetSessions = ConcurrentHashMap<String, Session>()

        val localAppSession = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(ByteArray::class.java) { bytes ->
                    val envelope = bytes.toEnvelope()
                    when (val payload = envelope.payload) {
                        is Control -> {
                            if (payload.value.action == Control.ControlPayload.ControlAction.REGISTERED) {
                                localAppLatch.countDown()
                            }
                        }
                        is WsUpgrade -> {
                            val upgrade = payload.value
                            // Connect to target (mock server)
                            container.connectToServer(object : Endpoint() {
                                override fun onOpen(targetSession: Session, config: EndpointConfig) {
                                    targetSessions[upgrade.wsId] = targetSession
                                    targetSession.addMessageHandler(
                                        String::class.java,
                                        MessageHandler.Whole<String> { msg ->
                                            // Forward TEXT message back to relay
                                            val relayMsg = Envelope(
                                                correlationId = UUID.randomUUID().toString(),
                                                payload = WsMessage(
                                                    WsMessage.WsMessagePayload(
                                                        wsId = upgrade.wsId,
                                                        type = WsMessage.WsMessagePayload.FrameType.TEXT,
                                                        data = msg.toByteArray()
                                                    )
                                                )
                                            )
                                            session.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(relayMsg.toByteArray()))
                                        })

                                    // Accept upgrade
                                    val response = Envelope(
                                        correlationId = envelope.correlationId,
                                        payload = WsUpgradeResponse(
                                            WsUpgradeResponse.WsUpgradeResponsePayload(
                                                wsId = upgrade.wsId,
                                                accepted = true,
                                                statusCode = 101
                                            )
                                        )
                                    )
                                    session.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(response.toByteArray()))
                                }

                                override fun onClose(targetSession: Session, closeReason: CloseReason) {
                                    targetSessions.remove(upgrade.wsId)
                                    val closeMsg = Envelope(
                                        correlationId = UUID.randomUUID().toString(),
                                        payload = WsClose(
                                            WsClose.WsClosePayload(
                                                wsId = upgrade.wsId,
                                                code = closeReason.closeCode.code,
                                                reason = closeReason.reasonPhrase
                                            )
                                        )
                                    )
                                    session.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(closeMsg.toByteArray()))
                                }
                            }, ClientEndpointConfig.Builder.create().build(), mockWsUri)
                        }

                        is WsMessage -> {
                            val message = payload.value
                            val targetSession = targetSessions[message.wsId]
                            if (targetSession != null) {
                                if (message.type == WsMessage.WsMessagePayload.FrameType.TEXT) {
                                    targetSession.asyncRemote.sendText(String(message.data))
                                } else {
                                    targetSession.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(message.data))
                                }
                            }
                        }

                        is WsClose -> {
                            val close = payload.value
                            targetSessions[close.wsId]?.close(
                                CloseReason(
                                    CloseReason.CloseCodes.getCloseCode(close.code),
                                    close.reason
                                )
                            )
                        }

                        else -> {}
                    }
                }
                
                // Register local app
                val registerMsg = Envelope(
                    correlationId = UUID.randomUUID().toString(),
                    payload = Control(Control.ControlPayload(Control.ControlPayload.ControlAction.REGISTER))
                )
                session.asyncRemote.sendBinary(java.nio.ByteBuffer.wrap(registerMsg.toByteArray()))
            }
        }, ClientEndpointConfig.Builder.create().apply {
            configurator(object : ClientEndpointConfig.Configurator() {
                override fun beforeRequest(headers: MutableMap<String, MutableList<String>>) {
                    headers["domain"] = mutableListOf("test-domain")
                }
            })
        }.build(), localAppWsUri)

        assertTrue(localAppLatch.await(5, TimeUnit.SECONDS), "Local app failed to connect")

        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2) // 1 for greeting, 1 for pong

        // 3. Connect external client to relay server
        val clientSession = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { msg ->
                    received.add(msg)
                    latch.countDown()
                    if (msg == "Hello from Mock!") {
                        session.asyncRemote.sendText("ping")
                    }
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), relayWsUri)

        clientSession.use {
            localAppSession.use {
                // Wait for greeting and pong
                assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for greeting and pong. Received so far: $received")
                // 4. Verify messages
                assertEquals(listOf("Hello from Mock!", "pong"), received)

            }
        }
    }
}
