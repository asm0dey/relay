package org.relay.client.command

/**
 * Utility class for validating CLI input parameters.
 * Provides validation for ports and DNS labels (subdomains).
 */
object ValidationUtils {

    /**
     * DNS label pattern per RFC 1035.
     * - 1-63 characters
     * - Starts and ends with alphanumeric
     * - Middle may contain hyphens
     * - Lowercase only
     */
    private val DNS_LABEL_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535

    /**
     * Validates that a port number is within the valid range (1-65535).
     * 
     * @param port the port number to validate
     * @return true if valid, false otherwise
     */
    fun isValidPort(port: Int): Boolean {
        return port in MIN_PORT..MAX_PORT
    }

    /**
     * Returns a validation error message for an invalid port.
     * 
     * @param port the invalid port number
     * @return error message, or null if port is valid
     */
    fun getPortValidationError(port: Int): String? {
        return if (isValidPort(port)) {
            null
        } else {
            "Port must be a number between $MIN_PORT and $MAX_PORT, got: $port"
        }
    }

    /**
     * Validates that a subdomain conforms to DNS label rules.
     * 
     * @param subdomain the subdomain to validate (null is considered valid)
     * @return true if valid or null, false otherwise
     */
    fun isValidSubdomain(subdomain: String?): Boolean {
        if (subdomain == null) return true
        if (subdomain.isEmpty()) return false
        if (subdomain.length > 63) return false
        return DNS_LABEL_PATTERN.matches(subdomain)
    }

    /**
     * Returns a validation error message for an invalid subdomain.
     * 
     * @param subdomain the invalid subdomain
     * @return error message, or null if subdomain is valid
     */
    fun getSubdomainValidationError(subdomain: String?): String? {
        return when {
            subdomain == null -> null
            subdomain.isEmpty() -> "Subdomain cannot be empty"
            subdomain.length > 63 -> "Subdomain must be 63 characters or fewer, got: ${subdomain.length}"
            !DNS_LABEL_PATTERN.matches(subdomain) -> 
                "Subdomain must contain only lowercase alphanumeric characters and hyphens, " +
                "start and end with alphanumeric, and be 1-63 characters long"
            else -> null
        }
    }
}
