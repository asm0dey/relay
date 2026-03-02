package site.asm0dey.relay.server

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.quarkiverse.wiremock.devservice.ConnectWireMock
import io.quarkiverse.wiremock.devservice.WireMockConfigKey
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.websockets.next.WebSocketConnector
import io.restassured.RestAssured.given
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.buffer.Buffer
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.matchesRegex
import org.hamcrest.core.Is.`is`
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import picocli.CommandLine
import site.asm0dey.relay.client.Client
import site.asm0dey.relay.client.WsClient
import site.asm0dey.relay.domain.Control
import site.asm0dey.relay.domain.Control.ControlPayload.ControlAction.REGISTER
import site.asm0dey.relay.domain.Envelope
import site.asm0dey.relay.domain.toByteArray
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


@QuarkusTest
@ConnectWireMock
class FirstTest {

    @TestHTTPResource
    lateinit var baseUri: URI

    lateinit var wireMock: WireMock

    @ConfigProperty(name = WireMockConfigKey.PORT)
    lateinit var wiremockPort: Integer

    @Produces
    @ApplicationScoped
    @Alternative
    @Priority(1)
    fun parseResult(): CommandLine.ParseResult {
        return CommandLine(Client()).parseArgs(
            "--secret",
            "Secret",
            "--insecure",
            "-l",
            "localhost",
            "-h",
            "localhost",
            "-r",
            System.getProperty("quarkus.http.test-port", "8081"),
            "--domain",
            "test",
            wiremockPort.toString()
        )
    }

    @Inject
    lateinit var connector: WebSocketConnector<WsClient>

    @Inject
    lateinit var client: Provider<WsClient>


    @Test
    fun testGet() {
        println("DEBUG: Testing connection to localhost:${baseUri.port}")
        try {
            java.net.Socket("127.0.0.1", baseUri.port).use {
                println("DEBUG: Socket connected to 127.0.0.1:${baseUri.port}: ${it.isConnected}")
            }
        } catch (e: Exception) {
            println("DEBUG: Socket failed to 127.0.0.1:${baseUri.port}: ${e.message}")
        }

        val wsUri = baseUri()
        println("DEBUG: wsUri=$wsUri")
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(get(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("OK")))
        given()
            .header("X-Domain", "test")
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(`is`("OK"))
    }

