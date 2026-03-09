package site.asm0dey.relay.server

data class WsTunnel(
    val wsId: String,
    val localConnectionId: String,
    val domain: String,
    var state: TunnelState = TunnelState.CONNECTING,
    var closeCode: Int = 1000,
    var closeReason: String = ""
) {
    enum class TunnelState { CONNECTING, OPEN, CLOSING, CLOSED }

    val isEstablished: Boolean
        get() = state == TunnelState.OPEN

    fun establish() {
        state = TunnelState.OPEN
    }

    fun initiateClose(code: Int, reason: String) {
        closeCode = code
        closeReason = reason
        state = TunnelState.CLOSING
    }

    fun close() {
        state = TunnelState.CLOSED
    }
}
