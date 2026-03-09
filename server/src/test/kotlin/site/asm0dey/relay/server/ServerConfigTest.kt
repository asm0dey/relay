package site.asm0dey.relay.server

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

@QuarkusTest
class ServerConfigTest {
    @Inject
    lateinit var serverConfig: ServerConfig

    @Test
    fun `WebSocket configuration has default values`() {
        assertEquals(Duration.ofSeconds(30), serverConfig.wsUpgradeTimeout)
        assertEquals(100, serverConfig.wsMaxTunnels)
        assertEquals(Duration.ofSeconds(30), serverConfig.wsPingInterval)
    }
}