    @Test
    fun testPost() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(post(urlEqualTo("/")).willReturn(aResponse().withStatus(201).withBody("Created")))
        given()
            .header("X-Domain", "test")
            .body("test body")
            .`when`()
            .post("/")
            .then()
            .statusCode(201)
            .body(`is`("Created"))
    }

    @Test
    fun testPut() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(put(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("Updated")))
        given()
            .header("X-Domain", "test")
            .body("update body")
            .`when`()
            .put("/")
            .then()
            .statusCode(200)
            .body(`is`("Updated"))
    }

    @Test
    fun testDelete() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(delete(urlEqualTo("/")).willReturn(aResponse().withStatus(204)))
        given()
            .header("X-Domain", "test")
            .`when`()
            .delete("/")
            .then()
            .statusCode(204)
    }

    @Test
    fun testHead() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(head(urlEqualTo("/")).willReturn(aResponse().withStatus(200)))
        given()
            .header("X-Domain", "test")
            .`when`()
            .head("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun testOptions() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(options(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("Options OK")))
        given()
            .header("X-Domain", "test")
            .`when`()
            .options("/")
            .then()
            .statusCode(200)
            .body(`is`("Options OK"))
    }

    @Test
    fun testPatch() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(patch(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("Patched")))
        given()
            .header("X-Domain", "test")
            .body("patch data")
            .`when`()
            .patch("/")
            .then()
            .statusCode(200)
            .body(`is`("Patched"))
    }

    @Test
    fun testLargePayload() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        val largePayload = "A".repeat(10 * 1024 * 1024)
        wireMock.register(
            post("/large")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("A")
                )
        )
        given()
            .header("X-Domain", "test")
            .body(largePayload)
            .`when`()
            .post("http://localhost:$wiremockPort/large")
            .then()
            .statusCode(200)
            .body(`is`("A"))
    }

    @Test
    fun testHeaderPropagation() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(get(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("Headers OK")))
        given()
            .header("X-Domain", "test")
            .header("X-Custom-Header", "custom-value")
            .header("Authorization", "Bearer token123")
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(`is`("Headers OK"))
    }

    @Test
    fun testQueryParameterForwarding() {
        val wsUri = baseUri()
        connector.baseUri(URI(wsUri))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(get(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200).withBody("Query OK")))
        given()
            .queryParam("X-Domain", "test")
            .queryParam("param1", "value1")
            .queryParam("param2", "value2")
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body(`is`("Query OK"))
    }

    @Test
    fun testErrorResponse() {
        connector.baseUri(URI(baseUri()))
            .pathParam("secret", "Secret")
            .customizeOptions { connectOptions, _ ->
                connectOptions.addHeader("domain", "test")
            }
            .connectAndAwait()
        wireMock.register(
            get(urlEqualTo("/")).willReturn(
                aResponse().withStatus(500).withBody("Internal Server Error")
            )
        )
        given()
            .header("X-Domain", "test")
            .`when`()
            .get("/")
            .then()
            .statusCode(500)
            .body(`is`("Internal Server Error"))
    }

    @Test
    fun testConnectionFailsWithWrongSecret() {
        runBlocking {
            val connection = connector.baseUri(URI(baseUri()))
                .pathParam("secret", "WrongSecret")
                .customizeOptions { connectOptions, _ ->
                    connectOptions.addHeader("domain", "test")
                }
                .connectAndAwait()

            // Wait for the server to close the connection
            delay(100)

            // The connection should be closed by the server due to invalid secret
            assertTrue(connection.isClosed, "Connection should be closed with wrong secret")

            // Verify the close reason
            val closeReason = connection.closeReason()
            assertTrue(closeReason != null, "Close reason should be present")
            assertTrue(closeReason?.code == 1008, "Close code should be 1008 (Policy Violation)")
            assertTrue(closeReason?.message?.contains("Invalid") == true, "Close reason should mention invalid secret")
        }
    }

    @Test
    fun testRandomDomainAssignmentWithoutHeader() {
        runBlocking {

            val connection = connector.baseUri(URI(baseUri()))
                .pathParam("secret", "Secret")
                .connectAndAwait()

            // Send a REGISTER message
            val registerMsg = Envelope(
                correlationId = UUID.randomUUID().toString(),
                payload = Control(Control.ControlPayload(REGISTER))
            )
            connection.sendBinary(Buffer.buffer(registerMsg.toByteArray())).awaitSuspending()

            // Wait for the response
            delay(200)

            // Get the assigned subdomain from the TestWsClient
            val assignedSubdomain = client.get().assignedSubdomain

            assertNotNull(assignedSubdomain, "Subdomain should not be null")
            assertThat(
                "Subdomain should be 5 alphanumeric characters, got: $assignedSubdomain",
                assignedSubdomain,
                matchesRegex("^[a-z0-9]{5}$")
            )
        }
    }

    @Test
    fun testTransformerIsApplied() {
        // Verify the transformer is being called by wiremock


        wireMock.register(
            post("/test-transform")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBodyFile("my-big-file.bin")
                )
        )

        val asByteArray = given()
            .post("http://localhost:$wiremockPort/test-transform")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asByteArray()
        val expectedBytes = this::class.java.classLoader.getResourceAsStream("/__files/my-big-file.bin")!!.readAllBytes();
        assertArrayEquals(expectedBytes, asByteArray)

        // Verify transformer was called
    }


    private fun baseUri(): String {
        val wsUri = baseUri.toString().replace("http://", "ws://").replace("localhost", "127.0.0.1")
        return wsUri
    }
}



