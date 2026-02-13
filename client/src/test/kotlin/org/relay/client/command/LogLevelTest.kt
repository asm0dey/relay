package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("LogLevel")
class LogLevelTest {

    @Test
    @DisplayName("TS-017: quiet flag maps to ERROR level")
    fun `quiet flag maps to ERROR level`() {
        val level = LogLevel.fromFlags(quiet = true, verbose = false)
        assertEquals(LogLevel.ERROR, level)
    }

    @Test
    @DisplayName("TS-018: no flags map to INFO level")
    fun `no flags maps to INFO level`() {
        val level = LogLevel.fromFlags(quiet = false, verbose = false)
        assertEquals(LogLevel.INFO, level)
    }

    @Test
    @DisplayName("TS-019: verbose flag maps to DEBUG level")
    fun `verbose flag maps to DEBUG level`() {
        val level = LogLevel.fromFlags(quiet = false, verbose = true)
        assertEquals(LogLevel.DEBUG, level)
    }

    @Test
    @DisplayName("TS-020: conflicting flags (quiet+verbose) return INFO (ignore both)")
    fun `conflicting flags return INFO level`() {
        val level = LogLevel.fromFlags(quiet = true, verbose = true)
        assertEquals(LogLevel.INFO, level)
    }

    @Test
    fun `all log levels are defined`() {
        assertEquals(3, LogLevel.entries.size)
        assertTrue(LogLevel.entries.contains(LogLevel.ERROR))
        assertTrue(LogLevel.entries.contains(LogLevel.INFO))
        assertTrue(LogLevel.entries.contains(LogLevel.DEBUG))
    }
}
