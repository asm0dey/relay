package org.relay.client

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.relay.client.command.ConnectionParameters
import org.relay.client.command.TunnelCommandInterface
import org.relay.client.command.TunnelCommandValidator
import org.relay.shared.protocol.*
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * T029: End-to-End CLI Integration Test
 *
 * Tests the complete CLI flow from argument parsing through connection configuration:
 * 1. CLI args parsing via Picocli
 * 2. Validation via TunnelCommandValidator
 * 3. ConnectionParameters building
 * 4. System properties configuration
 * 5. Protocol message serialization/deserialization
 *
 * This test verifies the full integration of all CLI components without
 * requiring an actual WebSocket server connection.
 */
class EndToEndTest {

    @BeforeEach
    fun clearSystemProperties() {
        System.clearProperty("relay.client.server-url")
        System.clearProperty("relay.client.local-url")
        System.clearProperty("relay.client.secret-key")
        System.clearProperty("relay.client.subdomain")
        System.clearProperty("quarkus.log.level")
    }

    @Test
    fun `end-to-end CLI flow with minimal arguments succeeds`() {
        // Given: Valid minimal CLI arguments
        val command = TestTunnelCommand()
        val args = arrayOf(
            "3000",
            "-s", "tun.example.com",
            "-k", "test-secret-key"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Command should succeed
        assertEquals(0, exitCode, "Command should execute successfully")

        // Verify ConnectionParameters are built correctly
        val params = command.getParameters()
        assertEquals(3000, params.port)
        assertEquals("tun.example.com", params.server)
        assertEquals("test-secret-key", params.secretKey)
        assertNull(params.subdomain, "Subdomain should be null for random assignment")
        assertEquals("http://localhost:3000", params.localUrl)
        assertEquals("wss://tun.example.com/ws?secret=test-secret-key", params.serverUrl)

        // Verify system properties were set for ClientConfig integration
        assertEquals("wss://tun.example.com/ws?secret=test-secret-key", System.getProperty("relay.client.server-url"))
        assertEquals("http://localhost:3000", System.getProperty("relay.client.local-url"))
        assertEquals("test-secret-key", System.getProperty("relay.client.secret-key"))
        assertNull(System.getProperty("relay.client.subdomain"))
    }

    @Test
    fun `end-to-end CLI flow with custom subdomain succeeds`() {
        // Given: CLI arguments with custom subdomain
        val command = TestTunnelCommand()
        val args = arrayOf(
            "8080",
            "-s", "tun.example.com",
            "-k", "my-secret",
            "-d", "myapp"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Command should succeed with subdomain
        assertEquals(0, exitCode, "Command should execute successfully")

        val params = command.getParameters()
        assertEquals(8080, params.port)
        assertEquals("tun.example.com", params.server)
        assertEquals("my-secret", params.secretKey)
        assertEquals("myapp", params.subdomain)
        assertEquals("http://localhost:8080", params.localUrl)
        assertEquals("wss://tun.example.com/ws?secret=my-secret&subdomain=myapp", params.serverUrl)

        // Verify subdomain system property is set
        assertEquals("myapp", System.getProperty("relay.client.subdomain"))
    }

    @Test
    fun `end-to-end CLI flow with insecure flag uses ws protocol`() {
        // Given: CLI arguments with --insecure flag
        val command = TestTunnelCommand()
        val args = arrayOf(
            "3000",
            "-s", "localhost:8080",
            "-k", "test-key",
            "--insecure"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Should use ws:// protocol
        assertEquals(0, exitCode)

        val params = command.getParameters()
        assertTrue(params.insecure)
        assertEquals("ws://localhost:8080/ws?secret=test-key", params.serverUrl,
            "Should use ws:// when insecure flag is set")
    }

    @Test
    fun `end-to-end CLI flow handles authentication parameters correctly`() {
        // Given: CLI arguments with authentication secret
        val command = TestTunnelCommand()
        val secretKey = "my-super-secret-key-12345"
        val args = arrayOf(
            "5000",
            "-s", "relay.example.com",
            "-k", secretKey
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Secret key should be properly configured
        assertEquals(0, exitCode)

        val params = command.getParameters()
        assertEquals(secretKey, params.secretKey)
        assertEquals(secretKey, System.getProperty("relay.client.secret-key"),
            "Secret key should be set in system properties for authentication")
    }

    @Test
    fun `end-to-end CLI flow with verbose flag configures logging`() {
        // Given: CLI arguments with verbose flag
        val command = TestTunnelCommand()
        val args = arrayOf(
            "3000",
            "-s", "tun.example.com",
            "-k", "test-key",
            "-v"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Logging should be configured to DEBUG
        assertEquals(0, exitCode)
        assertEquals("DEBUG", System.getProperty("quarkus.log.level"),
            "Verbose flag should set log level to DEBUG")
    }

    @Test
    fun `end-to-end CLI flow with quiet flag configures logging`() {
        // Given: CLI arguments with quiet flag
        val command = TestTunnelCommand()
        val args = arrayOf(
            "3000",
            "-s", "tun.example.com",
            "-k", "test-key",
            "-q"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Logging should be configured to ERROR
        assertEquals(0, exitCode)
        assertEquals("ERROR", System.getProperty("quarkus.log.level"),
            "Quiet flag should set log level to ERROR")
    }

    @Test
    fun `protocol message serialization works end-to-end`() {
        // Given: A REGISTERED control message from server
        val controlPayload = ControlPayload(
            action = ControlPayload.ACTION_REGISTERED,
            subdomain = "test-subdomain-123",
            publicUrl = "https://test-subdomain-123.example.com"
        )

        val envelope = Envelope(
            correlationId = "test-001",
            type = MessageType.CONTROL,
            payload = controlPayload.toJsonElement()
        )

        // When: Serialize to JSON
        val json = envelope.toJson()

        // Then: Should be able to deserialize back
        val deserializedEnvelope = json.toEnvelope()
        assertEquals(envelope.correlationId, deserializedEnvelope.correlationId)
        assertEquals(envelope.type, deserializedEnvelope.type)

        // Parse the control payload
        val deserializedControl = deserializedEnvelope.payload.toObject<ControlPayload>()

        assertEquals(ControlPayload.ACTION_REGISTERED, deserializedControl.action)
        assertEquals("test-subdomain-123", deserializedControl.subdomain)
        assertEquals("https://test-subdomain-123.example.com", deserializedControl.publicUrl)
    }

    @Test
    fun `validation errors prevent execution`() {
        // Given: Invalid CLI arguments (port out of range)
        val command = TestTunnelCommand()
        val args = arrayOf(
            "70000",  // Invalid port
            "-s", "tun.example.com",
            "-k", "test-key"
        )

        // When: Parse and execute command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Should fail validation
        assertEquals(1, exitCode, "Command should fail validation with invalid port")
    }

    @Test
    fun `missing required arguments fail gracefully`() {
        // Given: Missing server argument
        val command = TestTunnelCommand()
        val args = arrayOf(
            "3000",
            "-k", "test-key"
            // Missing -s/--server
        )

        // When: Parse command
        val exitCode = CommandLine(command).execute(*args)

        // Then: Should fail with Picocli error
        assertNotEquals(0, exitCode, "Command should fail with missing required argument")
    }

    @Test
    fun `all components integrate correctly from CLI to config`() {
        // Given: Complete set of CLI arguments
        val command = TestTunnelCommand()
        val args = arrayOf(
            "4000",
            "-s", "relay.example.com",
            "-k", "integration-test-key",
            "-d", "integration-test-subdomain",
            "--insecure",
            "-v"
        )

        // When: Execute full flow
        val exitCode = CommandLine(command).execute(*args)

        // Then: All components should work together
        assertEquals(0, exitCode, "Full integration should succeed")

        // Verify all system properties are correctly set
        assertEquals("ws://relay.example.com/ws?secret=integration-test-key&subdomain=integration-test-subdomain", System.getProperty("relay.client.server-url"),
            "Server URL should respect --insecure flag")

        assertEquals("http://localhost:4000", System.getProperty("relay.client.local-url"),
            "Local URL should be constructed from port")

        assertEquals("integration-test-key", System.getProperty("relay.client.secret-key"),
            "Secret key should be set for authentication")

        assertEquals("integration-test-subdomain", System.getProperty("relay.client.subdomain"),
            "Subdomain should be set when provided")

        assertEquals("DEBUG", System.getProperty("quarkus.log.level"),
            "Verbose flag should enable DEBUG logging")

        // Verify ConnectionParameters
        val params = command.getParameters()
        assertEquals(4000, params.port)
        assertEquals("relay.example.com", params.server)
        assertEquals("integration-test-key", params.secretKey)
        assertEquals("integration-test-subdomain", params.subdomain)
        assertTrue(params.insecure)
        assertEquals("ws://relay.example.com/ws?secret=integration-test-key&subdomain=integration-test-subdomain", params.serverUrl)
        assertEquals("http://localhost:4000", params.localUrl)
    }

    /**
     * Test double that simulates TunnelCommand behavior without starting Quarkus.
     */
    @CommandLine.Command(name = "test-relay-client")
    private class TestTunnelCommand : TunnelCommandInterface, Callable<Int> {
        @CommandLine.Parameters(index = "0", description = ["Local HTTP service port (1-65535)"])
        private var portField: Int = 0

        @CommandLine.Option(names = ["-s", "--server"], description = ["Tunnel server hostname"], required = true)
        private var serverField: String = ""

        @CommandLine.Option(names = ["-k", "--key"], description = ["Authentication secret key"], required = true)
        private var secretKeyField: String = ""

        @CommandLine.Option(names = ["-d", "--subdomain"], description = ["Request specific subdomain"])
        private var subdomainField: String? = null

        @CommandLine.Option(names = ["--insecure"], description = ["Use ws:// instead of wss://"])
        private var insecureField: Boolean = false

        @CommandLine.Option(names = ["-q", "--quiet"], description = ["Suppress non-error output"])
        private var quietField: Boolean = false

        @CommandLine.Option(names = ["-v", "--verbose"], description = ["Enable debug logging"])
        private var verboseField: Boolean = false

        private var parameters: ConnectionParameters? = null

        override val port: Int get() = portField
        override val server: String get() = serverField
        override val serverPort: Int? get() = null
        override val secretKey: String get() = secretKeyField
        override val subdomain: String? get() = subdomainField
        override val insecure: Boolean get() = insecureField
        override val quiet: Boolean get() = quietField
        override val verbose: Boolean get() = verboseField

        override fun call(): Int {
            // Validate CLI arguments
            val validationErrors = TunnelCommandValidator.validate(port, server, secretKey, subdomain)

            if (validationErrors.isNotEmpty()) {
                validationErrors.forEach { System.err.println("Error: $it") }
                return 1
            }

            // Build ConnectionParameters from parsed CLI args
            val params = ConnectionParameters.fromCommand(this)
            this.parameters = params

            // Configure system properties for ClientConfig (simulating RelayClientMain)
            System.setProperty("relay.client.server-url", params.serverUrl)
            System.setProperty("relay.client.local-url", params.localUrl)
            System.setProperty("relay.client.secret-key", params.secretKey)
            params.subdomain?.let {
                System.setProperty("relay.client.subdomain", it)
            }

            // Configure logging based on verbosity flags
            if (quiet) {
                System.setProperty("quarkus.log.level", "ERROR")
            } else if (verbose) {
                System.setProperty("quarkus.log.level", "DEBUG")
            }

            // Success - don't start Quarkus in tests
            return 0
        }

        fun getParameters(): ConnectionParameters {
            return parameters ?: throw IllegalStateException("Parameters not initialized - call() not executed")
        }
    }
}
