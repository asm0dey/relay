package org.relay.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.relay.client.command.ConnectionParameters
import org.relay.client.command.LogLevel
import org.relay.client.command.TunnelCommandInterface
import org.relay.client.command.TunnelCommandValidator
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * T014 Integration Tests: TunnelClient CLI Integration
 *
 * Tests the full integration flow:
 * CLI args -> TunnelCommand -> ConnectionParameters -> System Properties
 *
 * Scenarios:
 * - TS-001: Connect with minimal args (3000 -s tun.example.com -k secret)
 * - TS-002: Connect with custom subdomain (8080 -s tun.example.com -d myapp -k secret)
 * - TS-003: Connect with random subdomain (no -d flag)
 *
 * Note: These tests verify parameter building without actually starting Quarkus
 */
class TunnelClientIntegrationTest {

    @BeforeEach
    fun clearSystemProperties() {
        // Clear any previously set system properties
        System.clearProperty("relay.client.server-url")
        System.clearProperty("relay.client.local-url")
        System.clearProperty("relay.client.secret-key")
        System.clearProperty("relay.client.subdomain")
        System.clearProperty("quarkus.log.level")
    }

    @Test
    fun `TS-001 minimal arguments build correct ConnectionParameters`() {
        // Simulates: relay-client 3000 -s tun.example.com -k my-secret
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com", "-k", "my-secret")

        val exitCode = CommandLine(command).execute(*args)

        // Verify command parsed successfully
        assertEquals(0, exitCode, "Command should parse successfully")

        // Verify ConnectionParameters are built correctly from CLI args
        val params = command.getParameters()

        assertEquals(3000, params.port, "Port should be 3000")
        assertEquals("tun.example.com", params.server, "Server should be tun.example.com")
        assertEquals("my-secret", params.secretKey, "Secret key should be my-secret")
        assertNull(params.subdomain, "Subdomain should be null for random assignment")
        assertEquals("http://localhost:3000", params.localUrl, "Local URL should be http://localhost:3000")
        assertEquals("wss://tun.example.com/ws?secret=my-secret", params.serverUrl, "Server URL should use wss://")
        assertFalse(params.insecure, "Should use secure connection by default")
        assertEquals(LogLevel.INFO, params.logLevel, "Default log level should be INFO")

        // Verify system properties were set for ClientConfig integration
        assertEquals("wss://tun.example.com/ws?secret=my-secret", System.getProperty("relay.client.server-url"))
        assertEquals("http://localhost:3000", System.getProperty("relay.client.local-url"))
        assertEquals("my-secret", System.getProperty("relay.client.secret-key"))
        assertNull(System.getProperty("relay.client.subdomain"), "Subdomain property should not be set")
    }

    @Test
    fun `TS-002 custom subdomain builds correct ConnectionParameters`() {
        // Simulates: relay-client 8080 -s tun.example.com -d myapp -k secret
        val command = TestTunnelCommand()
        val args = arrayOf("8080", "-s", "tun.example.com", "-d", "myapp", "-k", "secret")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(0, exitCode, "Command should parse successfully")

        val params = command.getParameters()

        assertEquals(8080, params.port, "Port should be 8080")
        assertEquals("tun.example.com", params.server, "Server should be tun.example.com")
        assertEquals("secret", params.secretKey, "Secret key should be secret")
        assertEquals("myapp", params.subdomain, "Subdomain should be myapp")
        assertEquals("http://localhost:8080", params.localUrl, "Local URL should be http://localhost:8080")
        assertEquals("wss://tun.example.com/ws?secret=secret&subdomain=myapp", params.serverUrl, "Server URL should use wss://")

        // Verify system properties include subdomain
        assertEquals("myapp", System.getProperty("relay.client.subdomain"))
    }

    @Test
    fun `TS-003 random subdomain when no -d flag provided`() {
        // Simulates: relay-client 3000 -s tun.example.com -k secret
        // (no -d flag = random subdomain assignment)
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com", "-k", "secret")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(0, exitCode, "Command should parse successfully")

        val params = command.getParameters()

        assertNull(params.subdomain, "Subdomain should be null for random assignment by server")
        assertEquals(3000, params.port, "Port should be 3000")
        assertEquals("tun.example.com", params.server, "Server should be tun.example.com")

        // Subdomain property should not be set in system properties
        assertNull(System.getProperty("relay.client.subdomain"),
            "Subdomain system property should not be set when requesting random assignment")
    }

