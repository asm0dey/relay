@file:OptIn(ExperimentalTime::class)

package site.asm0dey.relay.server

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.websockets.next.*
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import site.asm0dey.relay.domain.*
import java.lang.reflect.Type
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime


@Singleton
@WebSocket(path = "/ws/{secret}")
class SocketService() {
    private val domainString = UserData.TypedKey.forString("domain")
    private val pendingRequests = ConcurrentHashMap<CorrelationID, CompletableDeferred<Envelope>>()

    @Suppress("CdiInjectionPointsInspection")
    @Inject
    private lateinit var connections: OpenConnections

    @Inject
    lateinit var serverConfig: ServerConfig

    @Inject
    lateinit var streamManager: StreamManager

    @OnOpen
    suspend fun onConnect(connection: WebSocketConnection, @PathParam secret: String) {
        if (secret !in serverConfig.allowedSecretKeys) {
            withTimeout(5000) {
                connection.close(CloseReason(1008, "Invalid secret key")).awaitSuspending()
            }
            return
        }
        val requestedSubdomain = connection.handshakeRequest().header("domain")
        val subdomain = if (requestedSubdomain.isNullOrEmpty()) generateRandomSubdomain() else requestedSubdomain
        connection.userData().put(domainString, subdomain)
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        val clientId = connection.id()
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

    @OnBinaryMessage
    suspend fun onMessage(connection: WebSocketConnection, envelope: Envelope) {
        val controlPayload = envelope.payload as? Control
        if (controlPayload != null) {
            when (controlPayload.value.action) {
                Control.ControlPayload.ControlAction.REGISTER -> {
                    val subdomain = connection.userData().get(domainString) ?: generateRandomSubdomain()
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
            val deferred = pendingRequests.remove(CorrelationID(envelope.correlationId))
            if (deferred != null) {
                if (envelope.payload !is Response) throw IllegalStateException("Expected response message, got $envelope")
                deferred.complete(envelope)
            } else {
                when (val payload = envelope.payload) {
                    is Control -> {}
                    is Error -> TODO()
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
                            // TODO: Forward chunk to HTTP client (will be done in HTTP integration phase)
                        }
                    }
                    is StreamAck -> {
                        // Server doesn't need to process ACKs (client sends ACKs)
                        // This is here for completeness - ACKs are client->server
                    }
                    is StreamError -> {
                        val error = payload.value
                        streamManager.cleanup(error.correlationId)
                        // TODO: Forward error to HTTP client (will be done in HTTP integration phase)
                    }
                    is Request -> throw IllegalStateException("Server is not expected to receiver requests, but got $envelope")
                    is Response -> throw IllegalStateException("Response $envelope is not expected to be received here")
                }
            }
        }
    }

    suspend fun request(envelope: Envelope, host: String): Envelope {
        val deferred = CompletableDeferred<Envelope>()
        val correlationId = CorrelationID(envelope.correlationId)
        pendingRequests[correlationId] = deferred
        val connection = connections.first { it.userData().get(domainString) == host }
        withTimeout(5000) {
            connection.sendBinary(envelope.toByteArray()).awaitSuspending()
        }
        return withTimeout(10000) {
            deferred.await()
        }
    }

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

@Singleton
class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}