package site.asm0dey.relay.client

import io.quarkus.runtime.Quarkus
import io.quarkus.websockets.next.BinaryMessageCodec
import io.quarkus.websockets.next.OnBinaryMessage
import io.quarkus.websockets.next.WebSocketClient
import io.quarkus.websockets.next.WebSocketClientConnection
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod.valueOf
import io.vertx.core.streams.WriteStream
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.buffer.Buffer.buffer
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import picocli.CommandLine.ParseResult
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.Control.ControlPayload.ControlAction.*
import site.asm0dey.relay.domain.Response.ResponsePayload
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import io.vertx.mutiny.core.buffer.Buffer as MBuffer

@WebSocketClient(path = "/ws/{secret}")
@Singleton
open class WsClient @Inject constructor(parseResult: ParseResult, vertx: Vertx) {

    val webClient = WebClient.create(vertx)
    val localHost = parseResult.matchedOption("l")?.getValue<String>() ?: "localhost"
    val localPort = parseResult.matchedPositional(0).getValue<Int>()
    val url = "http://$localHost:$localPort"
    lateinit var connection: WebSocketClientConnection
    var assignedSubdomain: String? = null
    val activeStreams = ConcurrentHashMap<String, StreamingSender>()
    val activeUploads = ConcurrentHashMap<String, WriteStream<Buffer>>()

