package site.asm0dey.relay.server.mock

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import jakarta.websocket.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
class MockWebSocketServerTest {

    @TestHTTPResource("/mock-ws/orders")
    lateinit var ordersUri: URI

    @Inject
    lateinit var store: WebSocketSessionStore

    @Inject
    lateinit var registry: MockScenarioRegistry

    @BeforeEach
    fun setUp() {
        store.reset()
        registry.clearAll()
    }

    @Test
    fun connectAndReceiveGreeting() {
        // Register a scenario with a greeting
        given().contentType(ContentType.JSON)
            .body(
                mapOf(
                    "channel" to "orders",
                    "greeting" to "Welcome to orders!",
                    "replies" to emptyMap<String, String>()
                )
            )
            .post("/mock/scenarios")
            .then().statusCode(200)

        val latch = CountDownLatch(1)
        val received = CopyOnWriteArrayList<String>()

        val container = ContainerProvider.getWebSocketContainer()
        val session = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { msg ->
                    received.add(msg)
                    latch.countDown()
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), ordersUri)
        session.use {
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }

        assertEquals(listOf("Welcome to orders!"), received)
    }

    @Test
    fun echoScenario() {
        registry.register(buildScenario("orders", null, mapOf("ping" to "pong", "*" to "unknown")))

        val latch = CountDownLatch(1)
        val received = CopyOnWriteArrayList<String>()

        val container = ContainerProvider.getWebSocketContainer()
        val session = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { msg ->
                    received.add(msg)
                    latch.countDown()
                }
                session.asyncRemote.sendText("ping")
            }
        }, ClientEndpointConfig.Builder.create().build(), ordersUri)
        session.use {
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }

        assertEquals(listOf("pong"), received)

        // Verify event was stored
        val msgs = store.messages("orders")
        assertEquals(1, msgs.size)
        assertEquals("ping", msgs[0].payload)
    }

    @Test
    fun broadcastViaRestApi() {
        val latch = CountDownLatch(1)
        val received = CopyOnWriteArrayList<String>()

        val container = ContainerProvider.getWebSocketContainer()
        val session = container.connectToServer(object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.addMessageHandler(String::class.java) { msg ->
                    received.add(msg)
                    latch.countDown()
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), ordersUri)
        session.use {
            given().contentType(ContentType.JSON)
                .body(mapOf("message" to "order-update"))
                .post("/mock/broadcast/orders")
                .then().statusCode(200)

            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }

        assertEquals(listOf("order-update"), received)
    }

    // --- helper ---

    private fun buildScenario(channel: String, greeting: String?, replies: Map<String, String>): MockScenario {
        val s = MockScenario()
        s.channel = channel
        s.greeting = greeting
        s.replies = replies
        return s
    }
}
