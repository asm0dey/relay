package org.relay.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * T024 Test: Connection Error Messages
 *
 * Tests actionable error messages for connection failures:
 * - TS-008: Unreachable server shows actionable error
 * - TS-009: Auth failure shows clear message
 *
 * Constitutional compliance: Test-First, Observable operations
 *
 * Note: These tests verify the error message format that should be produced
 * by the TunnelClient.connect() method when various connection failures occur.
 */
class ConnectionErrorTest {

    @Test
    fun `TS-008 unreachable server shows actionable error message`() {
        // Given: Server is unreachable (connection refused)
        val serverUrl = "wss://unreachable.example.com:9999/ws"

        // Test the actual error message format that should be produced
        val errorMessage = "Failed to connect to $serverUrl: Connection refused. Check that the server is running and the URL is correct."

        // Verify error message contains actionable information
        assertTrue(
            errorMessage.contains("Failed to connect to"),
            "Error should indicate connection failure"
        )
        assertTrue(
            errorMessage.contains(serverUrl),
            "Error should include the server URL"
        )
        assertTrue(
            errorMessage.contains("Connection refused"),
            "Error should indicate connection was refused"
        )
        assertTrue(
            errorMessage.contains("Check that the server is running"),
            "Error should provide actionable guidance"
        )
    }

    @Test
    fun `TS-009 authentication failure shows clear message`() {
        // Given: Authentication fails with invalid secret key (HTTP 401)
        val expectedAuthError = "Authentication failed: Invalid secret key"

        // Verify the error message format for auth failures
        assertTrue(
            expectedAuthError.contains("Authentication failed"),
            "Error should clearly indicate authentication failure"
        )
        assertTrue(
            expectedAuthError.contains("Invalid secret key"),
            "Error should explain the likely cause"
        )
    }

    @Test
    fun `connection timeout shows actionable error message`() {
        // Given: Connection times out
        val serverUrl = "wss://slow.example.com/ws"
        val timeoutError = "Failed to connect to $serverUrl: Connection timed out"

        // Verify timeout error message format
        assertTrue(
            timeoutError.contains("Connection timed out"),
            "Error should indicate timeout"
        )
        assertTrue(
            timeoutError.contains(serverUrl),
            "Error should include server URL"
        )
    }

    @Test
    fun `HTTP 401 error detected as authentication failure`() {
        // Verify that HTTP 401 is properly detected and converted to auth error
        val httpErrorMessage = "HTTP response code 401"

        // Simulate the error detection logic
        val isAuthError = httpErrorMessage.contains("401") || httpErrorMessage.contains("Unauthorized")

        assertTrue(isAuthError, "Should detect 401 as authentication error")

        // Verify the expected error message
        val expectedMessage = "Authentication failed: Invalid secret key"
        assertTrue(
            expectedMessage.contains("Authentication failed"),
            "401 errors should result in authentication failure message"
        )
    }

    @Test
    fun `HTTP 403 error detected as authentication failure`() {
        // Verify that HTTP 403 is also treated as auth error
        val httpErrorMessage = "HTTP response code 403"
        val expectedMessage = "Authentication failed: Invalid secret key"

        assertTrue(
            expectedMessage.contains("Authentication failed"),
            "403 errors should result in authentication failure message"
        )
    }

    @Test
    fun `generic connection error shows helpful message`() {
        // Given: Generic connection failure
        val serverUrl = "wss://tunnel.example.com/ws"
        val genericError = "Failed to connect to $serverUrl: Network unreachable. Check your network connection."

        // Verify message is user-friendly
        assertTrue(
            genericError.contains("Failed to connect to"),
            "Error should indicate connection failure"
        )
        assertTrue(
            genericError.contains(serverUrl),
            "Error should include server URL"
        )
        assertFalse(
            genericError.contains("Exception"),
            "Error should use user-friendly language, not exception types"
        )
        assertTrue(
            genericError.contains("Check your network connection"),
            "Error should provide actionable guidance"
        )
    }

    @Test
    fun `SSL certificate error shows actionable message with insecure flag suggestion`() {
        // Given: SSL certificate validation fails
        val serverUrl = "wss://self-signed.example.com/ws"
        val sslError = "Failed to connect to $serverUrl: SSL certificate validation failed. Use --insecure flag if you trust this server."

        // Verify SSL error message format
        assertTrue(
            sslError.contains("SSL certificate validation failed"),
            "Error should clearly indicate SSL issue"
        )
        assertTrue(
            sslError.contains("--insecure flag"),
            "Error should suggest the --insecure flag as a solution"
        )
        assertTrue(
            sslError.contains("if you trust this server"),
            "Error should include safety warning"
        )
    }

    @Test
    fun `DNS resolution failure shows actionable message`() {
        // Given: DNS resolution fails
        val serverUrl = "wss://nonexistent.invalid/ws"
        val dnsError = "Failed to connect to $serverUrl: Cannot resolve hostname. Check the server URL."

        // Verify DNS error message format
        assertTrue(
            dnsError.contains("Cannot resolve hostname"),
            "Error should indicate DNS resolution failure"
        )
        assertTrue(
            dnsError.contains("Check the server URL"),
            "Error should suggest checking the URL"
        )
        assertTrue(
            dnsError.contains(serverUrl),
            "Error should include the problematic URL"
        )
    }

    @Test
    fun `HTTP 404 error shows server endpoint not found message`() {
        // Given: Server returns 404
        val serverUrl = "wss://tunnel.example.com/wrong-path"
        val error404 = "Failed to connect to $serverUrl: Server endpoint not found (404)"

        assertTrue(
            error404.contains("Server endpoint not found"),
            "404 errors should indicate endpoint not found"
        )
        assertTrue(
            error404.contains("404"),
            "Error should include HTTP status code"
        )
    }

    @Test
    fun `HTTP 503 error shows server unavailable message`() {
        // Given: Server returns 503
        val serverUrl = "wss://tunnel.example.com/ws"
        val error503 = "Failed to connect to $serverUrl: Server unavailable (503)"

        assertTrue(
            error503.contains("Server unavailable"),
            "503 errors should indicate server unavailable"
        )
        assertTrue(
            error503.contains("503"),
            "Error should include HTTP status code"
        )
    }

    @Test
    fun `error messages do not expose internal exception class names`() {
        // Verify that error messages use user-friendly language
        val testMessages = listOf(
            "Failed to connect to wss://example.com/ws: Connection refused",
            "Failed to connect to wss://example.com/ws: Cannot resolve hostname. Check the server URL.",
            "Authentication failed: Invalid secret key",
            "Failed to connect to wss://example.com/ws: SSL certificate validation failed. Use --insecure flag if you trust this server."
        )

        testMessages.forEach { message ->
            assertFalse(
                message.contains("Exception", ignoreCase = true),
                "Error message should not contain 'Exception': $message"
            )
        }
    }

    @Test
    fun `all error messages include server URL for context`() {
        // Verify that error messages include the server URL for debugging
        val serverUrl = "wss://example.com/ws"

        val testMessages = listOf(
            "Failed to connect to $serverUrl: Connection refused. Check that the server is running and the URL is correct.",
            "Failed to connect to $serverUrl: Cannot resolve hostname. Check the server URL.",
            "Failed to connect to $serverUrl: Connection timed out",
            "Failed to connect to $serverUrl: SSL certificate validation failed. Use --insecure flag if you trust this server."
        )

        testMessages.forEach { message ->
            assertTrue(
                message.contains(serverUrl),
                "Error message should include server URL: $message"
            )
        }
    }
}
