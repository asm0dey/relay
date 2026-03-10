package site.asm0dey.relay.server.mock

import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Records all WebSocket events so tests can assert on them. */
@ApplicationScoped
class WebSocketSessionStore {

    data class Event(
        val sessionId: String,
        val channel: String,
        val type: String, // OPEN | CLOSE | ERROR | MESSAGE
        val payload: String?,
        val at: Instant
    )

    private val events = CopyOnWriteArrayList<Event>()

    fun record(sessionId: String, channel: String, type: String, payload: String?) {
        events.add(Event(sessionId, channel, type, payload, Instant.now()))
    }

    fun all(): List<Event> = events.toList()

    fun forChannel(channel: String): List<Event> {
        return events.filter { it.channel == channel }
    }

    fun messages(channel: String): List<Event> {
        return events.filter { it.channel == channel && "MESSAGE" == it.type }
    }

    fun reset() {
        events.clear()
    }
}
