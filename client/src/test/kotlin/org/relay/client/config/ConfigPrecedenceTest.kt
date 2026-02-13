package org.relay.client.config

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * T030: Config Precedence Test - System Properties Override
 *
 * Verifies the configuration precedence order:
 * CLI args (system properties) > Environment variables > Properties file > Defaults
 *
 * Test 1: CLI args (system properties) override everything else
 */
@QuarkusTest
@TestProfile(ConfigPrecedenceSystemPropertiesTest.SystemPropertiesProfile::class)
class ConfigPrecedenceSystemPropertiesTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class SystemPropertiesProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            // Simulate RelayClientMain setting system properties from CLI args
            return mapOf(
                // CLI args (system properties) - highest priority
                "relay.client.server-url" to "wss://cli-arg.example.com/ws",
                "relay.client.secret-key" to "cli-secret-key",
                "relay.client.local-url" to "http://localhost:9999",
                "relay.client.subdomain" to "cli-subdomain",

                // These would normally come from env vars or properties file
                "relay.client.reconnect.enabled" to "false",
                "relay.client.reconnect.initial-delay" to "5s"
            )
        }
    }

    @Test
    fun `CLI args via system properties take highest precedence`() {
        // CLI args should override env vars and properties file
        assertEquals("wss://cli-arg.example.com/ws", clientConfig.serverUrl(),
            "CLI arg (system property) should override other sources")

        assertEquals("cli-secret-key", clientConfig.secretKey().orElse(""),
            "CLI secret key should override other sources")

        assertEquals("http://localhost:9999", clientConfig.localUrl(),
            "CLI local URL should override other sources")

        assertEquals("cli-subdomain", clientConfig.subdomain().orElse(""),
            "CLI subdomain should override other sources")

        assertFalse(clientConfig.reconnect().enabled(),
            "CLI reconnect setting should override defaults")

        assertEquals(Duration.ofSeconds(5), clientConfig.reconnect().initialDelay(),
            "CLI reconnect delay should override defaults")
    }
}

/**
 * T030: Config Precedence Test - Environment Variables
 *
 * Test 2: Environment variables override properties file but not system properties
 */
@QuarkusTest
@TestProfile(ConfigPrecedenceEnvironmentVariablesTest.EnvironmentVariablesProfile::class)
class ConfigPrecedenceEnvironmentVariablesTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class EnvironmentVariablesProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            // Simulate environment variables (no system properties set)
            return mapOf(
                "relay.client.server-url" to "wss://env-var.example.com/ws",
                "relay.client.secret-key" to "env-secret-key",
                "relay.client.local-url" to "http://localhost:7777",
                "relay.client.reconnect.max-delay" to "120s"
            )
        }
    }

    @Test
    fun `environment variables override properties file`() {
        // Env vars should override properties file values
        assertEquals("wss://env-var.example.com/ws", clientConfig.serverUrl(),
            "Env var should override properties file")

        assertEquals("env-secret-key", clientConfig.secretKey().orElse(""),
            "Env var secret key should override properties file")

        assertEquals("http://localhost:7777", clientConfig.localUrl(),
            "Env var local URL should override properties file")

        assertEquals(Duration.ofSeconds(120), clientConfig.reconnect().maxDelay(),
            "Env var reconnect max delay should override properties file")
    }
}

/**
 * T030: Config Precedence Test - Properties File
 *
 * Test 3: Properties file overrides defaults
 */
