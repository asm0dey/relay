package org.relay.client.command

interface TunnelCommandInterface {
    val port: Int
    val server: String
    val serverPort: Int?
    val secretKey: String
    val subdomain: String?
    val insecure: Boolean
    val quiet: Boolean
    val verbose: Boolean
}