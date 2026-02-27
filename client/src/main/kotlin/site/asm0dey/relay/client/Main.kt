package site.asm0dey.relay.client

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import io.quarkus.websockets.next.WebSocketClientConnection
import io.quarkus.websockets.next.WebSocketConnector
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import site.asm0dey.relay.domain.Control
import site.asm0dey.relay.domain.Control.ControlPayload.ControlAction.REGISTER
import site.asm0dey.relay.domain.Envelope
import site.asm0dey.relay.domain.toByteArray
import java.util.*

@QuarkusMain(name = "client")
@CommandLine.Command
class Client constructor() : Runnable, QuarkusApplication {
    @CommandLine.Parameters(index = "0", paramLabel = "PORT", arity = "1", description = ["Port to listen on"])
    var localPort: Int? = null

    @CommandLine.Option(names = ["--remote-port", "-r"], defaultValue = "443", required = true)
    var remotePort: Int? = null

    @CommandLine.Option(names = ["--remote-host", "-h"], required = true)
    var remoteHost: String = "localhost"

    @CommandLine.Option(names = ["--domain", "-d"])
    var domain: String? = null

    @CommandLine.Option(names = ["--local-host", "-l"], defaultValue = "localhost")
    var localHost: String? = null

    @CommandLine.Option(names = ["--secret", "-s"], required = true)
    lateinit var secret: String

    @CommandLine.Option(names = ["--insecure"], defaultValue = "false", required = true)
    var insecure: Boolean = false

    @Inject
    lateinit var connector: WebSocketConnector<WsClient>


    override fun run() {


        val scheme = if (insecure) "ws" else "wss"
        val uri = "$scheme://$remoteHost:$remotePort/"

        val connection = connector
            .baseUri(uri)
            .pathParam("secret", secret)
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", domain)
            }
            .connectAndAwait()

        Runtime.getRuntime().addShutdownHook(Thread {
            val unregisterMsg = Envelope(
                correlationId = UUID.randomUUID().toString(),
                payload = Control(Control.ControlPayload(Control.ControlPayload.ControlAction.UNREGISTER))
            )
            connection.sendBinary(unregisterMsg.toByteArray()).await()
        })

        runBlocking {
            connection.sendRegister()
            launch {
                while (true) {
                    delay(30000)
                    val heartbeat = Envelope(
                        correlationId = UUID.randomUUID().toString(),
                        payload = Control(Control.ControlPayload(Control.ControlPayload.ControlAction.HEARTBEAT))
                    )
                    connection.sendBinary(heartbeat.toByteArray()).awaitSuspending()
                }
            }
        }

        Quarkus.waitForExit()
    }

    private suspend fun WebSocketClientConnection.sendRegister() {
        val registerMsg = Envelope(
            correlationId = UUID.randomUUID().toString(),
            payload = Control(Control.ControlPayload(REGISTER))
        )
        sendBinary(registerMsg.toByteArray()).awaitSuspending()

    }

    override fun run(vararg args: String?): Int {
        return CommandLine(this).execute(*args)
    }
}

