package site.asm0dey.relay.server

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.quarkiverse.wiremock.devservice.ConnectWireMock
import io.quarkiverse.wiremock.devservice.WireMockConfigKey
import io.quarkus.grpc.GrpcService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import picocli.CommandLine
import site.asm0dey.relay.client.ClientConfig
import site.asm0dey.relay.client.TunnelClient
import java.security.MessageDigest

@QuarkusTest
@ConnectWireMock
class RelayIntegrationTest {

    // Injected by @ConnectWireMock
    lateinit var wiremock: WireMock

    @ConfigProperty(name = WireMockConfigKey.PORT)
    lateinit var wireMockPort: Provider<Int>

    @Inject
    lateinit var tunnelClient: TunnelClient

    @Produces
    fun parseResult(): CommandLine.ParseResult {
        val config = ClientConfig()
        val parseResult = CommandLine(config).parseArgs(
            "--remote-host", "localhost",
            "--remote-port", "${testPort.get()}",
            "--secret", "secret",
            "--insecure",
            "--domain", "test",
            "--local-host", "localhost",
            "${wireMockPort.get()}"
        )
        return parseResult
    }

    @ConfigProperty(name = "quarkus.http.test-port")
    lateinit var testPort: Provider<Int>

    //    @ConfigProperty(name = "quarkus.grpc.server.port")
//    lateinit var grpcPort: Provider<Int>
    @BeforeEach
    fun startClient() {
        tunnelClient.start()
    }

    @AfterEach
    fun stopClient() {
    }

    @Test
    @Order(1)
    fun `GET request returns correct JSON body from WireMock`() {
        wiremock.register(
            get(urlEqualTo("/api/hello"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"hello"}""")
                )
        )

        val body = given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/hello")
            .then()
            .statusCode(200)
            .extract().body().asString()

        assertEquals("""{"message":"hello"}""", body)
    }

    // 2.3 GET returns 204 with no body
    @Test
    @Order(2)
    fun `GET request returns 204 with no body`() {
        wiremock.register(
            get(urlEqualTo("/api/empty"))
                .willReturn(aResponse().withStatus(204))
        )

        given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/empty")
            .then()
            .statusCode(204)
    }

    // 2.4 POST with JSON body forwarded correctly
    @Test
    @Order(3)
    fun `POST with JSON body is forwarded correctly and response returned`() {
        val requestBody = """{"data":"test"}"""
        val responseBody = """{"status":"ok"}"""

        wiremock.register(
            post(urlEqualTo("/api/data"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )

        val body = given()
            .header("X-Subdomain", "test")
            .header("Content-Type", "application/json")
            .body(requestBody)
            .post("http://localhost:${testPort.get()}/api/data")
            .then()
            .statusCode(200)
            .extract().body().asString()

        assertEquals(responseBody, body)
    }

    // 2.5 Custom response headers forwarded
    @Test
    @Order(4)
    fun `Custom response headers are forwarded through the relay`() {
        wiremock.register(
            get(urlEqualTo("/api/headers"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Header", "custom-value")
                        .withBody("ok")
                )
        )

        given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/headers")
            .then()
            .statusCode(200)
            .header("X-Custom-Header", "custom-value")
    }

    // 2.6 404 relayed correctly
    @Test
    @Order(5)
    fun `404 error status code is relayed correctly`() {
        wiremock.register(
            get(urlEqualTo("/api/notfound"))
                .willReturn(aResponse().withStatus(404).withBody("Not Found"))
        )

        given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/notfound")
            .then()
            .statusCode(404)
    }

    // 2.7 500 error with body relayed correctly
    @Test
    @Order(6)
    fun `500 error status code with body is relayed correctly`() {
        wiremock.register(
            get(urlEqualTo("/api/error"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")
                )
        )

        val body = given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/error")
            .then()
            .statusCode(500)
            .extract().body().asString()

        assertEquals("Internal Server Error", body)
    }

    // 2.8 Large binary response streamed correctly with MD5 check
    @Test
    @Order(7)
    fun `Large binary response is streamed correctly with MD5 integrity check`() {
        wiremock.register(
            get(urlEqualTo("/api/bigfile"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("my-big-file.bin")
                )
        )

        val responseBytes = given()
            .header("X-Subdomain", "test")
            .get("http://localhost:${testPort.get()}/api/bigfile")
            .then()
            .statusCode(200)
            .extract().asByteArray()

        val md5 = MessageDigest.getInstance("MD5")
            .digest(responseBytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals("007f2cbd62670b1fc165cc9c59ebeeb6", md5, "Large binary response MD5 mismatch")
    }
}
