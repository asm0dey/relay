package site.asm0dey.relay.server

import com.google.protobuf.ByteString
import io.quarkus.websockets.next.*
import io.smallrye.mutiny.Uni
import io.vertx.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import site.asm0dey.relay.domain.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * HttpUpgradeCheck: rejects WebSocket upgrades for unregistered domains or
 * domains that have reached the per-domain tunnel limit.
 */
@ApplicationScoped
class WsUpgradeCheck(
    private val wsTunnelManager: WsTunnelManager,
    @io.quarkus.grpc.GrpcService private val tunnelService: TunnelService,
) : HttpUpgradeCheck {

    override fun perform(ctx: HttpUpgradeCheck.HttpUpgradeContext): Uni<HttpUpgradeCheck.CheckResult> {
        val domain = ctx.pathParam("domain")
        if (domain == null || !tunnelService.hasClient(domain)) {
            return HttpUpgradeCheck.CheckResult.rejectUpgrade(503)
        }
        if (!wsTunnelManager.canAcceptTunnel(domain)) {
            return HttpUpgradeCheck.CheckResult.rejectUpgrade(503)
        }
        return HttpUpgradeCheck.CheckResult.permitUpgrade()
    }

    override fun appliesTo(endpointId: String): Boolean {
        return endpointId == WsProxyEndpoint.ENDPOINT_ID
    }
}

/**
 * WebSocket proxy endpoint: accepts external client connections at /ws-upgrade/{domain},
 * forwards upgrade to the client agent via gRPC tunnel, and bridges frames bidirectionally.
 */
@WebSocket(path = "/ws-upgrade/{domain}", endpointId = WsProxyEndpoint.ENDPOINT_ID)
class WsProxyEndpoint {

    companion object {
        const val ENDPOINT_ID = "ws-proxy"
        private val LOG = LoggerFactory.getLogger(WsProxyEndpoint::class.java)
        // Track connectionId per WebSocketConnection (shared across all instances)
        val connectionIds = ConcurrentHashMap<String, String>() // ws connection id → tunnel connectionId
    }

    @Inject
    lateinit var wsTunnelManager: WsTunnelManager

    @Inject
    @io.quarkus.grpc.GrpcService
    lateinit var tunnelService: TunnelService

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        val domain = connection.pathParam("domain")!!
        val connectionId = UUID.randomUUID().toString()
        connectionIds[connection.id()] = connectionId

        LOG.debug("WS upgrade request for domain={}, connectionId={}", domain, connectionId)

        val clientQueue = tunnelService.getClientQueue(domain)
        if (clientQueue == null) {
            LOG.warn("No client queue for domain={}, closing", domain)
            connection.closeAndAwait(CloseReason(1011, "No client agent"))
            return
        }

