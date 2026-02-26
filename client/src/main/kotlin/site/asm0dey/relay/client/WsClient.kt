package site.asm0dey.relay.client

import io.quarkus.runtime.Quarkus
import io.quarkus.websockets.next.BinaryMessageCodec
import io.quarkus.websockets.next.OnBinaryMessage
import io.quarkus.websockets.next.WebSocketClient
import io.quarkus.websockets.next.WebSocketClientConnection
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod.valueOf
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.buffer.Buffer.buffer
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import picocli.CommandLine
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.Control.Payload.ControlAction.*
import site.asm0dey.relay.domain.Response.Payload
import java.lang.reflect.Type

@WebSocketClient(path = "/ws/{secret}")
class WsClient @Inject constructor(parseResult: CommandLine.ParseResult, vertx: Vertx) {

    val webClient = WebClient(vertx)
    val url = "http://localhost:${parseResult.matchedPositional(0).getValue<Int>()}"

    @Suppress("unused")
    @OnBinaryMessage
    suspend fun onMessage(connection: WebSocketClientConnection, message: Envelope) {
        when (val payload = message.payload) {
            is Control -> when (payload.payload.action) {
                SHUTDOWN -> Quarkus.asyncExit(0)
                REGISTER -> TODO()
                REGISTERED -> TODO()
                UNREGISTER -> TODO()
                HEARTBEAT -> TODO()
                STATUS -> TODO()
            }

            is Error -> TODO()
            is Request -> {
                message.correlationId
                val (method, path, query, headers, body, websocketUpgrade) = payload.payload
                var req = webClient
                    .getAbs(url.removeSuffix("/") + "/" + path.removePrefix("/"))
                    .method(valueOf(method))
                query?.forEach { (k, v) -> req = req.addQueryParam(k, v) }
                headers?.forEach { (k, v) -> req = req.putHeader(k, v) }
                val response = req.sendBuffer(buffer(body)).awaitSuspending()
                connection.sendBinary(
                    Envelope(
                        correlationId = message.correlationId,
                        payload = Response(
                            Payload(
                                statusCode = response.statusCode(),
                                headers = response.headers().entries().associate { it.key to it.value },
                                body = response.body().bytes
                            )
                        )
                    ).toByteArray()
                ).awaitSuspending()
            }

            is Response -> throw IllegalStateException("Response $message is not expected to be received here")
        }
    }
}

@Singleton
class EnvelopeCodec : BinaryMessageCodec<Envelope> {
    override fun supports(type: Type?): Boolean = type?.equals(Envelope::class.java) ?: false

    override fun encode(value: Envelope?) = value?.let { Buffer.buffer(it.toByteArray()) }

    override fun decode(type: Type?, value: Buffer?) = value?.let { Envelope.fromByteArray(it.bytes) }

}