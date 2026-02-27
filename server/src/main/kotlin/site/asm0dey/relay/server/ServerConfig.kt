package site.asm0dey.relay.server

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.io.FileInputStream
import java.util.*

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val domain: String = "domain.example.com",
    val allowedSecretKeys: List<String> = listOf("Secret")
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
            }
        }
        conf
    } catch (e: Exception) {
        println("Failed to load config from ${e.message}, using defaults")
        ServerConfig()
    }
}
