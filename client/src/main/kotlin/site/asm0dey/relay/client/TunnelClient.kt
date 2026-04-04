package site.asm0dey.relay.client

import com.github.ajalt.mordant.rendering.BorderType.Companion.SQUARE
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.italic
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.google.protobuf.ByteString.copyFrom
import io.grpc.ManagedChannelBuilder
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.Cancellable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Provider
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.*
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.AbstractHttpEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.HTTP
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import picocli.CommandLine
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.ServerMessage.PayloadCase.*
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@ApplicationScoped
class TunnelClient(
    val parsedResult: CommandLine.ParseResult,
    @param:ConfigProperty(name = "quarkus.grpc.server.max-inbound-message-size") var maxInboundMessageSizeProvider: Provider<Optional<Int>>,
) {
    @Suppress("PrivatePropertyName")
    private val LOG = LoggerFactory.getLogger(TunnelClient::class.java)

    private val relay by lazy {
        val name = parsedResult.matchedOption("remote-host").getValue<String>()
        val port = parsedResult.matchedOption("remote-port").getValue<Int>()
        val channel = ManagedChannelBuilder
            .forAddress(name, port)
            .let { if (parsedResult.matchedOption("insecure").getValue<Boolean>() == true) it.usePlaintext() else it }
            .build()
        MutinyTunnelServiceGrpc.newMutinyStub(channel)
    }


    val t = Terminal()

    // Buffered channel to queue outgoing messages without blocking senders
//    private val outgoing = Channel<ClientMessage>(Channel.BUFFERED)
    private val outgoingQueue = LinkedBlockingQueue<ClientMessage>()

    // ConcurrentHashMap for thread-safe access to request queues from multiple coroutines
    private val incomingRequests = ConcurrentHashMap<String, LinkedBlockingQueue<ByteArray>>()
    private val maxMessageSize: Int by lazy { maxInboundMessageSizeProvider.get().orElse(4 * 1024 * 1024) }

    // CountDownLatch and AtomicBoolean to signal when registration is complete
    private val registrationLatch = CountDownLatch(1)
    private val registrationSuccess = AtomicBoolean(false)
    private val httpClient = HttpClients.createDefault()
    private lateinit var subscription: Cancellable
    private val isStarted = AtomicBoolean(false)

    @OptIn(ExperimentalUuidApi::class)
    fun start(localPort: Int = parsedResult.matchedPositional(0).getValue()) {
        if (!isStarted.compareAndSet(false, true)) {
            LOG.debug("TunnelClient already started, skipping")
            return
        }
        LOG.debug("Launching tunnel opening job")
        val pong = relay.ping(pingMessage { }).await().atMost(java.time.Duration.of(30, SECONDS))
        val streams = Multi.createBy().concatenating().streams(
            Multi.createFrom().item(clientMessage {
                correlationId = Uuid.random().toHexDashString()
                this.register = registerRequest {
                    authToken = parsedResult.matchedOption("secret").getValue()
                    val domain = parsedResult.matchedOption("domain").getValue<String>()
                    if (domain != null) desiredSubdomain = domain
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
                        ACK -> processRegistrationResponse(serverMessage)
                        HTTP_REQUEST -> processHttpRequest(serverMessage, localPort)
                        CHUNK -> processChunk(serverMessage)
                        WS_OPEN -> TODO()
                        WS_UPGRADE -> TODO()
                        WS_FRAME -> TODO()
                        WS_CLOSE -> TODO()
                        PAYLOAD_NOT_SET -> TODO()
                    }
                },
                { error ->
                    LOG.error("Error in tunnel stream", error)
                    registrationSuccess.set(false)
                    registrationLatch.countDown()
                }
            )

        Runtime.getRuntime().addShutdownHook(thread(false) {
            LOG.debug("Shutdown hook triggered, cancelling tunnel subscription")
            subscription.cancel()
            httpClient.close()
        })

        registrationLatch.await()
        if (!registrationSuccess.get()) {
            subscription.cancel()
        }

    }

    private fun processChunk(serverMessage: ServerMessage) {
        val chunk = serverMessage.chunk
        LOG.debug("Processing chunk for request ${serverMessage.correlationId}, last=${chunk.last}")
        incomingRequests[serverMessage.correlationId]?.offer(chunk.data.toByteArray())
        if (chunk.last) incomingRequests.remove(serverMessage.correlationId)
    }

    private fun processHttpRequest(serverMessage: ServerMessage, localPort: Int) {
        LOG.debug("Processing HTTP request: ${serverMessage.httpRequest.method} ${serverMessage.httpRequest.path}, correlationId=${serverMessage.correlationId}, hasBody=${serverMessage.httpRequest.hasBody}")
        // Buffered queue to decouple chunk producer from HTTP request consumer
        val bodyQueue = LinkedBlockingQueue<ByteArray>()
        incomingRequests[serverMessage.correlationId] = bodyQueue
        if (!serverMessage.httpRequest.hasBody) {
            incomingRequests.remove(serverMessage.correlationId)
        }
        // Launch a new coroutine to handle each HTTP request concurrently
        serverMessage.handleHttpRequest(bodyQueue, 1.minutes, localPort)
    }

    private fun processRegistrationResponse(serverMessage: ServerMessage) {
        if (serverMessage.ack.success) {
            t.println(yellow("Tunnel established, you can now start sending requests to the server"))
            t.println("URL: ${(black on red)(serverMessage.ack.publicUrl)}")
            t.println(yellow("To close the tunnel, press Ctrl+C"))
            registrationSuccess.set(true)
            registrationLatch.countDown()
        } else {
            LOG.debug("Registration failed: ${serverMessage.ack.error}")
            println("Failed to connect: ${serverMessage.ack.error}")
            registrationSuccess.set(false)
            registrationLatch.countDown()
            exitProcess(1)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun ServerMessage.handleHttpRequest(
        bodyQueue: LinkedBlockingQueue<ByteArray>,
        timeout: Duration,
        localPort: Int
    ) {
        LOG.debug("Handling HTTP request: ${httpRequest.method} ${httpRequest.path}, correlationId=$correlationId")
        // Try to get the first chunk immediately to determine if the request has a body
        val firstChunk = bodyQueue.poll()

        val localHost = parsedResult.matchedOption("local-host").getValue<String>()

        printRequestInfo(localHost, localPort)
        val request = buildHttpRequest(localHost, localPort, firstChunk, bodyQueue, timeout)

        thread {
            try {
                httpClient.execute(request) { processHttpResponse(it) }
            } catch (e: Exception) {
                LOG.error("Error executing local request", e)
                outgoingQueue.add(clientMessage {
                    correlationId = this@handleHttpRequest.correlationId
                    httpResponse = httpResponse {
                        hasBody = false
                        status = 502
                        statusMessage = "Bad Gateway: ${e.message}"
                    }
                })
            }
        }
    }

    private fun ServerMessage.processHttpResponse(resp: HttpResponse): Unit? {
        val content = resp.entity?.content
        t.println(
            "${(green)("▶ RES")} " +
                    "${(white)(resp.statusLine.statusCode.toString())} " +
                    "${(green)(resp.statusLine.reasonPhrase)}, " +
                    italic((cyan)("Request ID: ${correlationId}"))
        )
        outgoingQueue.add(clientMessage {
            correlationId = this@processHttpResponse.correlationId
            httpResponse = httpResponse {
                this.hasBody = content != null
                headers.putAll(resp.allHeaders.associate { x -> x.name to x.value })
                status = resp.statusLine.statusCode
                statusMessage = resp.statusLine.reasonPhrase
            }
        })

        return content?.use {
            val bar = ByteArray(maxMessageSize)
            while (true) {
                val read = it.read(bar)
                val noMoreBytes = read == -1
                outgoingQueue.add(clientMessage {
                    correlationId = this@processHttpResponse.correlationId
                    chunk = bodyChunk {
                        if (noMoreBytes) {
                            data = copyFrom(ByteArray(0))
                            last = true
                        } else if (read < bar.size) {
                            data = copyFrom(ByteBuffer.wrap(bar, 0, read))
                            last = false
                        } else {
                            data = copyFrom(bar)
                            last = false
                        }
                    }
                })
                if (noMoreBytes) break
            }
        }
    }

    private fun ServerMessage.buildHttpRequest(
        localHost: String?,
        localPort: Int,
        firstChunk: ByteArray?,
        bodyQueue: LinkedBlockingQueue<ByteArray>,
        timeout: Duration
    ): HttpRequestBase {
        val uri = run {
            val uRIBuilder = URIBuilder()
                .setScheme("http")
                .setHost(localHost)
                .setPort(localPort)
                .setPath(httpRequest.path)

            httpRequest.queryMap.forEach { (k, v) -> uRIBuilder.addParameter(k, v) }
            uRIBuilder.build()
        }
        val request = buildRequest(uri)
        val skipHeaders = setOf("content-length", "transfer-encoding")
        httpRequest.headersMap.forEach { (k, v) ->
            if (k.lowercase() !in skipHeaders) request.addHeader(k, v)
        }
        if (request is HttpEntityEnclosingRequestBase) {
            request.entity = buildStreamingEntity(
                firstChunk,
                bodyQueue,
                incomingRequests,
                httpRequest,
                timeout,
                correlationId,
                outgoingQueue,
            )
        }
        return request
    }

    private fun ServerMessage.printRequestInfo(localHost: String?, localPort: Int) {
        t.println("${(blue)("◀ REQ")} ${(white)(httpRequest.method)} ${(green)(httpRequest.path)} → ${(brightMagenta)("$localHost:$localPort")}")
        if (httpRequest.queryMap.isNotEmpty())
            t.println((gray)(httpRequest.queryMap.entries.joinToString(" ") { "${it.key}=${it.value}" }))
        if (httpRequest.headersMap.isNotEmpty())
            t.println((cyan)("Request ID: ${correlationId}"))
        t.println(table {
            borderType = SQUARE
            borderStyle = brightBlue
            header {
                style = brightGreen + bold
                row("Name", "Value")
            }
            body {
                httpRequest.headersMap.forEach { (k, v) ->
                    style = brightBlue + italic
                    row(k, v)
                }
            }
        })
    }

    private fun ServerMessage.buildRequest(uri: URI?): HttpRequestBase = when (httpRequest.method.uppercase()) {
        "GET" -> HttpGet(uri)
        "POST" -> HttpPost(uri)
        "PUT" -> HttpPut(uri)
        "DELETE" -> HttpDelete(uri)
        "PATCH" -> HttpPatch(uri)
        "HEAD" -> HttpHead(uri)
        "OPTIONS" -> HttpOptions(uri)
        else -> throw IllegalArgumentException("Unknown method: ${httpRequest.method}")
    }

    fun stop(){
        subscription.cancel()
    }
}

private fun buildStreamingEntity(
    firstChunk: ByteArray?,
    bodyQueue: LinkedBlockingQueue<ByteArray>,
    incomingRequests: ConcurrentHashMap<String, *>,
    httpRequest: HttpRequest,
    timeout: Duration,
    correlationId: String,
    outgoingQueue: LinkedBlockingQueue<ClientMessage>,
): HttpEntity = object : AbstractHttpEntity() {

    init {
        contentType = BasicHeader(
            HTTP.CONTENT_TYPE,
            httpRequest.headersMap["content-type"] ?: "application/octet-stream"
        )
        isChunked = true
    }

    override fun isRepeatable() = false
    override fun getContentLength() = -1L
    override fun isStreaming() = true

    // Not used for streaming — writeTo() is what Apache calls
    override fun getContent(): InputStream = throw UnsupportedOperationException()

    override fun writeTo(outStream: OutputStream) {
        if (firstChunk != null) outStream.write(firstChunk)

        val timeoutNanos = timeout.inWholeNanoseconds
        val startTime = System.nanoTime()

        while (true) {
            val remaining = timeoutNanos - (System.nanoTime() - startTime)
            if (remaining <= 0) {
                handleTimeout(correlationId, outgoingQueue)
                return
            }

            val bytes = bodyQueue.poll(remaining, TimeUnit.NANOSECONDS)
            if (bytes == null) {
                if (!incomingRequests.containsKey(correlationId)) {
                    // No more chunks expected and queue is empty
                    break
                }
                handleTimeout(correlationId, outgoingQueue)
                return
            }

            outStream.write(bytes)

            if (bodyQueue.isEmpty() && !incomingRequests.containsKey(correlationId)) {
                break
            }
        }

        outStream.flush()
    }

    private fun handleTimeout(
        correlationId: String,
        outgoingQueue: LinkedBlockingQueue<ClientMessage>,
    ) {
        incomingRequests.remove(correlationId)
        outgoingQueue.add(clientMessage {
            this.correlationId = correlationId
            httpResponse = httpResponse {
                hasBody = false
                status = 504
                statusMessage = "Gateway Timeout"
            }
        })
    }
}