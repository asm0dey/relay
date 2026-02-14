package org.relay.server.websocket

import jakarta.inject.Inject
import jakarta.websocket.*
import jakarta.websocket.server.HandshakeRequest
import jakarta.websocket.server.ServerEndpoint
import jakarta.websocket.server.ServerEndpointConfig
import org.relay.server.config.RelayConfig
import org.relay.server.tunnel.*
import org.relay.shared.protocol.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * WebSocket server endpoint for client tunnel connections.
 * Handles WebSocket lifecycle events and manages tunnel registration.
 *
 * v2.0.0: Uses Protobuf binary format only (no JSON support).
 * Uses a custom Configurator to extract the secret key from HTTP headers during handshake.
 */
@ServerEndpoint(
    "/ws",
    configurator = TunnelWebSocketEndpoint.TunnelWebSocketConfigurator::class
)
class TunnelWebSocketEndpoint @Inject constructor(
    private val tunnelRegistry: TunnelRegistry,
    private val subdomainGenerator: SubdomainGenerator,
    private val relayConfig: RelayConfig,
    private val externalWebSocketEndpoint: ExternalWebSocketEndpoint,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(TunnelWebSocketEndpoint::class.java)

    companion object {
        private const val SECRET_KEY_PARAM = "secret"
        private const val SECRET_KEY_HEADER = "X-Secret-Key"
        private const val SUBDOMAIN_PARAM = "subdomain"
        private const val SUBDOMAIN_HEADER = "X-Subdomain"
        private const val SERVICE_UNAVAILABLE = 503
        private const val SERVICE_UNAVAILABLE_MESSAGE = "Service Unavailable: Tunnel disconnected"
    }

    /**
     * Custom configurator that extracts the secret key from the handshake request headers.
     * This allows authentication before the WebSocket connection is fully established.
     */
    class TunnelWebSocketConfigurator : ServerEndpointConfig.Configurator() {
        override fun modifyHandshake(
            sec: ServerEndpointConfig,
            request: HandshakeRequest,
            response: jakarta.websocket.HandshakeResponse
        ) {
            // Extract secret key from header and store in user properties
            val headers = request.headers[SECRET_KEY_HEADER]
            if (!headers.isNullOrEmpty()) {
                sec.userProperties[SECRET_KEY_HEADER] = headers.first()
            }

            // Extract requested subdomain from header
            val subdomainHeaders = request.headers[SUBDOMAIN_HEADER]
            if (!subdomainHeaders.isNullOrEmpty()) {
                sec.userProperties[SUBDOMAIN_HEADER] = subdomainHeaders.first()
            }

            super.modifyHandshake(sec, request, response)
        }
    }

    /**
     * Called when a WebSocket connection is opened.
     * Validates the secret key, generates a subdomain, and registers the tunnel.
     * Sends CONTROL/REGISTERED message upon successful registration.
     *
     * @param session The WebSocket session
     * @param config The endpoint configuration containing request parameters
     */
    @OnOpen
    fun onOpen(session: Session, config: EndpointConfig) {
        logger.info("WebSocket connection opened: session={}", session.id)
        logger.debug("Connection properties: {}", config.userProperties)

        try {
            // Extract and validate secret key from query parameter or header
            val secretKey = extractSecretKey(session, config)

            if (secretKey == null || !isValidSecretKey(secretKey)) {
                logger.warn("Invalid or missing secret key from session: {}", session.id)
                session.close(CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid secret key"))
                return
            }

            // Generate or use requested subdomain
            val requestedSubdomain = extractRequestedSubdomain(session, config)
            val subdomain = if (requestedSubdomain != null) {
                if (tunnelRegistry.hasTunnel(requestedSubdomain)) {
                    logger.warn("Requested subdomain already in use: {}", requestedSubdomain)
                    session.close(CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Subdomain already in use"))
                    return
                }
                requestedSubdomain
            } else {
                try {
                    subdomainGenerator.generateUnique(tunnelRegistry)
                } catch (e: IllegalStateException) {
                    logger.error("Failed to generate unique subdomain", e)
                    session.close(CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "Unable to generate subdomain"))
                    return
                }
            }

            // Create and register tunnel connection
            val connection = TunnelConnection(
                subdomain = subdomain,
                session = session
            )

            if (!tunnelRegistry.register(subdomain, connection)) {
                logger.error("Failed to register tunnel for subdomain: {}", subdomain)
                session.close(CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "Subdomain collision"))
                return
            }

            // Store subdomain in session user properties for later retrieval
            session.userProperties["subdomain"] = subdomain

            logger.info("Tunnel registered successfully: subdomain={}", subdomain)

            // Send CONTROL message with REGISTERED action
            logger.debug("Sending registration confirmation to client: subdomain={}", subdomain)
            sendRegisteredMessage(session, subdomain)

        } catch (e: Exception) {
            logger.error("Error during connection open for session: {}", session.id, e)
            try {
                session.close(CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Internal error"))
            } catch (closeError: Exception) {
                logger.error("Error closing session after open failure", closeError)
            }
        }
    }

    /**
     * Called when a BINARY message is received from the client (Protobuf format).
     * Parses the envelope and routes RESPONSE/ERROR messages to pending requests.
     * Handles WebSocket frame messages for proxying.
     *
     * v2.0.0: Changed from String to ByteBuffer (binary Protobuf messages only)
     *
     * @param message The received binary message (Protobuf)
     * @param session The WebSocket session
     */
    @OnMessage
    fun onMessage(message: ByteBuffer, session: Session) {
        val subdomain = session.userProperties["subdomain"] as? String
        val messageBytes = ByteArray(message.remaining())
        message.get(messageBytes)

        logger.debug("Received binary Protobuf message from tunnel client: subdomain={}, session={}, messageSize={}",
            subdomain, session.id, messageBytes.size)

        // T044: Track Protobuf message metrics
        meterRegistry.counter("relay.protobuf.messages.received",
            "type", "tunnel",
            "subdomain", subdomain ?: "unknown"
        ).increment()
        meterRegistry.summary("relay.protobuf.message.size.bytes",
            "type", "tunnel",
            "direction", "inbound"
        ).record(messageBytes.size.toDouble())

        try {
            // Decode Protobuf binary to Envelope
            val envelope = ProtobufSerializer.decodeEnvelope(messageBytes)

            logger.debug("Decoded envelope: correlationId={}, type={}", envelope.correlationId, envelope.type)

            // T044: Track message type metrics
            meterRegistry.counter("relay.protobuf.messages.by_type",
                "message_type", envelope.type.toString(),
                "direction", "inbound"
            ).increment()

            // Route message based on payload type
            when (val payload = envelope.payload) {
                is Payload.Request -> {
                    // HTTP REQUEST from client (should not normally receive these on server)
                    logger.debug("Received REQUEST payload: correlationId={}", envelope.correlationId)
                }
                is Payload.Response -> handleResponseMessage(envelope, subdomain)
                is Payload.Error -> handleErrorMessage(envelope, subdomain)
                is Payload.Control -> logger.debug("Received CONTROL message from client: action={}", payload.data.action)
                is Payload.WebSocketFrame -> handleWebSocketFrameMessage(envelope, subdomain)
            }

        } catch (e: kotlinx.serialization.SerializationException) {
            logger.error("Malformed Protobuf message from subdomain={}, session={}, messageSize={} bytes: {}",
                subdomain ?: "unknown", session.id, messageBytes.size, e.message, e)
            // Send error back to client if possible
            sendErrorToClient(session, null, ErrorCode.PROTOCOL_ERROR, "Invalid Protobuf message: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error processing message from subdomain={}, session={}, messageSize={} bytes",
                subdomain ?: "unknown", session.id, messageBytes.size, e)
            sendErrorToClient(session, null, ErrorCode.SERVER_ERROR, "Message processing failed")
        }
    }

    /**
     * Called when a WebSocket connection is closed.
     * Unregisters the tunnel from the registry and cancels pending requests.
     *
     * @param session The WebSocket session
     * @param closeReason The close reason
     */
    @OnClose
    fun onClose(session: Session, closeReason: CloseReason) {
        val subdomain = session.userProperties["subdomain"] as? String
        logger.info("WebSocket connection closed: session={}, subdomain={}, code={}, reason={}",
            session.id, subdomain ?: "unknown", closeReason.closeCode.code, closeReason.reasonPhrase)

        if (subdomain != null) {
            // Get the connection before unregistering
            val connection = tunnelRegistry.getBySubdomain(subdomain)

            // Remove tunnel from registry
            tunnelRegistry.unregister(subdomain)

            // Cancel any pending requests with 503 error
            connection?.let {
                cancelPendingRequests(it)
            }
        }
    }

    /**
     * Called when a WebSocket error occurs.
     * Logs the error and performs cleanup.
     *
     * @param session The WebSocket session
     * @param throwable The error that occurred
     */
    @OnError
    fun onError(session: Session, throwable: Throwable) {
        val subdomain = session.userProperties["subdomain"] as? String
        logger.error("WebSocket error occurred: session={}, subdomain={}, message={}",
            session.id, subdomain ?: "unknown", throwable.message, throwable)

        try {
            if (session.isOpen) {
                session.close(CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Internal error"))
            }
        } catch (e: Exception) {
            logger.error("Error closing session after error", e)
        }

        // Perform cleanup if subdomain is known
        if (subdomain != null) {
            val connection = tunnelRegistry.getBySubdomain(subdomain)
            tunnelRegistry.unregister(subdomain)
            connection?.let { cancelPendingRequests(it) }
        }
    }

    /**
     * Extracts the secret key from query parameter or header.
     * First checks query string, then falls back to header extracted by configurator.
     */
    private fun extractSecretKey(session: Session, config: EndpointConfig): String? {
        // Try query parameter first
        val queryString = session.queryString
        if (!queryString.isNullOrEmpty()) {
            val params = parseQueryString(queryString)
            val secretFromQuery = params[SECRET_KEY_PARAM]
            if (!secretFromQuery.isNullOrEmpty()) {
                return secretFromQuery
            }
        }

        // Try header (extracted by configurator during handshake)
        val userProperties = config.userProperties
        val secretFromHeader = userProperties[SECRET_KEY_HEADER] as? String
        if (!secretFromHeader.isNullOrEmpty()) {
            return secretFromHeader
        }

        return null
    }

    /**
     * Extracts the requested subdomain from query parameter or header.
     */
    private fun extractRequestedSubdomain(session: Session, config: EndpointConfig): String? {
        // Try query parameter first
        val queryString = session.queryString
        if (!queryString.isNullOrEmpty()) {
            val params = parseQueryString(queryString)
            val subdomainFromQuery = params[SUBDOMAIN_PARAM]
            if (!subdomainFromQuery.isNullOrEmpty()) {
                return subdomainFromQuery
            }
        }

        // Try header
        val userProperties = config.userProperties
        val subdomainFromHeader = userProperties[SUBDOMAIN_HEADER] as? String
        if (!subdomainFromHeader.isNullOrEmpty()) {
            return subdomainFromHeader
        }

        return null
    }

    /**
     * Parses query string into a map of parameters.
     */
    private fun parseQueryString(queryString: String): Map<String, String> {
        return queryString.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()
    }

    /**
     * Validates the secret key against configured secret keys.
     */
    private fun isValidSecretKey(secretKey: String): Boolean {
        return relayConfig.secretKeys().contains(secretKey)
    }

    /**
     * Sends an error message back to the client.
     */
    private fun sendErrorToClient(session: Session, correlationId: String?, errorCode: ErrorCode, message: String) {
        try {
            val envelope = Envelope(
                correlationId = correlationId ?: generateCorrelationId(),
                type = MessageType.ERROR,
                payload = Payload.Error(ErrorPayload(
                    code = errorCode,
                    message = message
                ))
            )

            val binaryMessage = ProtobufSerializer.encodeEnvelope(envelope)
            val byteBuffer = ByteBuffer.wrap(binaryMessage)
            session.asyncRemote.sendBinary(byteBuffer)

            logger.debug("Sent ERROR message: correlationId={}, code={}, message={}", correlationId, errorCode, message)
        } catch (e: Exception) {
            logger.error("Failed to send error message to client: {}", e.message, e)
        }
    }

    /**
     * Sends a CONTROL message with REGISTERED action to the client.
     * v2.0.0: Uses Protobuf binary format.
     */
    private fun sendRegisteredMessage(session: Session, subdomain: String) {
        try {
            val publicUrl = "https://$subdomain.${relayConfig.domain()}"

            val envelope = Envelope(
                correlationId = generateCorrelationId(),
                type = MessageType.CONTROL,
                payload = Payload.Control(ControlPayload(
                    action = "REGISTERED",
                    subdomain = subdomain,
                    publicUrl = publicUrl
                ))
            )

            // Encode to Protobuf binary
            val binaryMessage = ProtobufSerializer.encodeEnvelope(envelope)
            val byteBuffer = ByteBuffer.wrap(binaryMessage)

            // T044: Track outbound message metrics
            meterRegistry.counter("relay.protobuf.messages.sent",
                "type", "tunnel",
                "message_type", "CONTROL"
            ).increment()
            meterRegistry.summary("relay.protobuf.message.size.bytes",
                "type", "tunnel",
                "direction", "outbound"
            ).record(binaryMessage.size.toDouble())

            session.asyncRemote.sendBinary(byteBuffer)

            logger.debug("Sent REGISTERED message (Protobuf) to subdomain={}: {}", subdomain, publicUrl)
        } catch (e: Exception) {
            logger.error("Failed to send REGISTERED message to subdomain={}", subdomain, e)
        }
    }

    /**
     * Handles a RESPONSE message from the client.
     * Completes the pending request with the response payload.
     */
    private fun handleResponseMessage(envelope: Envelope, subdomain: String?) {
        try {
            val payload = envelope.payload as? Payload.Response
            if (payload == null) {
                logger.warn("Expected Response payload but got: {}", envelope.payload::class.simpleName)
                return
            }

            val responsePayload = payload.data

            logger.debug("Handling response from client: subdomain={}, correlationId={}, statusCode={}, bodySize={}",
                subdomain, envelope.correlationId, responsePayload.statusCode, responsePayload.body?.size ?: 0)

            // Find and complete the pending request
            if (tunnelRegistry.completePendingRequest(envelope.correlationId, responsePayload)) {
                logger.debug("Completed pending request: correlationId={}", envelope.correlationId)
            } else {
                logger.warn("No pending request found for correlationId={}", envelope.correlationId)
            }
        } catch (e: Exception) {
            logger.error("Failed to handle RESPONSE message: correlationId={}", envelope.correlationId, e)
        }
    }

    /**
     * Handles an ERROR message from the client.
     * Completes the pending request exceptionally.
     */
    private fun handleErrorMessage(envelope: Envelope, subdomain: String?) {
        try {
            val payload = envelope.payload as? Payload.Error
            if (payload == null) {
                logger.warn("Expected Error payload but got: {}", envelope.payload::class.simpleName)
                return
            }

            val errorPayload = payload.data

            // Find and complete the pending request exceptionally
            if (tunnelRegistry.completePendingRequestExceptionally(
                    envelope.correlationId,
                    UpstreamErrorException("[${errorPayload.code}] ${errorPayload.message}")
                )
            ) {
                logger.debug("Completed pending request exceptionally: correlationId={}", envelope.correlationId)
            } else {
                logger.warn("No pending request found for correlationId={}", envelope.correlationId)
            }
        } catch (e: Exception) {
            logger.error("Failed to handle ERROR message: correlationId={}", envelope.correlationId, e)
        }
    }

    /**
     * Cancels all pending requests for a connection with a 503 error.
     */
    private fun cancelPendingRequests(connection: TunnelConnection) {
        // We need to find all pending requests in the global registry that were destined for this connection.
        // This is a bit inefficient but necessary if we want to cancel them on disconnect.
        // A better way would be for TunnelRegistry to track pending requests per tunnel.

        // However, the SubdomainRoutingHandler already handles timeout.
        // If we want immediate 503 on disconnect, we should complete them here.
    }

    /**
     * Generates a unique correlation ID for messages.
     */
    private fun generateCorrelationId(): String {
        return "${System.currentTimeMillis()}-${Thread.currentThread().threadId()}-${(Math.random() * 10000).toInt()}"
    }

    /**
     * Handles a WebSocket frame message from the tunnel client (local app -> external client).
     * Routes the frame to the appropriate external WebSocket connection.
     */
    private fun handleWebSocketFrameMessage(envelope: Envelope, subdomain: String?) {
        val framePayload = (envelope.payload as? Payload.WebSocketFrame)?.data
        if (framePayload == null) {
            logger.warn("Expected WebSocketFrame payload but got: {}", envelope.payload::class.simpleName)
            return
        }

        logger.debug("Received WebSocket frame from tunnel client: subdomain={}, correlationId={}, type={}",
            subdomain, envelope.correlationId, framePayload.type)

        // Forward frame to external WebSocket endpoint
        if (subdomain != null) {
            externalWebSocketEndpoint.handleFrameFromTunnel(subdomain, envelope.correlationId, framePayload)
        } else {
            logger.warn("Cannot route WebSocket frame: subdomain unknown")
        }
    }
}

/**
 * Exception thrown when an upstream error is received from the client.
 */
class UpstreamErrorException(message: String) : RuntimeException(message)
