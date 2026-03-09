package site.asm0dey.relay.server

import io.quarkus.grpc.GrpcService
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.ClientMessage.PayloadCase.*
import site.asm0dey.relay.domain.TunnelService
import java.io.InputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

sealed class RequestResult {
    data class Metadata(val response: HttpResponse) : RequestResult()
    data class Chunk(val chunk: BodyChunk) : RequestResult()
    data object Done : RequestResult()
}

@GrpcService
@Singleton
class TunnelService(
) : TunnelService {
    @ConfigProperty(name = "relay.allowed-secret-keys")
    lateinit var allowedSecretKeys: List<String>

    @ConfigProperty(name = "relay.domain")
    lateinit var tld: String

    @PostConstruct
    fun init() {
        println("Allowed secret keys: $allowedSecretKeys")
    }

    override fun ping(request: PingMessage?): Uni<PongMessage?>? {
        return Uni.createFrom().item(pongMessage { })
    }


    override fun openTunnel(request: Multi<ClientMessage>): Multi<ServerMessage> {
        val outgoingQueue = LinkedBlockingQueue<ServerMessage>()

        return Multi.createFrom().emitter { emitter ->
            // Subscribe to incoming client messages
            request.subscribe().with(
                { clientMessage ->
                    val correlationId = clientMessage.correlationId
                    when (clientMessage.payloadCase) {
                        REGISTER -> {
                            clientMessage.auth(correlationId, outgoingQueue)
                        }

                        HTTP_RESPONSE -> pendingRequests[correlationId]?.put(RequestResult.Metadata(clientMessage.httpResponse))
                        CHUNK -> {
                            pendingRequests[correlationId]?.put(RequestResult.Chunk(clientMessage.chunk))
                            if (clientMessage.chunk.last) {
                                val remove = pendingRequests.remove(correlationId)
                                remove?.add(RequestResult.Done)
                            }
                        }

                        WS_CLOSE, PAYLOAD_NOT_SET, WS_UPGRADE, WS_FRAME -> { /* TODO */
                        }
                    }
                },
                { error -> emitter.fail(error) },   // propagate upstream errors
                { emitter.complete() }              // client disconnected → end outbound stream
            )

            // Drain the queue on a background thread
            thread {
                try {
                    while (!emitter.isCancelled) {
                        val message = outgoingQueue.take()
                        emitter.emit(message)
                    }
                } catch (_: InterruptedException) {
                    emitter.complete()
                }
            }
        }
    }

    private fun ClientMessage.auth(
        correlationId: String,
        outgoingQueue: LinkedBlockingQueue<ServerMessage>
    ) {
        val authToken = this.register.authToken
        val ack = serverMessage {
            this.correlationId = correlationId
            this.ack = registerAck {
                if (authToken == null || authToken !in allowedSecretKeys) {
                    success = false
                    error = "Invalid auth token"
                } else {
                    val domain = this@auth.register.desiredSubdomain ?: Random.nextInt().toString()
                    clients[domain] = outgoingQueue
                    success = true
                    assignedSubdomain = domain
                    publicUrl = "$domain.${tld}"
                }
            }
        }
        outgoingQueue.put(ack)
    }

    private val clients = ConcurrentHashMap<String, BlockingQueue<ServerMessage>>()
    private val pendingRequests = ConcurrentHashMap<String, BlockingQueue<RequestResult>>()
    /*
        override fun openTunnel(requests: Flow<ClientMessage>): Flow<ServerMessage> = channelFlow {
            requests.flowOn(context).collect { clientMessage ->
                val correlationId = clientMessage.correlationId
                when (clientMessage.payloadCase) {
                    REGISTER -> auth(clientMessage.register, correlationId)
                    HTTP_RESPONSE -> pendingRequests[correlationId]?.send(RequestResult.Metadata(clientMessage.httpResponse))
                    CHUNK -> {
                        pendingRequests[correlationId]?.send(RequestResult.Chunk(clientMessage.chunk))
                        if (clientMessage.chunk.last) {
                            pendingRequests.remove(correlationId)?.close()
                        }
                    }

                    WS_UPGRADE -> {
                        // pendingRequests[clientMessage.correlationId] = CompletableDeferred()
                    }

                    WS_FRAME -> {
                        val data = clientMessage.wsFrame.data.toByteArray()
                        val connectionId = clientMessage.wsFrame.connectionId
                        val isBinary = clientMessage.wsFrame.isBinary
                    }

                    WS_CLOSE -> TODO()
                    PAYLOAD_NOT_SET -> TODO()
                }
            }
        }
    */

    fun startRequest(domain: String, message: ServerMessage): Pair<HttpResponse, InputStream> {
        val correlationId = message.correlationId
        if (correlationId.isEmpty()) error("Correlation ID must be set")
        val queue = LinkedBlockingQueue<RequestResult>()
        pendingRequests[correlationId] = queue
        try {
            val clientQueue = clients[domain] ?: error("Client $domain not connected")
            clientQueue.put(message)
            val firstResult = queue.poll(30, TimeUnit.SECONDS)
            if (firstResult !is RequestResult.Metadata) {
                pendingRequests.remove(correlationId)
                error("Expected metadata first, got $firstResult")
            }
            return firstResult.response to ChunkInputStream(queue)
        } catch (e: Exception) {
            pendingRequests.remove(correlationId)
            throw e
        }
    }
}