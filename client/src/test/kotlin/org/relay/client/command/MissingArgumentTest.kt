package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

/**
 * Missing Argument Tests for TunnelCommand
 *
 * Tests TS-005 and TS-006 from test-specs.md:
 * - TS-005: No arguments shows error (port required)
 * - TS-006: Port only (no -s/-k) shows errors for missing server and key
 *
 * These tests verify that Picocli's required=true annotation properly reports
 * missing arguments with appropriate error messages.
 */
class MissingArgumentTest {

    /**
     * TS-005: No arguments shows error (port required)
     *
     * Given: command invoked with no arguments
     * When: picocli parses the command
     * Then: error message indicates missing port parameter
     * And: exit code is 2 (picocli's default for missing required params)
     */
    @Test
    fun `TS-005 - no arguments shows port required error`() {
        val command = TunnelCommand()
        val cmdLine = CommandLine(command)

        // Capture stderr to verify error messages
        val errContent = ByteArrayOutputStream()
        cmdLine.err = PrintWriter(errContent, true)

        // Execute with no arguments
        val exitCode = cmdLine.execute()

        // Picocli returns exit code 2 for missing required parameters
        assertEquals(2, exitCode, "Missing required parameter should return exit code 2")

        val errorOutput = errContent.toString()

        // Verify error message mentions the missing port parameter
        assertTrue(
            errorOutput.contains("Missing required parameter", ignoreCase = true),
            "Error should indicate missing required parameter. Output: $errorOutput"
        )
        assertTrue(
            errorOutput.contains("<port>", ignoreCase = true) ||
            errorOutput.contains("PORT", ignoreCase = true) ||
            errorOutput.contains("positional parameter", ignoreCase = true),
            "Error should mention the port parameter. Output: $errorOutput"
        )
    }

    /**
     * TS-006: Port only (no -s/-k) shows errors for missing server and key
     *
     * Given: command invoked with port only, missing -s and -k flags
     * When: picocli parses the command
     * Then: error message indicates missing --server option
     * And: error message indicates missing --key option
     * And: exit code is 2 (picocli's default for missing required options)
     */
    @Test
    fun `TS-006 - port only shows missing server and key errors`() {
        val command = TunnelCommand()
        val cmdLine = CommandLine(command)

        // Capture stderr to verify error messages
        val errContent = ByteArrayOutputStream()
        cmdLine.err = PrintWriter(errContent, true)

        // Execute with only port, missing -s and -k
        val exitCode = cmdLine.execute("3000")

        // Picocli returns exit code 2 for missing required options
        assertEquals(2, exitCode, "Missing required options should return exit code 2")

        val errorOutput = errContent.toString()

        // Verify error message mentions missing --server option
        assertTrue(
            errorOutput.contains("--server", ignoreCase = true) ||
            errorOutput.contains("-s", ignoreCase = true),
            "Error should mention the missing --server option. Output: $errorOutput"
        )

        // Verify error message mentions missing --key option
        assertTrue(
            errorOutput.contains("--key", ignoreCase = true) ||
            errorOutput.contains("-k", ignoreCase = true),
            "Error should mention the missing --key option. Output: $errorOutput"
        )

        // Verify it's a "Missing required option" type error
        assertTrue(
            errorOutput.contains("Missing required option", ignoreCase = true),
            "Error should indicate missing required options. Output: $errorOutput"
        )
    }

    /**
     * Additional test: Port with only server (missing key) shows error
     *
     * Given: command invoked with port and -s, but missing -k
     * When: picocli parses the command
     * Then: error message indicates missing --key option
     */
    @Test
    fun `port and server only shows missing key error`() {
        val command = TunnelCommand()
        val cmdLine = CommandLine(command)

        // Capture stderr to verify error messages
        val errContent = ByteArrayOutputStream()
        cmdLine.err = PrintWriter(errContent, true)

        // Execute with port and server, missing -k
        val exitCode = cmdLine.execute("3000", "-s", "tun.example.com")

        assertEquals(2, exitCode, "Missing required option should return exit code 2")

        val errorOutput = errContent.toString()

        // Verify error message mentions missing --key option
        assertTrue(
            errorOutput.contains("--key", ignoreCase = true) ||
            errorOutput.contains("-k", ignoreCase = true),
            "Error should mention the missing --key option. Output: $errorOutput"
        )

        // Server should not be mentioned as the missing required option (it was provided)
        // Look for "Missing required option: '--server" pattern specifically
        val hasServerAsMissingOption = errorOutput.contains("Missing required option:") &&
                                       (errorOutput.contains("'--server") || errorOutput.contains("'-s"))
        assertFalse(
            hasServerAsMissingOption,
            "Error should not list server as the missing required option. Output: $errorOutput"
        )
    }

    /**
     * Additional test: Port with only key (missing server) shows error
     *
     * Given: command invoked with port and -k, but missing -s
     * When: picocli parses the command
     * Then: error message indicates missing --server option
     */
    @Test
    fun `port and key only shows missing server error`() {
        val command = TunnelCommand()
        val cmdLine = CommandLine(command)

        // Capture stderr to verify error messages
        val errContent = ByteArrayOutputStream()
        cmdLine.err = PrintWriter(errContent, true)

        // Execute with port and key, missing -s
        val exitCode = cmdLine.execute("3000", "-k", "test-secret")

        assertEquals(2, exitCode, "Missing required option should return exit code 2")

        val errorOutput = errContent.toString()

        // Verify error message mentions missing --server option
        assertTrue(
            errorOutput.contains("--server", ignoreCase = true) ||
            errorOutput.contains("-s", ignoreCase = true),
            "Error should mention the missing --server option. Output: $errorOutput"
        )

        // Key should not be mentioned as the missing required option (it was provided)
        // Look for "Missing required option: '--key" pattern specifically
        val hasKeyAsMissingOption = errorOutput.contains("Missing required option:") &&
                                   (errorOutput.contains("'--key") || errorOutput.contains("'-k"))
        assertFalse(
            hasKeyAsMissingOption,
            "Error should not list key as the missing required option. Output: $errorOutput"
        )
    }

    /**
     * Positive test: All required arguments provided succeeds parsing
     *
     * Given: command invoked with all required arguments (port, -s, -k)
     * When: picocli parses the command
     * Then: parsing succeeds (no picocli errors)
     * Note: Execution may fail later in validation or runtime, but picocli parsing succeeds
     */
    @Test
    fun `all required arguments provided passes picocli parsing`() {
        val command = TunnelCommand()
        val cmdLine = CommandLine(command)

        // We can't fully execute (it would call Quarkus.waitForExit())
        // So we test that picocli parsing succeeds by checking parseArgs doesn't throw
        try {
            val parseResult = cmdLine.parseArgs("3000", "-s", "tun.example.com", "-k", "test-secret")
            assertNotNull(parseResult, "Parsing should succeed with all required arguments")

            // Verify parameters were correctly parsed
            assertEquals(3000, command.port, "Port should be parsed correctly")
            assertEquals("tun.example.com", command.server, "Server should be parsed correctly")
            assertEquals("test-secret", command.secretKey, "Secret key should be parsed correctly")
        } catch (e: CommandLine.ParameterException) {
            fail("Should not throw ParameterException when all required arguments are provided: ${e.message}")
        }
    }
}
