package site.asm0dey.relay.server.mock

import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import site.asm0dey.relay.server.mock.MockScenario as MockScenarioData

/**
 * REST API to control the mock server at runtime.
 *
 * POST /mock/scenarios           – register a scenario
 * DELETE /mock/scenarios/{chan}  – remove a scenario
 * DELETE /mock/scenarios         – remove all
 * POST /mock/broadcast/{chan}    – push a message to all sessions on channel
 * GET  /mock/events              – list all recorded WS events
 * DELETE /mock/events            – clear event log
 */
@Path("/mock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MockControlResource {

    @Inject
    lateinit var registry: MockScenarioRegistry

    @Inject
    lateinit var store: WebSocketSessionStore

    @Inject
    lateinit var wsServer: MockWebSocketServer

    // --- scenarios ---

    @POST
    @Path("/scenarios")
    fun registerScenario(scenario: MockScenarioData): Response {
        registry.register(scenario)
        return Response.ok(mapOf("registered" to scenario.channel)).build()
    }

    @DELETE
    @Path("/scenarios/{channel}")
    fun clearScenario(@PathParam("channel") channel: String): Response {
        registry.clear(channel)
        return Response.noContent().build()
    }

    @DELETE
    @Path("/scenarios")
    fun clearAllScenarios(): Response {
        registry.clearAll()
        return Response.noContent().build()
    }

    @GET
    @Path("/scenarios")
    fun listScenarios(): Map<String, MockScenarioData> {
        return registry.all()
    }

    // --- broadcasting ---

    @POST
    @Path("/broadcast/{channel}")
    fun broadcast(@PathParam("channel") channel: String, body: Map<String, String>): Response {
        val msg = body["message"]
        if (msg == null) return Response.status(400).entity("missing 'message'").build()
        wsServer.broadcast(channel, msg)
        return Response.ok(mapOf("sent" to msg)).build()
    }

    // --- event inspection ---

    @GET
    @Path("/events")
    fun events(
        @QueryParam("channel") channel: String?
    ): List<WebSocketSessionStore.Event> {
        return if (channel != null) store.forChannel(channel) else store.all()
    }

    @DELETE
    @Path("/events")
    fun resetEvents(): Response {
        store.reset()
        return Response.noContent().build()
    }
}
