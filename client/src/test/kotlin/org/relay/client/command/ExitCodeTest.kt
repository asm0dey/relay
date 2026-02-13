package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Exit Code Tests for TunnelCommand
 *
 * Tests TS-021 and TS-022 from test-specs.md:
 * - TS-021: Exit code 0 on success (valid arguments and successful connection)
 * - TS-022: Exit code 1 on invalid arguments (validation failures)
 *
 * Exit code mapping per spec:
 * - 0: Success
 * - 1: Invalid arguments or configuration (validation errors)
 * - 2: Connection failed
 * - 3: Authentication failed
 * - 130: Interrupted (SIGINT)
 *
 * Note: These tests verify exit code logic by testing the validation layer directly
 * and by using a command wrapper that doesn't invoke Quarkus.waitForExit().
 */
class ExitCodeTest {

    /**
     * Helper: Test command that validates but doesn't block on Quarkus
     */
    private class TestableCommand : TunnelCommandInterface {
        override var port: Int = 0
        override var server: String = ""
        override var serverPort: Int? = null
        override var secretKey: String = ""
        override var subdomain: String? = null
        override var insecure: Boolean = false
        override var quiet: Boolean = false
        override var verbose: Boolean = false

        fun validate(): Int {
            val validationErrors = TunnelCommandValidator.validate(port, server, secretKey, subdomain)

            if (validationErrors.isNotEmpty()) {
                validationErrors.forEach { System.err.println("Error: $it") }
                return 1
            }

            return 0
        }
    }

    /**
     * TS-021: Exit code 0 on success
     *
     * Given: valid arguments and successful connection
     * When: client completes normally
     * Then: exit code is 0
     *
     * Note: This test validates that the validation logic returns 0 for valid inputs.
     * We test the validation layer directly to avoid blocking on Quarkus.waitForExit().
     */
    @Test
    fun `valid arguments return exit code 0`() {
        val command = TestableCommand()
        command.port = 3000
        command.server = "tun.example.com"
        command.secretKey = "test-secret-key"

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(0, exitCode, "Valid arguments should return exit code 0")

            // Verify no error messages were printed
            assertTrue(errContent.toString().isEmpty(), "No errors should be printed for valid input")
        } finally {
            System.setErr(originalErr)
        }
    }

    /**
     * TS-022: Exit code 1 on invalid arguments - Invalid Port
     *
     * Given: invalid port (out of range 1-65535)
     * When: validation fails
     * Then: exit code is 1
     */
    @ParameterizedTest
    @ValueSource(ints = [0, -1, 65536, 99999])
    fun `invalid port returns exit code 1`(invalidPort: Int) {
        val command = TestableCommand()
        command.port = invalidPort
        command.server = "tun.example.com"
        command.secretKey = "test-secret-key"

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(1, exitCode, "Invalid port $invalidPort should return exit code 1")

            // Verify error message mentions port validation
            val errorOutput = errContent.toString()
            assertTrue(
                errorOutput.contains("port", ignoreCase = true),
                "Error message should mention port validation"
            )
        } finally {
            System.setErr(originalErr)
        }
    }

    /**
     * TS-022: Exit code 1 on invalid arguments - Invalid Subdomain
     *
     * Given: invalid subdomain format
     * When: validation fails
     * Then: exit code is 1
     */
    @ParameterizedTest
    @ValueSource(strings = ["-invalid", "invalid-", "Invalid", "under_score"])
    fun `invalid subdomain returns exit code 1`(invalidSubdomain: String) {
        val command = TestableCommand()
        command.port = 3000
        command.server = "tun.example.com"
        command.secretKey = "test-secret-key"
        command.subdomain = invalidSubdomain

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(1, exitCode, "Invalid subdomain '$invalidSubdomain' should return exit code 1")

            // Verify error message mentions subdomain validation
            val errorOutput = errContent.toString()
            assertTrue(
                errorOutput.contains("subdomain", ignoreCase = true),
                "Error message should mention subdomain validation"
            )
        } finally {
            System.setErr(originalErr)
        }
    }

    /**
     * TS-022: Exit code 1 on invalid arguments - Missing Required Args
     *
     * Given: missing required flags (--server or --key)
     * When: validation fails
     * Then: exit code is 1
     */
    @Test
    fun `missing server flag returns exit code 1`() {
        val command = TestableCommand()
        command.port = 3000
        command.server = "" // Empty server (missing)
        command.secretKey = "test-secret-key"

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(1, exitCode, "Missing server should return exit code 1")

            // Verify error message mentions server
            val errorOutput = errContent.toString()
            assertTrue(
                errorOutput.contains("server", ignoreCase = true),
                "Error message should mention server validation"
            )
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `missing key flag returns exit code 1`() {
        val command = TestableCommand()
        command.port = 3000
        command.server = "tun.example.com"
        command.secretKey = "" // Empty key (missing)

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(1, exitCode, "Missing secret key should return exit code 1")

            // Verify error message mentions key
            val errorOutput = errContent.toString()
            assertTrue(
                errorOutput.contains("key", ignoreCase = true) || errorOutput.contains("secret", ignoreCase = true),
                "Error message should mention secret key validation"
            )
        } finally {
            System.setErr(originalErr)
        }
    }

    /**
     * Additional test: Verify successful validation with optional subdomain
     */
    @Test
    fun `valid arguments with custom subdomain return exit code 0`() {
        val command = TestableCommand()
        command.port = 8080
        command.server = "tun.example.com"
        command.secretKey = "test-secret-key"
        command.subdomain = "myapp"

        // Suppress stdout/stderr during test
        val errContent = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errContent))

        try {
            val exitCode = command.validate()
            assertEquals(0, exitCode, "Valid arguments with custom subdomain should return exit code 0")

            // Verify no error messages were printed
            assertTrue(errContent.toString().isEmpty(), "No errors should be printed for valid input")
        } finally {
            System.setErr(originalErr)
        }
    }
}
