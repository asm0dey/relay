package site.asm0dey.relay.client

import io.quarkus.runtime.Quarkus
import io.quarkus.websockets.next.BinaryMessageCodec
import io.quarkus.websockets.next.Closed
import io.quarkus.websockets.next.OnBinaryMessage
import io.quarkus.websockets.next.WebSocketClient
import io.quarkus.websockets.next.WebSocketClientConnection
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod.valueOf
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.buffer.Buffer.buffer
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.enterprise.event.ObservesAsync
import jakarta.inject.Inject
import jakarta.inject.Singleton
import picocli.CommandLine.ParseResult
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.Control.ControlPayload.ControlAction.*
import site.asm0dey.relay.domain.Response.ResponsePayload
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

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
                UNREGISTER -> TODO()
                HEARTBEAT -> {
                    val heartbeatResponse = Envelope(
                        correlationId = message.correlationId,
                        payload = Control(Control.ControlPayload(STATUS))
                    )
                    connection.sendBinary(heartbeatResponse.toByteArray()).awaitSuspending()
                }
                STATUS -> TODO()
                else -> {}
            }

            is Error -> TODO()
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
                activeStreams[error.correlationId]?.onError(error)
            }
            is StreamInit, is StreamChunk -> TODO("Handle stream messages from server")
        }
    }
}

@Singleton
class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}