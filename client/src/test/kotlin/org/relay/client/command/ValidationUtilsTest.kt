package org.relay.client.command

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ValidationUtilsTest {

    // TS-010, TS-011: Port validation tests
    @ParameterizedTest
    @ValueSource(ints = [1, 80, 443, 3000, 8080, 65535])
    fun `valid ports are accepted`(port: Int) {
        assertTrue(ValidationUtils.isValidPort(port), "Port $port should be valid")
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -100, 65536, 99999, 100000])
    fun `invalid ports are rejected`(port: Int) {
        assertFalse(ValidationUtils.isValidPort(port), "Port $port should be invalid")
    }

    @Test
    fun `port validation error message is descriptive`() {
        val error = ValidationUtils.getPortValidationError(0)
        assertNotNull(error)
        assertTrue(error!!.contains("1"))
        assertTrue(error.contains("65535"))
    }

    // TS-026, TS-027: DNS label validation tests
    @ParameterizedTest
    @ValueSource(strings = ["myapp", "a", "z9-8-7", "test-123", "sub-domain-1", "abc123", "1abc"])
    fun `valid subdomains are accepted`(subdomain: String) {
        assertTrue(ValidationUtils.isValidSubdomain(subdomain), "Subdomain '$subdomain' should be valid")
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "-invalid",      // starts with hyphen
        "invalid-",      // ends with hyphen
        "Invalid",       // uppercase
        "under_score",   // underscore
        "a.b",           // dot
        "",              // empty
        "toolong0123456789012345678901234567890123456789012345678901234567890" // 64 chars
    ])
    fun `invalid subdomains are rejected`(subdomain: String) {
        assertFalse(ValidationUtils.isValidSubdomain(subdomain), "Subdomain '$subdomain' should be invalid")
    }

    @Test
    fun `subdomain validation accepts exactly 63 characters`() {
        val exactly63 = "a" + "b".repeat(61) + "c"  // 63 chars total
        assertEquals(63, exactly63.length)
        assertTrue(ValidationUtils.isValidSubdomain(exactly63))
    }

    @Test
    fun `null subdomain is valid`() {
        assertTrue(ValidationUtils.isValidSubdomain(null))
    }

    @Test
    fun `subdomain validation error explains DNS rules`() {
        val error = ValidationUtils.getSubdomainValidationError("-invalid")
        assertNotNull(error)
        assertTrue(error!!.contains("DNS") || error.contains("alphanumeric") || error.contains("hyphen"))
    }
}
