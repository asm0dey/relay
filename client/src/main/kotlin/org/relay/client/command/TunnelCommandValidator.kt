package org.relay.client.command

object TunnelCommandValidator {
    
    fun validate(
        port: Int,
        server: String,
        secretKey: String,
        subdomain: String? = null
    ): List<String> {
        val errors = mutableListOf<String>()
        
        ValidationUtils.getPortValidationError(port)?.let { errors.add(it) }
        ValidationUtils.getSubdomainValidationError(subdomain)?.let { errors.add(it) }
        
        if (server.isBlank()) {
            errors.add("Server is required (use -s or --server)")
        }
        
        if (secretKey.isBlank()) {
            errors.add("Secret key is required (use -k or --key)")
        }
        
        return errors
    }
}
