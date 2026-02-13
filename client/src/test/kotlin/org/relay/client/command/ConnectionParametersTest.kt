package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConnectionParametersTest {

    @Test
    fun `local URL is constructed from port`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertEquals("http://localhost:3000", params.localUrl)
    }

    @Test
    fun `local URL uses provided port`() {
        val params = ConnectionParameters(
            port = 8080,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertEquals("http://localhost:8080", params.localUrl)
    }

    @Test
    fun `server URL uses wss protocol by default`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertEquals("wss://tun.example.com/ws", params.serverUrl)
    }

    @Test
    fun `server URL includes ws path`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "localhost:8080",
            secretKey = "secret"
        )
        assertEquals("wss://localhost:8080/ws", params.serverUrl)
    }

    @Test
    fun `insecure flag uses ws protocol`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "localhost:8080",
            secretKey = "secret",
            insecure = true
        )
        assertEquals("ws://localhost:8080/ws", params.serverUrl)
    }

    @Test
    fun `insecure flag does not affect local URL`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "localhost:8080",
            secretKey = "secret",
            insecure = true
        )
        assertEquals("http://localhost:3000", params.localUrl)
    }

    @Test
    fun `subdomain is stored when provided`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            subdomain = "myapp"
        )
        assertEquals("myapp", params.subdomain)
    }

    @Test
    fun `subdomain is null when not provided`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertNull(params.subdomain)
    }

    @Test
    fun `log level defaults to INFO`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )
        assertEquals(LogLevel.INFO, params.logLevel)
    }

    @Test
    fun `log level can be set to ERROR`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            logLevel = LogLevel.ERROR
        )
        assertEquals(LogLevel.ERROR, params.logLevel)
    }

    @Test
    fun `log level can be set to DEBUG`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret",
            logLevel = LogLevel.DEBUG
        )
        assertEquals(LogLevel.DEBUG, params.logLevel)
    }

    @Test
    fun `secret key is stored`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "my-secret-key"
        )
        assertEquals("my-secret-key", params.secretKey)
    }

    @Test
    fun `fromCommand creates parameters with all fields`() {
        val command = createTestCommand(
            port = 8080,
            server = "tun.example.com",
            secretKey = "secret",
            subdomain = "myapp",
            insecure = true,
            quiet = false,
            verbose = true
        )
        
        val params = ConnectionParameters.fromCommand(command)
        
        assertEquals("http://localhost:8080", params.localUrl)
        assertEquals("ws://tun.example.com/ws", params.serverUrl)
        assertEquals("secret", params.secretKey)
        assertEquals("myapp", params.subdomain)
        assertEquals(LogLevel.DEBUG, params.logLevel)
    }

    @Test
    fun `fromCommand handles minimal command`() {
        val command = createTestCommand(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )

        val params = ConnectionParameters.fromCommand(command)

        assertEquals("http://localhost:3000", params.localUrl)
        assertEquals("wss://tun.example.com/ws", params.serverUrl)
        assertNull(params.subdomain)
        assertEquals(LogLevel.INFO, params.logLevel)
    }

    @Test
    fun `TS-014 - local URL construction from port 3000`() {
        // Given a local port of 3000
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret123"
        )

        // When constructing the local URL
        val localUrl = params.localUrl

        // Then it should be http://localhost:3000
        assertEquals("http://localhost:3000", localUrl)
    }

    @Test
    fun `TS-015 - server WebSocket URL with wss protocol`() {
        // Given a server hostname and secure connection (default)
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret123",
            insecure = false
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should be wss://tun.example.com/ws
        assertEquals("wss://tun.example.com/ws", serverUrl)
    }

    @Test
    fun `TS-016 - server WebSocket URL with ws when insecure is true`() {
        // Given a server hostname and insecure connection
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret123",
            insecure = true
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should be ws://tun.example.com/ws
        assertEquals("ws://tun.example.com/ws", serverUrl)
    }

    @Test
    fun `stripProtocol removes wss prefix from hostname`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "wss://tun.example.com",
            secretKey = "secret"
        )

        assertEquals("tun.example.com", params.stripProtocol("wss://tun.example.com"))
    }

    @Test
    fun `stripProtocol removes ws prefix from hostname`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "ws://tun.example.com",
            secretKey = "secret"
        )

        assertEquals("tun.example.com", params.stripProtocol("ws://tun.example.com"))
    }

    @Test
    fun `stripProtocol removes https prefix from hostname`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "https://tun.example.com",
            secretKey = "secret"
        )

        assertEquals("tun.example.com", params.stripProtocol("https://tun.example.com"))
    }

    @Test
    fun `stripProtocol removes http prefix from hostname`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "http://tun.example.com",
            secretKey = "secret"
        )

        assertEquals("tun.example.com", params.stripProtocol("http://tun.example.com"))
    }

    @Test
    fun `stripProtocol returns hostname unchanged when no prefix`() {
        val params = ConnectionParameters(
            port = 3000,
            server = "tun.example.com",
            secretKey = "secret"
        )

        assertEquals("tun.example.com", params.stripProtocol("tun.example.com"))
    }

    @Test
    fun `serverUrl strips wss prefix from hostname`() {
        // Given a hostname with wss:// prefix
        val params = ConnectionParameters(
            port = 3000,
            server = "wss://tun.example.com",
            secretKey = "secret",
            insecure = false
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should strip the prefix and add wss://
        assertEquals("wss://tun.example.com/ws", serverUrl)
    }

    @Test
    fun `serverUrl strips ws prefix from hostname with insecure`() {
        // Given a hostname with ws:// prefix and insecure mode
        val params = ConnectionParameters(
            port = 3000,
            server = "ws://tun.example.com",
            secretKey = "secret",
            insecure = true
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should strip the prefix and add ws://
        assertEquals("ws://tun.example.com/ws", serverUrl)
    }

    @Test
    fun `serverUrl strips https prefix from hostname`() {
        // Given a hostname with https:// prefix
        val params = ConnectionParameters(
            port = 3000,
            server = "https://tun.example.com",
            secretKey = "secret",
            insecure = false
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should strip the prefix and add wss://
        assertEquals("wss://tun.example.com/ws", serverUrl)
    }

    @Test
    fun `serverUrl strips http prefix from hostname`() {
        // Given a hostname with http:// prefix
        val params = ConnectionParameters(
            port = 3000,
            server = "http://tun.example.com",
            secretKey = "secret",
            insecure = false
        )

        // When constructing the server URL
        val serverUrl = params.serverUrl

        // Then it should strip the prefix and add wss://
        assertEquals("wss://tun.example.com/ws", serverUrl)
    }

    private fun createTestCommand(
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
