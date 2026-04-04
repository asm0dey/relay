package site.asm0dey.relay.server

import com.google.protobuf.ByteString
import io.grpc.Status.UNAUTHENTICATED
import io.quarkus.grpc.GrpcService
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.ClientMessage.PayloadCase.*
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

sealed class RequestResult {
    data class Metadata(val response: HttpResponse) : RequestResult()
    data class Chunk(val chunk: BodyChunk) : RequestResult()
    data object Done : RequestResult()
}

@GrpcService
@Singleton
class TunnelService : MutinyTunnelServiceGrpc.TunnelServiceImplBase() {
    private val log = LoggerFactory.getLogger(TunnelService::class.java)

    @ConfigProperty(name = "relay.allowed-secret-keys")
    lateinit var allowedSecretKeys: List<String>

    @ConfigProperty(name = "relay.domain")
    lateinit var tld: String

    @Inject
    lateinit var wsTunnelManager: WsTunnelManager

    @PostConstruct
    fun init() {
        println("Allowed secret keys: $allowedSecretKeys")
    }

    fun hasClient(domain: String): Boolean = clients.containsKey(domain)

    fun getClientQueue(domain: String): BlockingQueue<ServerMessage>? = clients[domain]

    override fun ping(request: PingMessage?): Uni<PongMessage?>? {
        return Uni.createFrom().item(pongMessage { })
    }


    override fun openTunnel(request: Multi<ClientMessage>): Multi<ServerMessage> {
        val outgoingQueue = ArrayBlockingQueue<ServerMessage>(100)

        return Multi.createFrom().emitter { emitter ->
            request.subscribe().with(
                { clientMessage ->
                    val correlationId = clientMessage.correlationId
                    when (clientMessage.payloadCase) {
                        REGISTER -> {
                            if (!tryAuthenticate(clientMessage, correlationId, outgoingQueue))
                                emitter.fail(
                                    UNAUTHENTICATED
                                        .withDescription("Invalid auth token")
                                        .asException()
                                )
                        }

                        HTTP_RESPONSE -> pendingRequests[correlationId]?.put(RequestResult.Metadata(clientMessage.httpResponse))
                        CHUNK -> {
                            pendingRequests[correlationId]?.put(RequestResult.Chunk(clientMessage.chunk))
                            if (clientMessage.chunk.last) {
                                val remove = pendingRequests.remove(correlationId)
                                remove?.add(RequestResult.Done)
                            }
                        }

                        WS_UPGRADE -> {
                            val response = clientMessage.wsUpgrade
                            log.debug("WS_UPGRADE received: connectionId={}, accepted={}", response.connectionId, response.accepted)
                            wsTunnelManager.completeResponse(response.connectionId, response)
                        }

                        WS_FRAME -> {
                            val frame = clientMessage.wsFrame
                            val conn = wsTunnelManager.getExternalConnection(frame.connectionId)
                            if (conn != null) {
                                if (frame.isBinary) {
                                    conn.sendBinary(io.vertx.core.buffer.Buffer.buffer(frame.data.toByteArray()))
                                        .subscribe().with({}, { err -> log.error("Error sending binary frame", err) })
                                } else {
                                    conn.sendText(frame.data.toStringUtf8())
                                        .subscribe().with({}, { err -> log.error("Error sending text frame", err) })
                                }
                            } else {
                                log.trace("WS_FRAME for unknown connectionId={}, silently dropped", frame.connectionId)
                            }
                        }

                        WS_CLOSE -> {
                            val close = clientMessage.wsClose
                            log.debug("WS_CLOSE received: connectionId={}, code={}", close.connectionId, close.code)
                            val conn = wsTunnelManager.getExternalConnection(close.connectionId)
                            if (conn != null) {
                                conn.close(io.quarkus.websockets.next.CloseReason(close.code, close.reason))
                                    .subscribe().with({}, { err -> log.error("Error closing external connection", err) })
                            }
                            wsTunnelManager.closeTunnel(close.connectionId, close.code, close.reason)
                        }

                        PAYLOAD_NOT_SET -> { /* ignore */ }
                    }
                },
                { error ->
                    // Client agent error — cleanup WS tunnels for this domain
                    val domain = queueToDomain.remove(outgoingQueue)
                    if (domain != null) {
                        clients.remove(domain)
                        wsTunnelManager.cleanupForDomain(domain)
                    }
                    emitter.fail(error)
                },
                {
                    // Client agent disconnected — cleanup WS tunnels for this domain
                    val domain = queueToDomain.remove(outgoingQueue)
                    if (domain != null) {
                        clients.remove(domain)
                        wsTunnelManager.cleanupForDomain(domain)
                    }
                    emitter.complete()
                }
            )

            Thread.ofVirtual().start {
                try {
                    while (!emitter.isCancelled) {
                        emitter.emit(outgoingQueue.take())
                    }
                } catch (_: InterruptedException) {
                    emitter.complete()
                }
            }
        }
    }

    private fun tryAuthenticate(
        message: ClientMessage,
        correlationId: String,
        outgoingQueue: BlockingQueue<ServerMessage>
    ): Boolean {
        val authToken = message.register.authToken
        val isAuthTokenValid = allowedSecretKeys.isEmpty() || authToken in allowedSecretKeys
        val ackMsg = serverMessage {
            this.correlationId = correlationId
            this.ack = registerAck {
                if (!isAuthTokenValid) {
                    success = false
                    error = "Invalid auth token"
                } else {
                    val domain = message.register.desiredSubdomain ?: Random.nextInt().toString()
                    clients[domain] = outgoingQueue
                    queueToDomain[outgoingQueue] = domain
                    success = true
                    assignedSubdomain = domain
                    publicUrl = "$domain.${tld}"
                }
            }
        }
        outgoingQueue.put(ackMsg)
        return isAuthTokenValid
    }

    private val clients = ConcurrentHashMap<String, BlockingQueue<ServerMessage>>()
    private val pendingRequests = ConcurrentHashMap<String, BlockingQueue<RequestResult>>()
    // Track domain for each outgoing queue to enable cleanup on gRPC disconnect
    private val queueToDomain = ConcurrentHashMap<BlockingQueue<ServerMessage>, String>()

    fun startRequest(domain: String, message: ServerMessage, body: ByteArray? = null): Pair<HttpResponse, InputStream> {
        val correlationId = message.correlationId
        if (correlationId.isEmpty()) error("Correlation ID must be set")
        val queue = ArrayBlockingQueue<RequestResult>(50)
        pendingRequests[correlationId] = queue
        try {
            val clientQueue = clients[domain] ?: error("Client $domain not connected")
            clientQueue.put(message)
            if (body != null && body.isNotEmpty()) {
                val chunkSize = 4 * 1024 * 1024
                var offset = 0
                while (offset < body.size) {
                    val end = minOf(offset + chunkSize, body.size)
                    clientQueue.put(serverMessage {
                        this.correlationId = correlationId
                        chunk = bodyChunk {
                            data = ByteString.copyFrom(body, offset, end - offset)
                            last = false
                        }
                    })
                    offset = end
                }
                clientQueue.put(serverMessage {
                    this.correlationId = correlationId
                    chunk = bodyChunk {
                        data = ByteString.EMPTY
                        last = true
                    }
                })
            }
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