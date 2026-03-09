@file:OptIn(ExperimentalTime::class)

package site.asm0dey.relay.server

import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.coroutines.asFlow
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import org.jboss.resteasy.reactive.RestResponse
import site.asm0dey.relay.domain.Envelope
import site.asm0dey.relay.domain.Request
import site.asm0dey.relay.domain.Request.RequestPayload
import java.util.UUID.randomUUID
import kotlin.time.ExperimentalTime
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder as RestResponseBuilder

@Path("")
class HttpEndpoint {
    @Inject
    lateinit var socketService: SocketService

    @Inject
    lateinit var streamManager: StreamManager

    @Inject
    lateinit var serverConfig: ServerConfig

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.websockets-next.server.max-frame-size")
    lateinit var maxFrameSize: jakarta.inject.Provider<Int>

    @Context
    lateinit var request: HttpServerRequest

    @POST
    suspend fun post(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ): RestResponse<ByteArray?> {
        return processStreamingRequest(domain, domainHeader, "POST", body?.let { Multi.createFrom().item(it) })
    }

    @PUT
    suspend fun put(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ): RestResponse<ByteArray?> {
        return processStreamingRequest(domain, domainHeader, "PUT", body?.let { Multi.createFrom().item(it) })
    }

    @GET
    suspend fun get(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ): RestResponse<ByteArray?> {
        return response(makeRequest(domain, domainHeader, "GET"))
    }

    @HEAD
    suspend fun head(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ): RestResponse<ByteArray?> {
        return response(makeRequest(domain, domainHeader, "HEAD"))
    }

    @DELETE
    suspend fun delete(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ): RestResponse<ByteArray?> {
        return processStreamingRequest(domain, domainHeader, "DELETE", body?.let { Multi.createFrom().item(it) })
    }

    @OPTIONS
    suspend fun options(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ): RestResponse<ByteArray?> {
        return response(makeRequest(domain, domainHeader, "OPTIONS"))
    }

    @PATCH
    suspend fun patch(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ): RestResponse<ByteArray?> {
        return processStreamingRequest(domain, domainHeader, "PATCH", body?.let { Multi.createFrom().item(it) })
    }

    private suspend fun processStreamingRequest(
        domain: String?,
        domainHeader: String?,
        method: String,
        body: Multi<ByteArray>?
    ): RestResponse<ByteArray?> {
        val contentLength = request.getHeader("Content-Length")?.toLongOrNull()
        val isStreaming = body != null && (contentLength == null || contentLength > maxFrameSize.get())

        return if (isStreaming) {
            val host = extractHost(domain, domainHeader)
            val streamId = "$host-${randomUUID()}"
            val sender = StreamingSender(socketService, host, streamId, serverConfig, maxFrameSize.get())
            streamManager.registerUpload(streamId, sender)

            sender.sendInit(
                method,
                request.path(),
                request.getHeader("Content-Type"),
                contentLength,
                request.headers().entries().filterNot { it.key.startsWith("X-Domain") || it.key.equals("Content-Length", true) }
                    .associate { it.key to it.value }
            )

            val flow = body.asFlow()
            flow.collect { chunk ->
                sender.sendChunk(chunk, false)
            }
            sender.sendChunk(ByteArray(0), true)

            response(socketService.waitForResponse(streamId))
        } else {
            // Non-streaming: collect body and use makeRequest
            val fullBody = body?.collect()?.asList()?.awaitSuspending()?.fold(ByteArray(0)) { acc: ByteArray, bytes: ByteArray -> acc + bytes }
            response(makeRequest(domain, domainHeader, method, fullBody))
        }
    }

    private suspend fun makeRequest(
        domain: String?,
        domainHeader: String?,
        method: String,
        body: ByteArray? = null
    ): Envelope {
        val host = extractHost(domain, domainHeader)
        val envelope = envelope(host, method, body)
        return socketService.request(envelope, host)
    }

    private fun envelope(
        host: String,
        method: String,
        body: ByteArray?
    ): Envelope = Envelope(
        correlationId = "$host-${randomUUID()}",
        payload = Request(
            RequestPayload(
                method = method,
                path = request.path(),
                query = request
                    .query()
                    ?.split('&')
                    ?.filterNot { it.startsWith("X-Domain") }
                    ?.mapNotNull { param ->
                        val parts = param.split('=', limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    ?.toMap() ?: hashMapOf(),
                headers = request.headers().entries().filterNot { it.key.startsWith("X-Domain") }
                    .associate { it.key to it.value },
                body = body
            )
        )
    )

    private fun response(envelope: Envelope): RestResponse<ByteArray?> {
        val responsePayload = envelope.payload as? site.asm0dey.relay.domain.Response
            ?: throw IllegalStateException("Expected Response payload, got ${envelope.payload}")
        val payload = responsePayload.value

        val status = RestResponse.Status.fromStatusCode(payload.statusCode)

        var builder = RestResponseBuilder.create<ByteArray?>(status).entity(payload.body)
        payload.headers.forEach { (key, value) ->
            builder = builder.header(key, value)
        }
        return builder.build()
    }


    private fun extractHost(domain: String?, domainHeader: String?): String =
        (domain ?: domainHeader ?: request.getHeader("Host"))
            ?.substringBefore('.') ?: throw IllegalStateException("No host found")
}