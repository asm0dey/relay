package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for TunnelCommand CLI functionality.
 * Tests TS-001, TS-002, TS-003 scenarios.
 */
class TunnelCommandIntegrationTest {

    @Test
    fun `minimal arguments create valid connection parameters`() {
        // Simulates: client 3000 -s tun.example.com -k my-secret
        val command = createCommand(
            port = 3000,
            server = "tun.example.com",
            secretKey = "my-secret"
        )
        
        val params = ConnectionParameters.fromCommand(command)
        
        assertEquals(3000, params.port)
        assertEquals("tun.example.com", params.server)
        assertEquals("my-secret", params.secretKey)
        assertEquals("http://localhost:3000", params.localUrl)
        assertEquals("wss://tun.example.com/ws", params.serverUrl)
        assertNull(params.subdomain) // Should be null for random assignment
    }

    @Test
    fun `custom subdomain creates valid connection parameters`() {
        // Simulates: client 8080 -s tun.example.com -d myapp -k secret
        val command = createCommand(
            port = 8080,
            server = "tun.example.com",
            secretKey = "secret",
            subdomain = "myapp"
        )
        
        val params = ConnectionParameters.fromCommand(command)
        
        assertEquals(8080, params.port)
        assertEquals("myapp", params.subdomain)
        assertEquals("http://localhost:8080", params.localUrl)
        // Public URL would be: https://myapp.tun.example.com
    }

    @Test
    fun `insecure flag uses ws protocol`() {
        // Simulates: client 3000 -s localhost:8080 -k secret --insecure
        val command = createCommand(
            port = 3000,
            server = "localhost:8080",
            secretKey = "secret",
            insecure = true
        )
        
        val params = ConnectionParameters.fromCommand(command)
        
        assertEquals("ws://localhost:8080/ws", params.serverUrl)
    }

    @Test
    fun `validation accepts valid command`() {
        val errors = TunnelCommandValidator.validate(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validation rejects invalid port`() {
        val errors = TunnelCommandValidator.validate(
            port = 70000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertTrue(errors.any { it.contains("port", ignoreCase = true) })
    }

    @Test
    fun `validation rejects invalid subdomain`() {
        val errors = TunnelCommandValidator.validate(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            subdomain = "-invalid"
        )
        assertTrue(errors.any { it.contains("subdomain", ignoreCase = true) })
    }

    @Test
    fun `quiet flag sets ERROR log level`() {
        val command = createCommand(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            quiet = true
        )
        
        val params = ConnectionParameters.fromCommand(command)
        assertEquals(LogLevel.ERROR, params.logLevel)
    }

    @Test
    fun `verbose flag sets DEBUG log level`() {
        val command = createCommand(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            verbose = true
        )
        
        val params = ConnectionParameters.fromCommand(command)
        assertEquals(LogLevel.DEBUG, params.logLevel)
    }

    private fun createCommand(
        port: Int,
        server: String,
        secretKey: String,
        subdomain: String? = null,
        insecure: Boolean = false,
        quiet: Boolean = false,
        verbose: Boolean = false
    ): TunnelCommandInterface {
        return object : TunnelCommandInterface {
            override val port: Int = port
            override val server: String = server
            override val serverPort: Int? = null
            override val secretKey: String = secretKey
            override val subdomain: String? = subdomain
            override val insecure: Boolean = insecure
            override val quiet: Boolean = quiet
            override val verbose: Boolean = verbose
        }
    }
}
