@file:OptIn(ExperimentalTime::class)

package site.asm0dey.relay.server.ws

import io.quarkus.websockets.next.*
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.server.ServerConfig
import site.asm0dey.relay.server.SocketService
import site.asm0dey.relay.server.WsTunnelManager
import java.lang.reflect.Type
import java.util.*
import kotlin.time.ExperimentalTime

@OptIn(DelicateCoroutinesApi::class)
@WebSocket(path = "/ws-upgrade/{domain}")
class ExternalWsEndpoint {
    private val wsIdKey = UserData.TypedKey.forString("wsId")
    private val domainKey = UserData.TypedKey.forString("domain")

    @Inject
    lateinit var socketService: SocketService

    @Inject
    lateinit var wsTunnelManager: WsTunnelManager

    @Inject
    lateinit var serverConfig: ServerConfig

    @OnOpen
    suspend fun onOpenEndpoint(connection: WebSocketConnection, @PathParam domain: String) {
        val handshake = connection.handshakeRequest()
        val wsId = handshake.header("X-Ws-Id") ?: "${domain}-${UUID.randomUUID()}"
        connection.userData().put(wsIdKey, wsId)
        connection.userData().put(domainKey, domain)

        // Extract request information
        val path = handshake.path()
        val query = emptyMap<String, String>() // handshake.queryParamNames() not available
        val headers = emptyMap<String, String>() // handshake.headerNames() not available
        val subprotocols = emptyList<String>()

        // Create tunnel in CONNECTING state
        wsTunnelManager.openTunnel(wsId, "external", domain)
        socketService.registerExternalConnection(wsId, connection)

        val upgradeEnvelope = Envelope(
            correlationId = wsId,
            payload = WsUpgrade(WsUpgrade.WsUpgradePayload(
                wsId = wsId,
                path = path,
                query = query,
                headers = headers,
                subprotocols = subprotocols
            ))
        )

        try {
            val response = socketService.sendUpgrade(upgradeEnvelope, domain)
            if (response.accepted) {
                // Tunnel is now established
                val tunnel = wsTunnelManager.getTunnel(wsId)
                tunnel?.establish()
            } else {
                connection.close(
                    CloseReason(response.statusCode, "Upgrade rejected by local app")
                ).awaitSuspending()
            }
        } catch (e: Exception) {
            connection.close(CloseReason(1011, "Internal Error")).awaitSuspending()
        }
    }

    @OnTextMessage
    suspend fun onTextMessageEndpoint(message: String, connection: WebSocketConnection) {
        val wsId = connection.userData().get(wsIdKey)
            ?: throw IllegalStateException("No wsId in connection data")

        val tunnel = wsTunnelManager.getTunnel(wsId)
            ?: throw IllegalStateException("No tunnel found for wsId: $wsId")

        val wsMessage = Envelope(
            correlationId = wsId,
            payload = WsMessage(WsMessage.WsMessagePayload(
                wsId = wsId,
                type = WsMessage.WsMessagePayload.FrameType.TEXT,
                data = message.toByteArray()
            ))
        )

        // Send to local app via SocketService
        val domain = connection.userData().get(domainKey)!!
        socketService.sendWsMessage(wsMessage, domain)
    }

    @OnBinaryMessage
    suspend fun onBinaryMessageEndpoint(data: Buffer, connection: WebSocketConnection) {
        val wsId = connection.userData().get(wsIdKey)
            ?: throw IllegalStateException("No wsId in connection data")

        val tunnel = wsTunnelManager.getTunnel(wsId)
            ?: throw IllegalStateException("No tunnel found for wsId: $wsId")

        val wsMessage = Envelope(
            correlationId = wsId,
            payload = WsMessage(WsMessage.WsMessagePayload(
                wsId = wsId,
                type = WsMessage.WsMessagePayload.FrameType.BINARY,
                data = data.bytes
            ))
        )

        // Send to local app via SocketService
        val domain = connection.userData().get(domainKey)!!
        socketService.sendWsMessage(wsMessage, domain)
    }

    @OnClose
    fun onCloseEndpoint(connection: WebSocketConnection) {
        val wsId = connection.userData().get(wsIdKey) ?: return

        // Send WsClose to local app
        wsTunnelManager.closeTunnel(wsId, 1000, "Client closed")
        socketService.unregisterExternalConnection(wsId)
    }
}

@ApplicationScoped
class WsMessageCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }
}
