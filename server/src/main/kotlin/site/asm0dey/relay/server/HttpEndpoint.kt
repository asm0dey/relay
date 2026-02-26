package site.asm0dey.relay.server

import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HEAD
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import site.asm0dey.relay.domain.Envelope
import site.asm0dey.relay.domain.Request
import site.asm0dey.relay.domain.Request.Payload
import java.util.*

@Path("")
class HttpEndpoint {
    @Inject
    lateinit var socketService: SocketService

    @Context
    lateinit var request: HttpServerRequest

    @GET
    suspend fun get(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ) {
        val envelope = envelope(null)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @POST
    suspend fun post(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ) {
        val envelope = envelope(body)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @PUT
    suspend fun put(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ) {
        val envelope = envelope(body)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @HEAD
    suspend fun head(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ) {
        val envelope = envelope(null)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @DELETE
    suspend fun delete(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ) {
        val envelope = envelope(body)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @OPTIONS
    suspend fun options(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
    ) {
        val envelope = envelope(null)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    @PATCH
    suspend fun patch(
        @QueryParam("X-Domain") domain: String?,
        @HeaderParam("X-Domain") domainHeader: String?,
        body: ByteArray?
    ) {
        val envelope = envelope(body)
        socketService.request(envelope, extractHost(domain, domainHeader))
    }

    private fun envelope(body: ByteArray?): Envelope = Envelope(
        correlationId = UUID.randomUUID().toString(),
        payload = Request(
            Payload(
                method = "GET",
                path = request.path(),
                query = request.query().split('&').map { it.split('=') }.filterNot { it[0] == "X-Domain" }
                    .associate { it[0] to it[1] },
                headers = request.headers().entries().filterNot { it.key.startsWith("X-Domain") }
                    .associate { it.key to it.value },
                body = body
            )
        )
    )


    private fun extractHost(domain: String?, domainHeader: String?): String =
        (domain ?: domainHeader ?: request.getHeader("Host"))
            ?.substringBefore('.') ?: throw IllegalStateException("No host found")

}