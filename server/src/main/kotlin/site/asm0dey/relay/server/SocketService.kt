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


@Singleton
@WebSocket(path = "/ws/{secret}")
class SocketService() {
    private val domainString = UserData.TypedKey.forString("domain")
    private val pendingRequests = ConcurrentHashMap<CorrelationID, CompletableDeferred<Envelope>>()
    @Inject
    private lateinit var connections: OpenConnections

    @OnOpen
    suspend fun onConnect(connection: WebSocketConnection, @PathParam secret: String) {
        if (!("Secret".equals(secret, ignoreCase = true))) {
            withTimeout(5000) {
                connection.close(CloseReason(1008, "Invalid secret key")).awaitSuspending()
            }
            return
        }
        val customDomain: String = connection.handshakeRequest().header("domain") ?: "xxxx"
        connection.userData().put(domainString, customDomain)
    }

    @OnBinaryMessage
    suspend fun onMessage(envelope: Envelope) {
        val deferred = pendingRequests.remove(CorrelationID(envelope.correlationId))
        if (deferred != null) {
            if (envelope.payload !is Response) throw IllegalStateException("Expected response message, got $envelope")
            deferred.complete(envelope)
        } else {
            when (envelope.payload) {
                is Control -> TODO()
                is Error -> TODO()
                is Request -> throw IllegalStateException("Server is not expected to receiver requests, but got $envelope")
                is Response -> throw IllegalStateException("Response $envelope is not expected to be received here")
            }
        }

    }

    suspend fun request(envelope: Envelope, host: String) {
        val deferred = CompletableDeferred<Envelope>()
        pendingRequests[CorrelationID(envelope.correlationId)] = deferred
        val connection = connections.first { it.userData().get(domainString) == host }
        withTimeout(5000) {
            connection.sendBinary(envelope.toByteArray()).awaitSuspending()
        }
        withTimeout(10000) {
            deferred.await()
        }
    }


    fun onStop(@Observes ev: ShutdownEvent?) {
        val message = Envelope(
            correlationId = UUID.randomUUID().toString(),
            payload = Control(Control.Payload(Control.Payload.ControlAction.SHUTDOWN))
        ).toByteArray()

        connections
            .map {
                it.sendBinary(message) to it.close()
            }
            .forEach { (a,b) ->
                a.await().atMost(Duration.ofSeconds(5))
                b.await().atMost(Duration.ofSeconds(5))
            }
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Server is shutting down")) }
    }

    private fun parseQueryString(connection: WebSocketConnection): Map<String, String> {
        val query = connection.handshakeRequest().query() ?: return emptyMap()
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

}

private fun UUID.toCorrelationId(): CorrelationID = CorrelationID(toString())


@JvmInline
value class CorrelationID(val id: String)

@Singleton
class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}