        try {
            val deferred = wsTunnelManager.openTunnel(connectionId, domain, connection)

            // Send WsOpen to client agent
            val wsOpenMsg = serverMessage {
                correlationId = connectionId
                wsOpen = wsOpen {
                    this.connectionId = connectionId
                    val req = connection.handshakeRequest()
                    val originalPath = req.path() ?: "/"
                    val stripped = originalPath.removePrefix("/ws-upgrade/$domain")
                    headers["path"] = if (stripped.isEmpty()) "/" else stripped
                    headers["host"] = req.header("host") ?: ""
                    headers["query"] = req.query() ?: ""
                    val secWsProtocol = req.header("sec-websocket-protocol")
                    if (secWsProtocol != null) {
                        headers["sec-websocket-protocol"] = secWsProtocol
                    }
                }
            }
            clientQueue.put(wsOpenMsg)

            // Await upgrade response with timeout
            val timeoutMs = wsTunnelManager.upgradeTimeout.toMillis()
            val response: WsUpgradeResponseX
            try {
                response = kotlinx.coroutines.runBlocking {
                    withTimeout(timeoutMs) {
                        deferred.await()
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                LOG.warn("Upgrade timeout for connectionId={}", connectionId)
                wsTunnelManager.closeTunnel(connectionId, 1011, "Upgrade timeout")
                connection.closeAndAwait(CloseReason(1011, "Upgrade timeout"))
                return
            }

            if (!response.accepted) {
                LOG.debug("Upgrade rejected by local app for connectionId={}", connectionId)
                val closeCode = if (response.statusCode in 1000..4999) response.statusCode else 1008
                connection.closeAndAwait(CloseReason(closeCode, "Upgrade rejected"))
                return
            }

            LOG.debug("Upgrade accepted for connectionId={}, subprotocol={}", connectionId, response.subprotocol)
        } catch (e: IllegalStateException) {
            LOG.warn("Failed to open tunnel: {}", e.message)
            connection.closeAndAwait(CloseReason(1011, e.message ?: "Tunnel error"))
        }
    }

    @OnTextMessage
    fun onTextMessage(connection: WebSocketConnection, message: String) {
        val connectionId = connectionIds[connection.id()] ?: return
        val domain = wsTunnelManager.getTunnel(connectionId)?.domain ?: return
        val clientQueue = tunnelService.getClientQueue(domain) ?: return

        clientQueue.put(serverMessage {
            correlationId = connectionId
            wsFrame = wsFrame {
                this.connectionId = connectionId
                data = ByteString.copyFrom(message.toByteArray(Charsets.UTF_8))
                isBinary = false
            }
        })
    }

    @OnBinaryMessage
    fun onBinaryMessage(connection: WebSocketConnection, message: Buffer) {
        val connectionId = connectionIds[connection.id()] ?: return
        val domain = wsTunnelManager.getTunnel(connectionId)?.domain ?: return
        val clientQueue = tunnelService.getClientQueue(domain) ?: return

        clientQueue.put(serverMessage {
            correlationId = connectionId
            wsFrame = wsFrame {
                this.connectionId = connectionId
                data = ByteString.copyFrom(message.bytes)
                isBinary = true
            }
        })
    }

    @OnPingMessage
    fun onPingMessage(connection: WebSocketConnection, message: Buffer) {
        val connectionId = connectionIds[connection.id()] ?: return
        val domain = wsTunnelManager.getTunnel(connectionId)?.domain ?: return
        val clientQueue = tunnelService.getClientQueue(domain) ?: return

        clientQueue.put(serverMessage {
            correlationId = connectionId
            wsFrame = wsFrame {
                this.connectionId = connectionId
                data = ByteString.copyFrom(message.bytes)
                isBinary = true
                isPing = true
            }
        })
    }

    @OnPongMessage
    fun onPongMessage(connection: WebSocketConnection, message: Buffer) {
        LOG.trace("PONG from external client, connectionId={}", connectionIds[connection.id()])
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        val connectionId = connectionIds.remove(connection.id()) ?: return
        val domain = wsTunnelManager.getTunnel(connectionId)?.domain ?: return
        val clientQueue = tunnelService.getClientQueue(domain)

        LOG.debug("External client closed, connectionId={}", connectionId)

        val closeReason = connection.closeReason()
        val code = closeReason?.code ?: 1000
        val reason = closeReason?.message ?: "normal close"

        if (clientQueue != null) {
            clientQueue.put(serverMessage {
                correlationId = connectionId
                wsClose = wsCloseX {
                    this.connectionId = connectionId
                    this.code = code
                    this.reason = reason
                }
            })
        }

        wsTunnelManager.closeTunnel(connectionId, code, reason)
    }

    @OnError
    fun onError(connection: WebSocketConnection, error: Throwable) {
        val connectionId = connectionIds.remove(connection.id()) ?: return
        LOG.error("WebSocket error for connectionId={}", connectionId, error)
        wsTunnelManager.closeTunnel(connectionId, 1011, error.message ?: "Internal error")
    }
}
