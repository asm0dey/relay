package site.asm0dey.relay.server.mock

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import org.jboss.logging.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock WebSocket server.
 *
 * Connect via:  ws://localhost:8080/mock-ws/{channel}
 *
 * channel examples: "orders", "notifications", "chat"
 *
 * The server:
 *  - records every received message
 *  - auto-replies based on configured scenarios
 *  - can broadcast on demand via the REST control API
 */
@ServerEndpoint("/mock-ws/{channel}")
@ApplicationScoped
class MockWebSocketServer {

    private val LOG = Logger.getLogger(MockWebSocketServer::class.java)

    /** sessionId → session */
    private val sessions = ConcurrentHashMap<String, Session>()

    /** sessionId → channel name */
    private val sessionChannels = ConcurrentHashMap<String, String>()

    @Inject
    lateinit var sessionStore: WebSocketSessionStore

    @Inject
    lateinit var scenarioRegistry: MockScenarioRegistry

    // ------------------------------------------------------------------ lifecycle

    @OnOpen
    fun onOpen(session: Session, @PathParam("channel") channel: String) {
        sessions[session.id] = session
        sessionChannels[session.id] = channel
        sessionStore.record(session.id, channel, "OPEN", null)
        LOG.infof("WS OPEN  id=%s channel=%s", session.id, channel)

        // Send a greeting if a scenario defines one
        val greeting = scenarioRegistry.greeting(channel)
        if (greeting != null) {
            send(session, greeting)
        }
    }

    @OnClose
    fun onClose(session: Session, @PathParam("channel") channel: String) {
        sessions.remove(session.id)
        sessionChannels.remove(session.id)
        sessionStore.record(session.id, channel, "CLOSE", null)
        LOG.infof("WS CLOSE id=%s channel=%s", session.id, channel)
    }

    @OnError
    fun onError(session: Session, @PathParam("channel") channel: String, t: Throwable) {
        sessions.remove(session.id)
        sessionChannels.remove(session.id)
        sessionStore.record(session.id, channel, "ERROR", t.message)
        LOG.errorf("WS ERROR id=%s channel=%s error=%s", session.id, channel, t.message)
    }

    @OnMessage
    fun onMessage(message: String, session: Session, @PathParam("channel") channel: String) {
        sessionStore.record(session.id, channel, "MESSAGE", if (message.length > 100) message.take(100) + "..." else message)
        LOG.infof("WS MSG   id=%s channel=%s size=%d", session.id, channel, message.length)

        // Look up a scripted reply for this channel + message
        val reply = scenarioRegistry.reply(channel, message)
        if (reply != null) {
            send(session, reply)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun send(session: Session, message: String) {
        session.asyncRemote.sendText(message) { result ->
            if (!result.isOK) {
                LOG.warnf("Send failed to %s: %s", session.id, result.exception)
            }
        }
    }

    /** Broadcast a message to every session on a given channel. */
    fun broadcast(channel: String, message: String) {
        sessions.forEach { (id, session) ->
            if (channel == sessionChannels[id]) {
                send(session, message)
            }
        }
    }

    /** Broadcast to ALL sessions regardless of channel. */
    fun broadcastAll(message: String) {
        sessions.values.forEach { s -> send(s, message) }
    }

    fun getSessions(): Map<String, Session> {
        return sessions
    }
}
