package site.asm0dey.relay.server

import io.grpc.ManagedChannelBuilder
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.Cancellable
import org.slf4j.LoggerFactory
import site.asm0dey.relay.client.WsLocalConnector
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.ServerMessage.PayloadCase.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Lightweight test tunnel client for WebSocket proxy testing.
 * Connects to the relay server's gRPC endpoint, registers a domain,
 * and handles WsOpen by connecting to a local WebSocket echo server.
 *
 * Unlike the production TunnelClient, this is test-only and connects
 * the local WS to the same Quarkus test instance (at /local-echo path).
 */
class TestWsTunnelClient(
    private val relayPort: Int,
    private val domain: String,
    private val localWsPort: Int,
    private val localWsPath: String = "/",
    connectorProvider: () -> BasicWebSocketConnector,
) {
    private val log = LoggerFactory.getLogger(TestWsTunnelClient::class.java)
    private val outgoingQueue = LinkedBlockingQueue<ClientMessage>()
    private val wsLocalConnector = WsLocalConnector(connectorProvider)
    private val registrationLatch = CountDownLatch(1)
    private val registrationSuccess = AtomicBoolean(false)
    private var subscription: Cancellable? = null
    private var channel: io.grpc.ManagedChannel? = null
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        val ch = ManagedChannelBuilder
            .forAddress("localhost", relayPort)
            .usePlaintext()
            .build()
        this.channel = ch
        val relay = MutinyTunnelServiceGrpc.newMutinyStub(ch)

        val streams = Multi.createBy().concatenating().streams(
            Multi.createFrom().item(clientMessage {
                correlationId = UUID.randomUUID().toString()
                register = registerRequest {
                    authToken = "secret"
                    desiredSubdomain = domain
                }
            }),
            Multi.createFrom().emitter {
                thread {
                    while (true) {
                        it.emit(outgoingQueue.take())
                    }
                }
            }
        )

        subscription = relay.openTunnel(streams)
            .subscribe().with(
                { serverMessage ->
                    when (serverMessage.payloadCase) {
                        ACK -> handleAck(serverMessage)
                        WS_OPEN -> handleWsOpen(serverMessage)
                        WS_FRAME -> handleWsFrame(serverMessage)
                        WS_CLOSE -> handleWsClose(serverMessage)
                        else -> log.trace("Ignoring message type: {}", serverMessage.payloadCase)
                    }
                },
                { error ->
                    log.error("Tunnel stream error", error)
                    registrationSuccess.set(false)
                    registrationLatch.countDown()
                }
            )

        registrationLatch.await(5, TimeUnit.SECONDS)
        if (!registrationSuccess.get()) {
            throw RuntimeException("Failed to register domain $domain")
        }
    }

    fun stop() {
        wsLocalConnector.closeAll()
        subscription?.cancel()
        channel?.shutdownNow()
        channel = null
        started.set(false)
    }

    private fun handleAck(msg: ServerMessage) {
        if (msg.ack.success) {
            log.debug("Registered domain: {}", msg.ack.assignedSubdomain)
            registrationSuccess.set(true)
        } else {
            log.error("Registration failed: {}", msg.ack.error)
            registrationSuccess.set(false)
        }
        registrationLatch.countDown()
    }

    private fun handleWsOpen(msg: ServerMessage) {
        val wsOpen = msg.wsOpen
        val connectionId = wsOpen.connectionId
        log.debug("WS_OPEN for connectionId={}", connectionId)

        val path = wsOpen.headersMap["path"] ?: localWsPath

        thread {
            val result = wsLocalConnector.connect(
                connectionId = connectionId,
                path = if (path == "/") localWsPath else path,
                headers = wsOpen.headersMap,
                localHost = "localhost",
                localPort = localWsPort,
                outgoingQueue = outgoingQueue,
            )

            outgoingQueue.add(clientMessage {
                correlationId = connectionId
                wsUpgrade = wsUpgradeResponseX {
                    this.connectionId = connectionId
                    accepted = result.accepted
                    statusCode = if (result.accepted) 101 else result.statusCode
                    subprotocol = result.subprotocol
                }
            })
        }
    }

    private fun handleWsFrame(msg: ServerMessage) {
        val frame = msg.wsFrame
        wsLocalConnector.sendFrame(
            frame.connectionId,
            frame.data.toByteArray(),
            frame.isBinary,
            frame.isPing,
        )
    }

    private fun handleWsClose(msg: ServerMessage) {
        val close = msg.wsClose
        wsLocalConnector.close(close.connectionId, close.code, close.reason)
    }
}
