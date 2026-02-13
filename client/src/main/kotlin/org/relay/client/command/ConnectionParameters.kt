package org.relay.client.command

/**
 * Value object containing normalized connection settings derived from CLI arguments.
 * This class bridges the CLI layer (TunnelCommand) with the connection layer (TunnelClient).
 */
data class ConnectionParameters(
    /** The local HTTP service port number */
    val port: Int,

    /** The tunnel server hostname */
    val server: String,

    /** The tunnel server port (null for default) */
    val serverPort: Int? = null,

    /** Authentication secret key */
    val secretKey: String,

    /** Optional requested subdomain */
    val subdomain: String? = null,

    /** Use ws:// instead of wss:// */
    val insecure: Boolean = false,

    /** Output verbosity level */
    val logLevel: LogLevel = LogLevel.INFO
) {
    /**
     * The full local URL constructed from the port.
     * Format: http://localhost:{port}
     */
    val localUrl: String
        get() = "http://localhost:$port"
    
    /**
     * The full WebSocket server URL constructed from the hostname and port.
     * Format: wss://{hostname}:{port}/ws (or ws:// if insecure)
     * Default ports: 443 for wss://, 80 for ws://
     */
    val serverUrl: String
        get() {
            val protocol = if (insecure) "ws" else "wss"
            val cleanHostname = stripProtocol(server)
            val defaultPort = if (insecure) 80 else 443
            val effectivePort = serverPort ?: defaultPort

            // Only include port in URL if it's not the default for the protocol
            val portSuffix = if (effectivePort == defaultPort) "" else ":$effectivePort"
            
            val queryParams = mutableListOf<String>()
            queryParams.add("secret=$secretKey")
            subdomain?.let { queryParams.add("subdomain=$it") }
            
            val queryString = queryParams.joinToString("&", prefix = "?")
            return "$protocol://$cleanHostname$portSuffix/ws$queryString"
        }

    /**
     * Removes common protocol prefixes from a hostname string.
     * Strips: http://, https://, ws://, wss://
     *
     * @param hostname the hostname potentially with protocol prefix
     * @return the hostname without any protocol prefix
     */
    fun stripProtocol(hostname: String): String {
        return hostname
            .removePrefix("wss://")
            .removePrefix("ws://")
            .removePrefix("https://")
            .removePrefix("http://")
    }
    
    companion object {
        /**
         * Creates ConnectionParameters from a TunnelCommandInterface.
         * 
         * @param command the CLI command with parsed arguments
         * @return ConnectionParameters with all fields populated
         */
        fun fromCommand(command: TunnelCommandInterface): ConnectionParameters {
            return ConnectionParameters(
                port = command.port,
                server = command.server,
                serverPort = command.serverPort,
                secretKey = command.secretKey,
                subdomain = command.subdomain,
                insecure = command.insecure,
                logLevel = LogLevel.fromFlags(command.quiet, command.verbose)
            )
        }
    }
}
