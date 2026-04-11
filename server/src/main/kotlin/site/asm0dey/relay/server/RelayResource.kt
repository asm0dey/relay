package site.asm0dey.relay.server

import io.quarkus.grpc.GrpcService
import io.vertx.core.http.HttpServerRequest
import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import site.asm0dey.relay.domain.httpRequest
import site.asm0dey.relay.domain.serverMessage
import java.util.*

@Path("/{path:.*}")
@Blocking
class RelayResource(
    @param:GrpcService private val tunnelService: TunnelService,
) {
    private val log = Logger.getLogger(RelayResource::class.java)

    @ConfigProperty(name = "relay.domain")
    lateinit var domain: String

    @GET
    fun handleGet(@PathParam("path") path: String, @Context request: HttpServerRequest) =
        handleRelay(path, request, null)

    @POST
    fun handlePost(@PathParam("path") path: String, @Context request: HttpServerRequest, body: ByteArray?) =
        handleRelay(path, request, body)

    @DELETE
    fun handleDelete(@PathParam("path") path: String, @Context request: HttpServerRequest, body: ByteArray?) =
        handleRelay(path, request, body)

    @OPTIONS
    fun handleOptions(@PathParam("path") path: String, @Context request: HttpServerRequest) =
        handleRelay(path, request, null)

    @PUT
    fun handlePut(@PathParam("path") path: String, @Context request: HttpServerRequest, body: ByteArray?) =
        handleRelay(path, request, body)

    @PATCH
    fun handlePatch(@PathParam("path") path: String, @Context request: HttpServerRequest, body: ByteArray?) =
        handleRelay(path, request, body)

    @HEAD
    fun handleHead(@PathParam("path") path: String, @Context request: HttpServerRequest) =
        handleRelay(path, request, null)

    fun handleRelay(path: String, request: HttpServerRequest, body: ByteArray?): Response {
        log.info(
            "Incoming request: ${request.method()} /$path from ${
                request.authority().host()
            }, body size: ${body?.size ?: 0}"
        )
        val host =
            request.headers()["X-Subdomain"]
                ?: request.query()
                    ?.split("&=")
                    ?.chunked(2)
                    ?.firstOrNull { (a, _) -> a.equals("x-subdomain", ignoreCase = true) }
                    ?.get(2)
                ?: request.authority().host() ?: return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing Host header").build()

        val subdomain = if (host.endsWith(domain)) {
            host.removeSuffix(domain).removeSuffix(".")
        } else {
            host.split(":")[0].split(".")[0]
        }

        if (subdomain.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Subdomain not specified").build()
        }

        val correlationId = UUID.randomUUID().toString()
        val protoRequest = httpRequest {
            this.method = request.method().name()
            this.path = "/" + path + (if (request.query() != null) "?" + request.query() else "")
            request.headers().forEach { (k, v) ->
                this.headers[k.lowercase()] = v
            }
            this.hasBody = body != null && body.isNotEmpty()
        }

        val serverMsg = serverMessage {
            this.correlationId = correlationId
            this.httpRequest = protoRequest
        }

        return try {
            val (protoResponse, inputStream) = tunnelService.startRequest(subdomain, serverMsg, body)

            val responseBuilder = Response.status(protoResponse.status)
            protoResponse.headersMap.forEach { (k, v) ->
                if (k.lowercase() != "content-length" && k.lowercase() != "transfer-encoding") {
                    responseBuilder.header(k, v)
                }
            }

            if (protoResponse.hasBody) {
                responseBuilder.entity(inputStream)
            }
            responseBuilder.build()
        } catch (e: Exception) {
            Response.status(Response.Status.GATEWAY_TIMEOUT).entity(e.message).build()
        }
    }
}
