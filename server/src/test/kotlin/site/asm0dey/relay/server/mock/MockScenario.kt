package site.asm0dey.relay.server.mock

/** Defines how the mock server behaves for one channel. */
data class MockScenario(
    /** Channel this scenario applies to. */
    var channel: String = "",

    /** Message sent to a new client when they connect. Nullable. */
    var greeting: String? = null,

    /**
     * Fixed replies: incoming message text → reply text.
     * Use "*" as a catch-all key.
     */
    var replies: Map<String, String>? = null
)
