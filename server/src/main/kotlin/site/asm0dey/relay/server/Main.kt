package site.asm0dey.relay.server

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain


@QuarkusMain
object Main: QuarkusApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        Quarkus.run(*args)
    }

    override fun run(vararg args: String?): Int {
        Quarkus.run(*args)
        return 0
    }
}