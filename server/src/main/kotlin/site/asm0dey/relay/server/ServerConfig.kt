package site.asm0dey.relay.server

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.io.FileInputStream
import java.time.Duration
import java.util.*

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val domain: String = "domain.example.com",
    val allowedSecretKeys: List<String> = listOf("Secret"),
    val maxInflightChunks: Int = 10,
    val chunkTimeout: Duration = Duration.ofSeconds(10),
    val wsUpgradeTimeout: Duration = Duration.ofSeconds(30),
    val wsMaxTunnels: Int = 100,
    val wsPingInterval: Duration = Duration.ofSeconds(30),
)

@ApplicationScoped
class ServerConfigProducer {
    @Produces
    fun serverConfig(): ServerConfig = config
}

val config: ServerConfig by lazy {
    try {
        val properties = Properties()
        properties.load(
            FileInputStream(
                System.getenv("SERVER_CONFIG") ?: System.getProperty("server.config") ?: "server-config.conf"
            )
        )
        var conf = ServerConfig()
        properties.forEach { (k, v) ->
            when (k) {
                "port" -> conf = conf.copy(port = v.toString().toInt())
                "host" -> conf = conf.copy(host = v.toString())
                "domain" -> conf = conf.copy(domain = v.toString())
                "allowed_secret_keys" -> conf = conf.copy(allowedSecretKeys = v.toString().split(",").map { it.trim() })
                "max_inflight_chunks" -> conf = conf.copy(maxInflightChunks = v.toString().toInt())
                "chunk_timeout" -> conf = conf.copy(chunkTimeout = Duration.ofMillis(v.toString().toLong()))
                "ws_upgrade_timeout" -> conf = conf.copy(wsUpgradeTimeout = Duration.ofMillis(v.toString().toLong()))
                "ws_max_tunnels" -> conf = conf.copy(wsMaxTunnels = v.toString().toInt())
                "ws_ping_interval" -> conf = conf.copy(wsPingInterval = Duration.ofMillis(v.toString().toLong()))
            }
        }
        conf
    } catch (e: Exception) {
        println("Failed to load config from ${e.message}, using defaults")
        ServerConfig()
    }
}