    @Suppress("unused")
    @OnBinaryMessage
    suspend fun onMessage(connection: WebSocketClientConnection, message: Envelope) {
        this.connection = connection
        when (val payload = message.payload) {
            is Control -> when (payload.value.action) {
                SHUTDOWN -> Quarkus.asyncExit(0)
                REGISTERED -> {
                    val subdomain = payload.value.subdomain
                    assignedSubdomain = subdomain
                    val publicUrl = payload.value.publicUrl
                    println(publicUrl)
                }

                UNREGISTER -> {
                    println("Unregistered from server. Exiting.")
                    Quarkus.asyncExit(0)
                }

                HEARTBEAT -> {
                    val heartbeatResponse = Envelope(
                        correlationId = message.correlationId,
                        payload = Control(Control.ControlPayload(STATUS))
                    )
                    connection.sendBinary(heartbeatResponse.toByteArray()).awaitSuspending()
                }

                STATUS -> {
                    val statusResponse = Envelope(
                        correlationId = message.correlationId,
                        payload = Control(Control.ControlPayload(REGISTERED, subdomain = assignedSubdomain))
                    )
                    connection.sendBinary(statusResponse.toByteArray()).awaitSuspending()
                }

                else -> {}
            }

            is Error -> {
                System.err.println("Error from server: ${payload.value.code} - ${payload.value.message}")
            }
            is Request -> {
                message.correlationId
                val (method, path, query, headers, body, _) = payload.value
                val queryStr = if (query.isNotEmpty()) {
                    "?" + query.entries.joinToString("&") { (k, v) -> "$k=$v" }
                } else ""
                println("-> $method $localHost:$localPort$path$queryStr")
                var req = webClient
                    .getAbs(url.removeSuffix("/") + "/" + path.removePrefix("/"))
                    .method(valueOf(method))
                query.forEach { (k, v) -> req = req.addQueryParam(k, v) }
                headers.forEach { (k, v) -> req = req.putHeader(k, v) }
                val response = (body?.let { req.sendBuffer(buffer(it)) } ?: req.send()).awaitSuspending()
                connection.sendBinary(
                    Envelope(
                        correlationId = message.correlationId,
                        payload = Response(
                            ResponsePayload(
                                statusCode = response.statusCode(),
                                headers = response.headers().entries().associate { it.key to it.value },
                                body = response.body()?.bytes
                            )
                        )
                    ).toByteArray()
                ).awaitSuspending()
            }

            is Response -> throw IllegalStateException("Response $message is not expected to be received here")
            is StreamAck -> {
                val ack = payload.value
                activeStreams[ack.correlationId]?.onAck(ack)
            }
            is StreamError -> {
                val error = payload.value
                activeStreams[error.correlationId]?.onError()
                activeUploads.remove(error.correlationId)?.end()
            }

            is StreamInit -> {
                val init = payload.value
                val req = webClient
                    .requestAbs(valueOf(init.method ?: "POST"), url.removeSuffix("/") + "/" + (init.path ?: "").removePrefix("/"))
                init.headers.forEach { (k, v) -> req.putHeader(k, v) }
                init.contentType?.let { req.putHeader("Content-Type", it) }
                init.contentLength?.let { req.putHeader("Content-Length", it.toString()) }

                val chunks = mutableListOf<ByteArray>()
                val bridge = object : WriteStream<Buffer> {
                    override fun write(data: Buffer): Future<Void> {
                        chunks.add(data.bytes)
                        return Future.succeededFuture()
                    }
                    override fun write(data: Buffer, handler: io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>?) {
                        write(data)
                        handler?.handle(Future.succeededFuture())
                    }
                    override fun end(): Future<Void> {
                        val fullBody = chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
                        req.sendBuffer(MBuffer.newInstance(Buffer.buffer(fullBody))).subscribe().with { response ->
                             val responsePayload = Response(
                                ResponsePayload(
                                    statusCode = response.statusCode(),
                                    headers = response.headers().entries().associate { it.key to it.value },
                                    body = response.body()?.bytes
                                )
                            )
                            connection.sendBinary(
                                Envelope(correlationId = init.correlationId, payload = responsePayload).toByteArray()
                            ).subscribe().with { }
                        }
                        return Future.succeededFuture()
                    }
                    override fun end(handler: io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>?) {
                        end()
                        handler?.handle(Future.succeededFuture())
                    }
                    override fun setWriteQueueMaxSize(maxSize: Int): WriteStream<Buffer> = this
                    override fun writeQueueFull(): Boolean = false
                    override fun drainHandler(handler: io.vertx.core.Handler<Void>?): WriteStream<Buffer> = this
                    override fun exceptionHandler(handler: io.vertx.core.Handler<Throwable>?): WriteStream<Buffer> = this
                }
                activeUploads[init.correlationId] = bridge
            }

            is StreamChunk -> {
                val chunk = payload.value
                val writeStream = activeUploads[chunk.correlationId]
                if (writeStream != null) {
                    if (chunk.data.isNotEmpty()) {
                        writeStream.write(Buffer.buffer(chunk.data))
                    }
                    if (chunk.isLast) {
                        writeStream.end()
                        activeUploads.remove(chunk.correlationId)
                    }
                    // Send ACK back to server
                    val ack = Envelope(
                        correlationId = chunk.correlationId,
                        payload = StreamAck(StreamAck.StreamAckPayload(chunk.correlationId, chunk.chunkIndex))
                    )
                    connection.sendBinary(ack.toByteArray()).awaitSuspending()
                }
            }

            is WsUpgrade -> {
                val upgrade = payload.value
                val req = webClient
                    .requestAbs(valueOf("GET"), url.removeSuffix("/") + "/" + upgrade.path.removePrefix("/"))
                upgrade.headers.forEach { (k, v) -> req.putHeader(k, v) }
                req.putHeader("Upgrade", "websocket")
                req.putHeader("Connection", "Upgrade")

                // Vert.x WebClient doesn't support WebSocket upgrade directly easily like this, 
                // we'd need to use HttpClient.
                // For now, let's assume we can't easily implement it without more changes to how webClient is used.
                // But we should at least not crash.
                System.err.println("WebSocket upgrade not fully implemented yet in client for ${upgrade.wsId}")
            }

            is WsUpgradeResponse -> {
                // Client usually doesn't receive this from server unless it initiated it
                System.err.println("Received unexpected WsUpgradeResponse for ${payload.value.wsId}")
            }

            is WsMessage -> {
                val wsMsg = payload.value
                // Here we would forward to the local WebSocket connection if we had one
                System.err.println("Received WsMessage for ${wsMsg.wsId}, but forwarding is not implemented")
            }

            is WsClose -> {
                val close = payload.value
                activeUploads.remove(close.wsId)?.end()
                System.out.println("Connection ${close.wsId} closed: ${close.code} ${close.reason}")
            }
        }
    }
}

@Singleton
class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}