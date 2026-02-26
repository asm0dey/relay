package site.asm0dey.relay.client

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import io.quarkus.websockets.next.WebSocketConnector
import jakarta.inject.Inject
import picocli.CommandLine

@QuarkusMain
@CommandLine.Command
class Client @Inject constructor(val connector: WebSocketConnector<WsClient>) : Runnable, QuarkusApplication {
    @CommandLine.Parameters
    var localPort: Int = -1

    @CommandLine.Option(names = ["--remote-port", "-r"], defaultValue = "443")
    var remotePort: Int = -1

    @CommandLine.Option(names = ["--domain", "-d"])
    lateinit var domain: String

    @CommandLine.Option(names = ["--secret", "-s"])
    lateinit var secret: String

    @CommandLine.Option(names = ["--insecure"], defaultValue = "false")
    var insecure: Boolean = false


    override fun run() {


        val scheme = if (insecure) "ws" else "wss"
        val uri = "$scheme://$domain:$remotePort/"

        connector
            .baseUri(uri)
            .pathParam("secret", secret)
            .connectAndAwait()
        Quarkus.waitForExit()
    }

    override fun run(vararg args: String?): Int {
        return CommandLine(this).execute(*args)
    }
}

