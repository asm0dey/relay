package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TunnelCommandValidationTest {

    // TS-010: Valid ports (1, 8080, 65535) are accepted
    @ParameterizedTest
    @ValueSource(ints = [1, 80, 443, 3000, 8080, 65535])
    fun `valid ports are accepted`(port: Int) {
        val errors = TunnelCommandValidator.validate(port = port, server = "localhost", secretKey = "secret")
        assertTrue(errors.none { it.contains("port", ignoreCase = true) }, "Port $port should be valid")
    }

    // TS-011: Invalid ports (0, -1, 65536) are rejected with error message
    @ParameterizedTest
    @ValueSource(ints = [0, -1, 65536])
    fun `invalid ports are rejected`(port: Int) {
        val errors = TunnelCommandValidator.validate(port = port, server = "localhost", secretKey = "secret")
        assertTrue(errors.any { it.contains("port", ignoreCase = true) }, "Port $port should be invalid")
    }

    // TS-012: Non-numeric port values throw exception or are rejected
    @ParameterizedTest
    @ValueSource(strings = ["abc", "8080x", "not-a-number", "12.5"])
    fun `non-numeric port values are rejected by picocli`(portString: String) {
        val command = TunnelCommand()
        val cli = CommandLine(command)

        // Capture stderr to suppress error output during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = cli.execute(
                portString,
                "--server", "relay.example.com",
                "--key", "test-secret-key"
            )

            assertNotEquals(0, exitCode, "Non-numeric port '$portString' should be rejected with non-zero exit code")
        } finally {
            System.setErr(originalErr)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["myapp", "a", "z9-8-7", "test-123"])
    fun `valid subdomains are accepted`(subdomain: String) {
        val errors = TunnelCommandValidator.validate(
            port = 3000,
            server = "localhost",
            secretKey = "secret",
            subdomain = subdomain
        )
        assertTrue(errors.none { it.contains("subdomain", ignoreCase = true) }, "Subdomain '$subdomain' should be valid")
    }

    // TS-013: Invalid subdomain formats (-start, end-, Capital) are rejected
    @ParameterizedTest
    @ValueSource(strings = ["-invalid", "invalid-", "Invalid", "under_score"])
    fun `invalid subdomains are rejected`(subdomain: String) {
        val errors = TunnelCommandValidator.validate(
            port = 3000,
            server = "localhost",
            secretKey = "secret",
            subdomain = subdomain
        )
        assertTrue(errors.any { it.contains("subdomain", ignoreCase = true) }, "Subdomain '$subdomain' should be invalid")
    }

    @Test
    fun `null subdomain is valid`() {
        val errors = TunnelCommandValidator.validate(
            port = 3000,
            server = "localhost",
            secretKey = "secret",
            subdomain = null
        )
        assertTrue(errors.none { it.contains("subdomain", ignoreCase = true) })
    }

    @Test
    fun `empty server is rejected`() {
        val errors = TunnelCommandValidator.validate(port = 3000, server = "", secretKey = "secret")
        assertTrue(errors.any { it.contains("server", ignoreCase = true) })
    }

    @Test
    fun `empty secret key is rejected`() {
        val errors = TunnelCommandValidator.validate(port = 3000, server = "localhost", secretKey = "")
        assertTrue(errors.any { it.contains("key", ignoreCase = true) || it.contains("secret", ignoreCase = true) })
    }

    @Test
    fun `valid command has no errors`() {
        val errors = TunnelCommandValidator.validate(port = 3000, server = "tun.example.com", secretKey = "secret")
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }
}
