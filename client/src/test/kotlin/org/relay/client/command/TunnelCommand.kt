package org.relay.client.command

import picocli.CommandLine
import picocli.CommandLine.*

/**
 * Test-only TunnelCommand for Picocli parsing tests.
 *
 * This is a lightweight version used only for testing Picocli's argument parsing,
 * validation, and help generation without requiring the full Quarkus runtime.
 *
 * The production CLI uses RelayClientMain with full Quarkus integration.
 */
@Command(
    name = "relay-client",
    mixinStandardHelpOptions = true,
    version = ["1.0.0"],
    description = ["Relay Tunnel Client - expose local HTTP services to the internet"],
    footer = [
        "",
        "Examples:",
        "  # Expose local service on port 3000 with random subdomain",
        "  relay-client 3000 -s tun.example.com -k my-secret",
        "",
        "  # Request specific subdomain 'myapp'",
        "  relay-client 8080 -s tun.example.com -d myapp -k secret-key",
        "",
        "  # Use insecure ws:// for local development",
        "  relay-client 3000 -s localhost:8080 -k test --insecure"
    ]
)
class TunnelCommand : Runnable, TunnelCommandInterface {

    @Parameters(index = "0", description = ["Local HTTP service port (1-65535)"])
    override var port: Int = 0

    @Option(names = ["-s", "--server"], description = ["Tunnel server hostname (e.g., tun.example.com)"], required = true)
    override var server: String = ""

    @Option(names = ["-p", "--server-port"], description = ["Tunnel server port (default: 443 for wss://, 80 for ws://)"])
    override var serverPort: Int? = null

    @Option(names = ["-k", "--key"], description = ["Authentication secret key"], required = true)
    override var secretKey: String = ""

    @Option(names = ["-d", "--subdomain"], description = ["Request specific subdomain (optional)"])
    override var subdomain: String? = null

    @Option(names = ["--insecure"], description = ["Use ws:// instead of wss:// (for local development)"])
    override var insecure: Boolean = false

    @Option(names = ["-q", "--quiet"], description = ["Suppress non-error output"])
    override var quiet: Boolean = false

    @Option(names = ["-v", "--verbose"], description = ["Enable debug logging"])
    override var verbose: Boolean = false

    override fun run() {
        // Test-only: No-op implementation
        // Real execution happens in RelayClientMain
    }
}
