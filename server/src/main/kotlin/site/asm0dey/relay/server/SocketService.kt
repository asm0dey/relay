@file:OptIn(ExperimentalTime::class)

package site.asm0dey.relay.server

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.websockets.next.*
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.WsMessage.WsMessagePayload.FrameType
import java.lang.reflect.Type
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime


@WebSocket(path = "/ws/{secret}")
class SocketService() {
    private val domainString = UserData.TypedKey.forString("domain")
    private val pendingRequests = ConcurrentHashMap<CorrelationID, CompletableDeferred<Envelope>>()

    @Inject
    private lateinit var connections: OpenConnections

    @Inject
    lateinit var serverConfig: ServerConfig

    @Inject
    lateinit var streamManager: StreamManager

    private val registeredDomains = ConcurrentHashMap<String, String>() // connectionId -> domain

    @OnOpen
    suspend fun onConnectUnique(connection: WebSocketConnection, @PathParam secret: String) {
        if (secret !in serverConfig.allowedSecretKeys) {
            connection.close(CloseReason(1008, "Invalid secret key")).awaitSuspending()
            return
        }
        val requestedSubdomain = connection.handshakeRequest().header("domain")
        val subdomain = if (requestedSubdomain.isNullOrEmpty()) generateRandomSubdomain() else requestedSubdomain
        connection.userData().put(domainString, subdomain)
        registeredDomains[connection.id()] = subdomain
    }

    @Suppress("unused")
    @OnClose
    fun onClose(connection: WebSocketConnection) {
        val clientId = connection.id()
        registeredDomains.remove(clientId)
        streamManager.cleanupForConnection(clientId)
        pendingRequests
            .filter { it.key.id.startsWith(connection.userData().get(domainString)) }
            .forEach { it.value.completeExceptionally(IllegalStateException("Client disconnected")) }
    }

    private fun generateRandomSubdomain(): String {
        val chars = ('a'..'z') + ('0'..'9')
        return (1..5)
            .map { chars.random() }
            .joinToString("")
    }

    @Inject
    lateinit var wsTunnelManager: WsTunnelManager

    @OnBinaryMessage
    suspend fun onMessage(connection: WebSocketConnection, envelope: Envelope) {
        val controlPayload = envelope.payload as? Control
        if (controlPayload != null) {
            when (controlPayload.value.action) {
                Control.ControlPayload.ControlAction.REGISTER -> {
                    val subdomain = connection.userData().get(domainString) ?: generateRandomSubdomain()
                    registeredDomains[connection.id()] = subdomain
                    val fullDomain = "$subdomain.${serverConfig.domain}"
                    val response = Envelope(
                        correlationId = envelope.correlationId,
                        payload = Control(
                            Control.ControlPayload(
                                Control.ControlPayload.ControlAction.REGISTERED,
                                subdomain = subdomain,
                                publicUrl = fullDomain
                            )
                        )
                    )
                    connection.sendBinary(response.toByteArray()).awaitSuspending()
                }

                Control.ControlPayload.ControlAction.HEARTBEAT -> {
                    val response = Envelope(
                        correlationId = envelope.correlationId,
                        payload = Control(Control.ControlPayload(Control.ControlPayload.ControlAction.STATUS))
                    )
                    connection.sendBinary(response.toByteArray()).awaitSuspending()
                }

                Control.ControlPayload.ControlAction.UNREGISTER -> {
                    connection.close().awaitSuspending()
                }

                else -> {}
            }
        } else {
            val deferred = pendingRequests[CorrelationID(envelope.correlationId)]
            if (deferred != null && (envelope.payload is Response || envelope.payload is StreamInit)) {
                pendingRequests.remove(CorrelationID(envelope.correlationId))
                deferred.complete(envelope)
            } else {
                when (val payload = envelope.payload) {
                    is Error -> {
                        System.err.println("Error from client: ${payload.value.code} - ${payload.value.message}")
                    }
                    is StreamInit -> {
                        val init = payload.value
                        val clientId = connection.id()
                        streamManager.initiateStream(init.correlationId, clientId, init)
                    }
                    is StreamChunk -> {
                        val chunk = payload.value
                        val result = streamManager.receiveChunk(chunk.correlationId, chunk)
                        if (result.isSuccess) {
                            // Send ACK
                            sendAck(connection, chunk.correlationId, chunk.chunkIndex)
                            // Forward chunk to HTTP client if a consumer is registered
                            result.getOrNull()?.chunkConsumer?.invoke(chunk.data)
                        }
                    }
                    is StreamAck -> {
                        val ack = payload.value
                        streamManager.getUpload(ack.correlationId)?.onAck(ack)
                    }
                    is StreamError -> {
                        val error = payload.value
                        val context = streamManager.getStream(error.correlationId)
                        context?.errorConsumer?.invoke(IllegalStateException("Stream error from client: ${error.message}"))
                        streamManager.cleanup(error.correlationId)
                    }
                    is Request -> throw IllegalStateException("Server is not expected to receiver requests, but got $envelope")
                    is Response -> throw IllegalStateException("Response $envelope is not expected to be received here")
                    is WsUpgrade -> {
                        val upgrade = payload.value
                        // Store pending upgrade request for response handling
                        pendingRequests[CorrelationID(envelope.correlationId)] = CompletableDeferred()
                        // Forwarding to local app is handled via the request/response mechanism
                    }

                    is WsUpgradeResponse -> {
                        val response = payload.value
                        // Complete the pending request if any
                        val deferred = pendingRequests.remove(CorrelationID(envelope.correlationId))
                        deferred?.complete(envelope)
                    }

                    is WsMessage -> {
                        val message = payload.value
                        // Forward to external client
                        sendWsMessageToExternal(message.wsId, message)
                    }

                    is WsClose -> {
                        val close = payload.value
                        // Close the tunnel and notify external client
                        wsTunnelManager.closeTunnel(close.wsId, close.code, close.reason)
                        closeExternalConnection(close.wsId, close.code, close.reason)
                    }

                    is Control -> {}
                }
            }
        }
    }