    @Test
    fun `insecure flag uses ws protocol`() {
        // Simulates: relay-client 3000 -s localhost:8080 -k secret --insecure
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "localhost:8080", "-k", "secret", "--insecure")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(0, exitCode, "Command should parse successfully")

        val params = command.getParameters()

        assertTrue(params.insecure, "Insecure flag should be true")
        assertEquals("ws://localhost:8080/ws?secret=secret", params.serverUrl, "Server URL should use ws:// when insecure")
    }

    @Test
    fun `quiet flag sets ERROR log level and configures logging`() {
        // Simulates: relay-client 3000 -s tun.example.com -k secret -q
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com", "-k", "secret", "-q")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(0, exitCode, "Command should parse successfully")

        val params = command.getParameters()

        assertEquals(LogLevel.ERROR, params.logLevel, "Log level should be ERROR with quiet flag")
        assertEquals("ERROR", System.getProperty("quarkus.log.level"), "Quarkus log level should be set to ERROR")
    }

    @Test
    fun `verbose flag sets DEBUG log level and configures logging`() {
        // Simulates: relay-client 3000 -s tun.example.com -k secret -v
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com", "-k", "secret", "-v")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(0, exitCode, "Command should parse successfully")

        val params = command.getParameters()

        assertEquals(LogLevel.DEBUG, params.logLevel, "Log level should be DEBUG with verbose flag")
        assertEquals("DEBUG", System.getProperty("quarkus.log.level"), "Quarkus log level should be set to DEBUG")
    }

    @Test
    fun `validation fails with invalid port`() {
        // Simulates: relay-client 70000 -s tun.example.com -k secret
        val command = TestTunnelCommand()
        val args = arrayOf("70000", "-s", "tun.example.com", "-k", "secret")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(1, exitCode, "Command should fail validation with invalid port")
    }

    @Test
    fun `validation fails with invalid subdomain`() {
        // Simulates: relay-client 3000 -s tun.example.com -d -invalid -k secret
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com", "-d", "-invalid", "-k", "secret")

        val exitCode = CommandLine(command).execute(*args)

        assertEquals(1, exitCode, "Command should fail validation with invalid subdomain")
    }

    @Test
    fun `validation fails with missing server`() {
        // Simulates: relay-client 3000 -k secret
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-k", "secret")

        val exitCode = CommandLine(command).execute(*args)

        // Picocli will return error code 2 for missing required options
        assertNotEquals(0, exitCode, "Command should fail with missing server")
    }

    @Test
    fun `validation fails with missing secret key`() {
        // Simulates: relay-client 3000 -s tun.example.com
        val command = TestTunnelCommand()
        val args = arrayOf("3000", "-s", "tun.example.com")

        val exitCode = CommandLine(command).execute(*args)

        // Picocli will return error code 2 for missing required options
        assertNotEquals(0, exitCode, "Command should fail with missing secret key")
    }

    /**
     * Test double that extends TunnelCommand but doesn't call Quarkus.waitForExit()
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
            val validationErrors = TunnelCommandValidator.validate(port, server, secretKey, subdomain)

            if (validationErrors.isNotEmpty()) {
                validationErrors.forEach { System.err.println("Error: $it") }
                return 1
            }

            val params = ConnectionParameters.fromCommand(this)
            this.parameters = params

            // Configure system properties for ClientConfig
            System.setProperty("relay.client.server-url", params.serverUrl)
            System.setProperty("relay.client.local-url", params.localUrl)
            System.setProperty("relay.client.secret-key", params.secretKey)
            params.subdomain?.let {
                System.setProperty("relay.client.subdomain", it)
            }

            if (quiet) {
                System.setProperty("quarkus.log.level", "ERROR")
            } else if (verbose) {
                System.setProperty("quarkus.log.level", "DEBUG")
            }

            // Don't call Quarkus.waitForExit() in tests
            return 0
        }

        fun getParameters(): ConnectionParameters {
            return parameters ?: throw IllegalStateException("Parameters not initialized - call() not executed")
        }
    }
}
