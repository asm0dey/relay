package site.asm0dey.relay.client

import io.quarkus.picocli.runtime.annotations.TopCommand
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import jakarta.inject.Inject
import picocli.CommandLine
import kotlin.properties.Delegates

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true)
open class ClientConfig : Runnable {
    @set:CommandLine.Parameters(
        index = "0",
        paramLabel = "PORT",
        arity = "1",
        description = ["Port to listen on"],
        defaultValue = "8081"
    )
    var localPort: Int by Delegates.notNull()

    @set:CommandLine.Option(names = ["--remote-port", "-r"], defaultValue = "443", required = true)
    var remotePort: Int by Delegates.notNull()

    @set:CommandLine.Option(names = ["--remote-host", "-h"], required = true)
    var remoteHost: String by Delegates.notNull()

    @set:CommandLine.Option(names = ["--domain", "-d"], defaultValue = "test")
    var domain: String by Delegates.notNull()

    @set:CommandLine.Option(names = ["--local-host", "-l"], defaultValue = "localhost")
    var localHost: String by Delegates.notNull()

    @set:CommandLine.Option(names = ["--secret", "-s"], required = true, defaultValue = "secret")
    var secret: String by Delegates.notNull()

    @set:CommandLine.Option(names = ["--insecure"], defaultValue = "false", required = true)
    var insecure: Boolean by Delegates.notNull()

    override fun run() {
    }

}

@QuarkusMain
class ClientMain : QuarkusApplication {
    @Inject
    lateinit var factory: CommandLine.IFactory

    @Inject
    @TopCommand
    lateinit var config: ClientConfig

    @Inject
    lateinit var tunnelService: TunnelClient

    override fun run(vararg args: String?): Int {
        // Parse directly into the CDI-managed instance
        val exitCode = CommandLine(config, factory).execute(*args)
        if (exitCode != 0) return exitCode

        tunnelService.start()
        Quarkus.waitForExit()
        return 0
    }
}

