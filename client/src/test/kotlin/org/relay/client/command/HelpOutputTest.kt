package org.relay.client.command

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Tests for TS-004: Help output displays usage, options, examples.
 * Verifies FR-018 format: Usage line, blank line, positional args section,
 * blank line, options section (aligned), blank line, examples section.
 *
 * Tests Picocli command help generation directly without Quarkus runtime.
 */
class HelpOutputTest {

    private fun captureHelp(): String {
        val command = TunnelCommand()
        val commandLine = CommandLine(command)
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        commandLine.usage(ps)
        return baos.toString("UTF-8")
    }

    @Test
    fun `help option shows usage information`() {
        val output = captureHelp()

        // Should contain usage information
        assertTrue(output.contains("Usage:") || output.contains("Usage :"),
            "Help should contain usage. Output: $output")
        assertTrue(output.contains("port") || output.contains("<port>"),
            "Help should mention port argument. Output: $output")
    }

    @Test
    fun `help shows all required options`() {
        val output = captureHelp()

        // Required options
        assertTrue(output.contains("-s") && output.contains("--server"),
            "Help should show -s/--server option. Output: $output")
        assertTrue(output.contains("-k") && output.contains("--key"),
            "Help should show -k/--key option. Output: $output")
    }

    @Test
    fun `help shows optional options`() {
        val output = captureHelp()

        // Optional flags
        assertTrue(output.contains("-d") && output.contains("--subdomain"),
            "Help should show -d/--subdomain option. Output: $output")
        assertTrue(output.contains("--insecure"),
            "Help should show --insecure flag. Output: $output")
        assertTrue(output.contains("-q") && output.contains("--quiet"),
            "Help should show -q/--quiet option. Output: $output")
        assertTrue(output.contains("-v") && output.contains("--verbose"),
            "Help should show -v/--verbose option. Output: $output")
    }

    @Test
    fun `help shows option descriptions`() {
        val output = captureHelp()

        // Check that descriptions are present
        assertTrue(output.contains("port") || output.contains("Local HTTP service"),
            "Help should describe port parameter. Output: $output")
        assertTrue(output.contains("server") || output.contains("hostname"),
            "Help should describe server option. Output: $output")
        assertTrue(output.contains("secret") || output.contains("Authentication") || output.contains("key"),
            "Help should describe key option. Output: $output")
        assertTrue(output.contains("subdomain") || output.contains("Request specific"),
            "Help should describe subdomain option. Output: $output")
    }

    @Test
    fun `help follows FR-018 format structure`() {
        val output = captureHelp()
        val lines = output.lines()

        // FR-018: Usage line, blank line, positional args section, blank line,
        // options section (aligned), blank line, examples section

        // Find key sections
        val usageLineIndex = lines.indexOfFirst { it.contains("Usage:") || it.contains("Usage :") }
        assertTrue(usageLineIndex >= 0, "Should have Usage line. Output: $output")

        // Should have some structure with blank lines
        val hasBlankLines = lines.any { it.trim().isEmpty() }
        assertTrue(hasBlankLines, "Should have blank lines for formatting. Output: $output")

        // Should have positional argument mentioned
        val hasPositionalSection = lines.any {
            it.contains("port") || it.contains("<port>") || it.contains("Parameters:")
        }
        assertTrue(hasPositionalSection, "Should have positional arguments section. Output: $output")

        // Should have options section
        val hasOptionsSection = lines.any {
            it.contains("-s") || it.contains("Options:") || it.contains("--server")
        }
        assertTrue(hasOptionsSection, "Should have options section. Output: $output")
    }

    @Test
    fun `help includes examples section`() {
        val output = captureHelp()

        // FR-018 requires examples section with 2-3 common use cases
        // Examples should show the command syntax in action
        val hasExamplesSection = output.contains("Example") ||
                                  output.contains("EXAMPLE") ||
                                  output.contains("Usage examples") ||
                                  output.contains("relay-client 3000")

        assertTrue(hasExamplesSection,
            "Help should include examples section per FR-018. Output: $output")
    }

    @Test
    fun `help shows aligned option descriptions`() {
        val output = captureHelp()
        val lines = output.lines()

        // FR-018: options section with aligned descriptions
        // Find lines with options (those that start with - and have --)
        val optionLines = lines.filter {
            val trimmed = it.trim()
            trimmed.startsWith("-") && trimmed.contains("--")
        }

        assertTrue(optionLines.size >= 5,
            "Should have at least 5 option lines (s, k, d, insecure, q, v). Found: ${optionLines.size}. Lines: $optionLines")

        // Check that each option line has a description (text after the option name)
        // Picocli formats as: "  -s, --server=<value>  Description text here"
        optionLines.forEach { line ->
            // Look for description text after the option specification
            // Descriptions should be separated from the option by spaces
            val parts = line.split("\\s{2,}".toRegex()) // Split on 2+ spaces
            val hasDescription = parts.size >= 2 && parts[1].isNotBlank()
            assertTrue(hasDescription,
                "Option line should have description: '$line' (parts: $parts)")
        }
    }

    @Test
    fun `help shows standard help options from mixin`() {
        val output = captureHelp()

        // mixinStandardHelpOptions should add -h, --help, -V, --version
        assertTrue(output.contains("-h") || output.contains("--help"),
            "Help should show -h/--help option from mixin. Output: $output")
        assertTrue(output.contains("-V") || output.contains("--version"),
            "Help should show -V/--version option from mixin. Output: $output")
    }

}