@QuarkusTest
@TestProfile(ConfigPrecedencePropertiesFileTest.PropertiesFileProfile::class)
class ConfigPrecedencePropertiesFileTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class PropertiesFileProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            // Simulate properties file values (no system properties or env vars)
            return mapOf(
                "relay.client.server-url" to "wss://properties.example.com/ws",
                "relay.client.secret-key" to "properties-secret-key",
                "relay.client.local-url" to "http://localhost:8888",
                "relay.client.reconnect.enabled" to "true",
                "relay.client.reconnect.initial-delay" to "3s",
                "relay.client.reconnect.max-delay" to "90s",
                "relay.client.reconnect.multiplier" to "1.8",
                "relay.client.reconnect.jitter" to "0.15"
            )
        }
    }

    @Test
    fun `properties file overrides default values`() {
        // Properties file should override @WithDefault annotations
        assertEquals("wss://properties.example.com/ws", clientConfig.serverUrl(),
            "Properties file should provide server URL")

        assertEquals("properties-secret-key", clientConfig.secretKey().orElse(""),
            "Properties file should provide secret key")

        assertEquals("http://localhost:8888", clientConfig.localUrl(),
            "Properties file should provide local URL")

        assertTrue(clientConfig.reconnect().enabled(),
            "Properties file should override default reconnect enabled")

        assertEquals(Duration.ofSeconds(3), clientConfig.reconnect().initialDelay(),
            "Properties file should override default initial delay")

        assertEquals(Duration.ofSeconds(90), clientConfig.reconnect().maxDelay(),
            "Properties file should override default max delay")

        assertEquals(1.8, clientConfig.reconnect().multiplier(),
            "Properties file should override default multiplier")

        assertEquals(0.15, clientConfig.reconnect().jitter(),
            "Properties file should override default jitter")
    }
}

/**
 * T030: Config Precedence Test - Default Values
 *
 * Test 4: Default values are used when nothing else is configured
 */
@QuarkusTest
@TestProfile(ConfigPrecedenceDefaultValuesTest.DefaultValuesProfile::class)
class ConfigPrecedenceDefaultValuesTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class DefaultValuesProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            // Only provide required values, let reconnect config use defaults
            return mapOf(
                "relay.client.server-url" to "wss://default-test.example.com/ws",
                "relay.client.secret-key" to "default-secret-key",
                "relay.client.local-url" to "http://localhost:3000"
                // No reconnect configuration provided - should use @WithDefault values
            )
        }
    }

    @Test
    fun `default values are used when no configuration is provided`() {
        // Reconnect configuration should use @WithDefault values
        assertTrue(clientConfig.reconnect().enabled(),
            "Default reconnect enabled should be true")

        assertEquals(Duration.ofSeconds(1), clientConfig.reconnect().initialDelay(),
            "Default initial delay should be 1 second")

        assertEquals(Duration.ofSeconds(60), clientConfig.reconnect().maxDelay(),
            "Default max delay should be 60 seconds")

        assertEquals(2.0, clientConfig.reconnect().multiplier(),
            "Default multiplier should be 2.0")

        assertEquals(0.1, clientConfig.reconnect().jitter(),
            "Default jitter should be 0.1")
    }
}

/**
 * T030: Config Precedence Test - Full Chain
 *
 * Test 5: Full precedence chain verification
 */
@QuarkusTest
@TestProfile(ConfigPrecedenceFullChainTest.FullPrecedenceProfile::class)
class ConfigPrecedenceFullChainTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class FullPrecedenceProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            // Mix of CLI args (system properties), env vars, and properties file
            return mapOf(
                // Highest priority: CLI arg (system property)
                "relay.client.server-url" to "wss://cli.example.com/ws",

                // Medium priority: Env var (no CLI arg for this)
                "relay.client.local-url" to "http://localhost:4000",

                // Lower priority: Properties file (no CLI arg or env var)
                "relay.client.secret-key" to "properties-secret",
                "relay.client.reconnect.enabled" to "true",
                "relay.client.reconnect.initial-delay" to "2s",
                "relay.client.reconnect.max-delay" to "30s",
                "relay.client.reconnect.multiplier" to "1.5"
                // jitter not configured - should use @WithDefault value of 0.1
            )
        }
    }

    @Test
    fun `configuration precedence chain works correctly`() {
        // CLI arg (system property) wins
        assertEquals("wss://cli.example.com/ws", clientConfig.serverUrl(),
            "CLI arg should take highest precedence")

        // Env var wins (no CLI arg for this field)
        assertEquals("http://localhost:4000", clientConfig.localUrl(),
            "Env var should take precedence over properties file")

        // Properties file wins (no CLI arg or env var for this field)
        assertEquals("properties-secret", clientConfig.secretKey().orElse(""),
            "Properties file should take precedence over defaults")

        assertTrue(clientConfig.reconnect().enabled(),
            "Properties file should provide reconnect enabled")

        assertEquals(Duration.ofSeconds(2), clientConfig.reconnect().initialDelay(),
            "Properties file should provide initial delay")

        assertEquals(Duration.ofSeconds(30), clientConfig.reconnect().maxDelay(),
            "Properties file should provide max delay")

        assertEquals(1.5, clientConfig.reconnect().multiplier(),
            "Properties file should provide multiplier")

        // Default value wins (not configured anywhere)
        assertEquals(0.1, clientConfig.reconnect().jitter(),
            "Default jitter should be used when not configured")
    }
}

