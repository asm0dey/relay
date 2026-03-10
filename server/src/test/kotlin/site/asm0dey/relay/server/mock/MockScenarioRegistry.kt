package site.asm0dey.relay.server.mock

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

/** Holds the currently configured mock scenarios. Thread-safe. */
@ApplicationScoped
class MockScenarioRegistry {

    private val scenarios = ConcurrentHashMap<String, MockScenario>()

    fun register(scenario: MockScenario) {
        scenarios[scenario.channel] = scenario
    }

    fun clear(channel: String) {
        scenarios.remove(channel)
    }

    fun clearAll() {
        scenarios.clear()
    }

    fun greeting(channel: String): String? {
        val s = scenarios[channel]
        return s?.greeting
    }

    fun reply(channel: String, incomingMessage: String): String? {
        val s = scenarios[channel]
        if (s == null || s.replies == null) return null

        val exact = s.replies!![incomingMessage]
        if (exact != null) return exact

        return s.replies!!["*"] // catch-all
    }

    fun all(): Map<String, MockScenario> {
        return scenarios.toMap()
    }
}