    private val externalConnections = ConcurrentHashMap<String, WebSocketConnection>()

    fun registerExternalConnection(wsId: String, connection: WebSocketConnection) {
        externalConnections[wsId] = connection
    }

    fun unregisterExternalConnection(wsId: String) {
        externalConnections.remove(wsId)
    }

    suspend fun sendUpgrade(upgrade: Envelope, host: String): WsUpgradeResponse.WsUpgradeResponsePayload {
        val deferred = CompletableDeferred<Envelope>()
        val correlationId = CorrelationID(upgrade.correlationId)
        pendingRequests[correlationId] = deferred
        val connection = getConnectionForHost(host)

        withTimeout(5000) {
            connection.sendBinary(upgrade.toByteArray()).awaitSuspending()
        }

        val response = waitForResponse(upgrade.correlationId)
        val wsResponse = (response.payload as? WsUpgradeResponse)?.value
            ?: throw IllegalStateException("Expected WsUpgradeResponse, got ${response.payload}")

        return wsResponse
    }

    suspend fun sendWsMessage(message: Envelope, host: String) {
        val connection = getConnectionForHost(host)
        withTimeout(5000) {
            connection.sendBinary(message.toByteArray()).awaitSuspending()
        }
    }

    suspend fun sendWsMessageToExternal(wsId: String, message: WsMessage.WsMessagePayload) {
        val connection = externalConnections[wsId]
        if (connection != null) {
            if (message.type == FrameType.TEXT) {
                connection.sendText(String(message.data)).awaitSuspending()
            } else {
                connection.sendBinary(message.data).awaitSuspending()
            }
        }
    }

    suspend fun closeExternalConnection(wsId: String, code: Int, reason: String) {
        val connection = externalConnections.remove(wsId)
        connection?.close(CloseReason(code, reason))?.awaitSuspending()
    }

    suspend fun request(envelope: Envelope, host: String): Envelope {
        val deferred = CompletableDeferred<Envelope>()
        val correlationId = CorrelationID(envelope.correlationId)
        pendingRequests[correlationId] = deferred
        val connection = getConnectionForHost(host)
        withTimeout(5000) {
            connection.sendBinary(envelope.toByteArray()).awaitSuspending()
        }
        return waitForResponse(envelope.correlationId)
    }

    suspend fun waitForResponse(correlationId: String): Envelope {
        val deferred = pendingRequests.getOrPut(CorrelationID(correlationId)) { CompletableDeferred() }
        return withTimeout(10000) {
            deferred.await()
        }
    }

    fun getConnectionForHost(host: String): WebSocketConnection =
        connections.first { registeredDomains[it.id()] == host }

    suspend fun sendAck(connection: WebSocketConnection, correlationId: String, chunkIndex: Long) {
        val ack = Envelope(
            correlationId = correlationId,
            payload = StreamAck(StreamAck.StreamAckPayload(correlationId, chunkIndex))
        )
        withTimeout(5000) {
            connection.sendBinary(ack.toByteArray()).awaitSuspending()
        }
    }


    fun onStop(@Observes ev: ShutdownEvent?) {
        val message = Envelope(
            correlationId = UUID.randomUUID().toString(),
            payload = Control(Control.ControlPayload(Control.ControlPayload.ControlAction.SHUTDOWN))
        ).toByteArray()

        connections
            .map {
                it.sendBinary(message) to it.close()
            }
            .forEach { (a, b) ->
                a.await().atMost(Duration.ofSeconds(5))
                b.await().atMost(Duration.ofSeconds(5))
            }
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Server is shutting down")) }
        streamManager.cleanupAll()
    }

}


@JvmInline
value class CorrelationID(val id: String)

class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}