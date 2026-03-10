package site.asm0dey.relay.server

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.websocket.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.server.mock.MockScenario
import site.asm0dey.relay.server.mock.MockScenarioRegistry
import site.asm0dey.relay.server.mock.WebSocketSessionStore
import java.net.URI
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@QuarkusTest
class WsTunnelLargeMessageTest {

    @Inject
    lateinit var scenarioRegistry: MockScenarioRegistry

    @Inject
    lateinit var sessionStore: WebSocketSessionStore

    @TestHTTPResource("/ws-upgrade/large-message-domain")
    lateinit var relayWsUri: URI

    @TestHTTPResource("/ws/Secret")
    lateinit var localAppWsUri: URI

    @TestHTTPResource("/mock-ws/large-message-domain")
    lateinit var mockWsUri: URI

    @BeforeEach
    fun setup() {
        scenarioRegistry.clearAll()
        sessionStore.reset()
    }

    @Test
    fun `tunneling of large messages`() = runBlocking {
        val payloadSize = 5000
        val requestPayload = RandomStringUtils.insecure().nextAlphanumeric(payloadSize)
        val responsePayload = RandomStringUtils.insecure().nextAlphanumeric(payloadSize)

        // 1. Setup mock scenario for the target
        scenarioRegistry.register(
            MockScenario(
                channel = "large-message-domain",
                replies = mapOf(requestPayload to responsePayload)
            )
        )

        // 2. Simulate local app connecting to relay
        val container = ContainerProvider.getWebSocketContainer()
        // Increase max message size for the container
        container.defaultMaxBinaryMessageBufferSize = payloadSize * 2
        container.defaultMaxTextMessageBufferSize = payloadSize * 2

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
                                            session.asyncRemote.sendBinary(ByteBuffer.wrap(relayMsg.toByteArray()))
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
                                    session.asyncRemote.sendBinary(ByteBuffer.wrap(response.toByteArray()))
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
                                    session.asyncRemote.sendBinary(ByteBuffer.wrap(closeMsg.toByteArray()))
                                }
                            }, ClientEndpointConfig.Builder.create().apply {
                                configurator(object : ClientEndpointConfig.Configurator() {
                                    override fun beforeRequest(headers: MutableMap<String, MutableList<String>>) {
                                        // Optional: add any needed headers for mock server
                                    }
                                })
                            }.build(), mockWsUri)
                        }

                        is WsMessage -> {
                            val message = payload.value
                            val targetSession = targetSessions[message.wsId]
                            if (targetSession != null) {
                                if (message.type == WsMessage.WsMessagePayload.FrameType.TEXT) {
                                    targetSession.asyncRemote.sendText(String(message.data))
                                } else {
                                    targetSession.asyncRemote.sendBinary(ByteBuffer.wrap(message.data))
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
                session.asyncRemote.sendBinary(ByteBuffer.wrap(registerMsg.toByteArray()))
            }
        }, ClientEndpointConfig.Builder.create().apply {
            configurator(object : ClientEndpointConfig.Configurator() {
                override fun beforeRequest(headers: MutableMap<String, MutableList<String>>) {
                    headers["domain"] = mutableListOf("large-message-domain")
                }
            })
        }.build(), localAppWsUri)

        assertTrue(localAppLatch.await(10, TimeUnit.SECONDS), "Local app failed to connect")

        val receivedQueue = LinkedBlockingQueue<String>()

        // 3. Connect external client to relay server
        val clientSession = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { msg ->
                    receivedQueue.put(msg)
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), relayWsUri)

        clientSession.use {
            localAppSession.use {
                // Wait for potential greeting if defined (none in this case)
                
                // 4. Send large message from external client
                clientSession.asyncRemote.sendText(requestPayload)
                
                // 5. Wait for response
                val receivedResponse = receivedQueue.poll(20, TimeUnit.SECONDS)
                
                // 6. Verify response
                assertEquals(responsePayload, receivedResponse)
            }
        }
    }
}