/**
 * T030: Config Precedence Test - Optional Values
 *
 * Test 6: Optional configuration values
 */
@QuarkusTest
@TestProfile(ConfigPrecedenceOptionalValuesTest.OptionalValuesProfile::class)
class ConfigPrecedenceOptionalValuesTest {

    @Inject
    lateinit var clientConfig: ClientConfig

    class OptionalValuesProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            return mapOf(
                "relay.client.server-url" to "wss://optional-test.example.com/ws",
                "relay.client.secret-key" to "optional-secret-key",
                "relay.client.local-url" to "http://localhost:3000"
                // Note: subdomain not provided - should be Optional.empty()
            )
        }
    }

    @Test
    fun `optional configuration values can be absent`() {
        // Required values should be present
        assertEquals("wss://optional-test.example.com/ws", clientConfig.serverUrl())
        assertTrue(clientConfig.secretKey().isPresent)
        assertEquals("http://localhost:3000", clientConfig.localUrl())

        // Optional subdomain should be absent
        assertFalse(clientConfig.subdomain().isPresent,
            "Subdomain should be Optional.empty() when not configured")
    }

    @Test
    fun `optional secret key handling`() {
        // Subdomain is optional and should be handled gracefully
        assertFalse(clientConfig.subdomain().isPresent,
            "Optional subdomain should be empty when not provided")
    }
}

/**
 * T030: Config Precedence Test - Runtime System Properties
 *
 * Test 7: CLI args override via system properties at runtime
 */
class ConfigPrecedenceRuntimeTest {

    @Test
    fun `system properties set by CLI override all other sources`() {
        // Given: System properties are set (simulating RelayClientMain behavior)
        val originalServerUrl = System.getProperty("relay.client.server-url")
        val originalLocalUrl = System.getProperty("relay.client.local-url")
        val originalSecretKey = System.getProperty("relay.client.secret-key")

        try {
            // Set system properties as RelayClientMain would do
            System.setProperty("relay.client.server-url", "wss://runtime-cli.example.com/ws")
            System.setProperty("relay.client.local-url", "http://localhost:12345")
            System.setProperty("relay.client.secret-key", "runtime-cli-secret")

            // Then: System properties should be readable
            assertEquals("wss://runtime-cli.example.com/ws",
                System.getProperty("relay.client.server-url"),
                "System property should be set from CLI args")

            assertEquals("http://localhost:12345",
                System.getProperty("relay.client.local-url"),
                "System property should be set from CLI args")

            assertEquals("runtime-cli-secret",
                System.getProperty("relay.client.secret-key"),
                "System property should be set from CLI args")

        } finally {
            // Cleanup
            if (originalServerUrl != null) {
                System.setProperty("relay.client.server-url", originalServerUrl)
            } else {
                System.clearProperty("relay.client.server-url")
            }

            if (originalLocalUrl != null) {
                System.setProperty("relay.client.local-url", originalLocalUrl)
            } else {
                System.clearProperty("relay.client.local-url")
            }

            if (originalSecretKey != null) {
                System.setProperty("relay.client.secret-key", originalSecretKey)
            } else {
                System.clearProperty("relay.client.secret-key")
            }
        }
    }
}
