package site.asm0dey.relay.server

data class WsTunnel(
    val connectionId: String,
    val domain: String,
    @Volatile var state: TunnelState = TunnelState.CONNECTING,
    @Volatile var closeCode: Int = 1000,
    @Volatile var closeReason: String = ""
) {
    enum class TunnelState { CONNECTING, OPEN, CLOSING, CLOSED }

    fun establish() {
        require(state == TunnelState.CONNECTING) { "Cannot establish tunnel from state: $state" }
        state = TunnelState.OPEN
    }

    fun initiateClose(code: Int, reason: String) {
        require(state == TunnelState.OPEN) { "Cannot initiate close from state: $state" }
        closeCode = code
        closeReason = reason
        state = TunnelState.CLOSING
    }

    fun close() {
        require(state == TunnelState.CLOSING) { "Cannot close from state: $state" }
        state = TunnelState.CLOSED
    }
}
