package site.asm0dey.relay.server

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import jakarta.inject.Inject


@QuarkusMain(name = "server")
class Main: QuarkusApplication {
    companion object{
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("quarkus.http.port", config.port.toString())
            System.setProperty("quarkus.http.host", config.host)
            Quarkus.run(*args)
        }
    }
    @Inject
    lateinit var serverConfig: ServerConfig

    override fun run(vararg args: String?): Int {
        System.setProperty("quarkus.http.port", serverConfig.port.toString())
        System.setProperty("quarkus.http.host", serverConfig.host)
        Quarkus.run(*args)
        return 0
    }
}