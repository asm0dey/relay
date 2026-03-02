@file:OptIn(ExperimentalSerializationApi::class)

package site.asm0dey.relay.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import site.asm0dey.relay.domain.Control.ControlPayload.ControlAction.REGISTER
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoBufSerializationTest {

    @Test
    fun `Control payload serialization and deserialization - SHUTDOWN`() {
        val controlEnvelope = Envelope(
            correlationId = "test-id-1",
            payload = Control(value = Control.ControlPayload(Control.ControlPayload.ControlAction.SHUTDOWN))
        )

        val bytes = controlEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(controlEnvelope.correlationId, decoded.correlationId)
        assertTrue(decoded.payload is Control)
        val control = decoded.payload
        assertEquals(Control.ControlPayload.ControlAction.SHUTDOWN, control.value.action)
    }

    @Test
    fun `Control payload serialization and deserialization - REGISTER`() {
        val controlEnvelope = Envelope(
            correlationId = "test-id-2",
            payload = Control(
                value = Control.ControlPayload(
                    action = REGISTER,
                    subdomain = "mysubdomain",
                    publicUrl = "https://example.com"
                )
            )
        )

        val bytes = controlEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(controlEnvelope.correlationId, decoded.correlationId)
        assertTrue(decoded.payload is Control)
        val control = decoded.payload
        assertEquals(REGISTER, control.value.action)
        assertEquals("mysubdomain", control.value.subdomain)
        assertEquals("https://example.com", control.value.publicUrl)
    }

    @Test
    fun `Control payload serialization and deserialization - all control actions`() {
        val actions = Control.ControlPayload.ControlAction.entries

        for (action in actions) {
            val envelope = Envelope(
                correlationId = "test-$action",
                payload = Control(value = Control.ControlPayload(action))
            )

            val bytes = envelope.toByteArray()
            val decoded = Envelope.fromByteArray(bytes)

            assertTrue(decoded.payload is Control)
            assertEquals(action, decoded.payload.value.action)
        }
    }

    @Test
    fun `Request payload serialization and deserialization - minimal`() {
        val requestEnvelope = Envelope(
            correlationId = "test-id-3",
            payload = Request(value = Request.RequestPayload(method = "GET", path = "/api/users"))
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(requestEnvelope.correlationId, decoded.correlationId)
        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals("GET", request.value.method)
        assertEquals("/api/users", request.value.path)
        assertEquals(emptyMap(), request.value.query)
        assertEquals(emptyMap(), request.value.headers)
    }

    @Test
    fun `Request payload serialization and deserialization - with query params`() {
        val requestEnvelope = Envelope(
            correlationId = "test-id-4",
            payload = Request(
                value = Request.RequestPayload(
                    method = "POST",
                    path = "/api/users",
                    query = mapOf("name" to "John", "age" to "30", "active" to "true")
                )
            )
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals("POST", request.value.method)
        assertEquals("/api/users", request.value.path)
        assertEquals(3, request.value.query.size)
        assertEquals("John", request.value.query["name"])
        assertEquals("30", request.value.query["age"])
        assertEquals("true", request.value.query["active"])
    }

    @Test
    fun `Request payload serialization and deserialization - with headers`() {
        val requestEnvelope = Envelope(
            correlationId = "test-id-5",
            payload = Request(
                value = Request.RequestPayload(
                    method = "DELETE",
                    path = "/api/users/123",
                    headers = mapOf("Authorization" to "Bearer token", "Content-Type" to "application/json")
                )
            )
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals("DELETE", request.value.method)
        assertEquals("/api/users/123", request.value.path)
        assertEquals(2, request.value.headers.size)
        assertEquals("Bearer token", request.value.headers["Authorization"])
        assertEquals("application/json", request.value.headers["Content-Type"])
    }

    @Test
    fun `Request payload serialization and deserialization - with body`() {
        val bodyContent = "Hello, World!".toByteArray()
        val requestEnvelope = Envelope(
            correlationId = "test-id-6",
            payload = Request(
                value = Request.RequestPayload(
                    method = "PUT",
                    path = "/api/users/123",
                    body = bodyContent
                )
            )
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals("PUT", request.value.method)
        assertTrue(request.value.body!!.contentEquals(bodyContent))
    }

    @Test
    fun `Request payload serialization and deserialization - websocket upgrade flag`() {
        val requestEnvelope = Envelope(
            correlationId = "test-id-7",
            payload = Request(
                value = Request.RequestPayload(
                    method = "GET",
                    path = "/socket",
                    websocketUpgrade = true
                )
            )
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals(true, request.value.websocketUpgrade)
    }

    @Test
    fun `Request payload serialization and deserialization - with all fields`() {
        val bodyContent = """{"name":"John","age":30}""".toByteArray()
        val requestEnvelope = Envelope(
            correlationId = "test-id-8",
            payload = Request(
                value = Request.RequestPayload(
                    method = "POST",
                    path = "/api/users",
                    query = mapOf("debug" to "true"),
                    headers = mapOf("Authorization" to "Bearer xyz"),
                    body = bodyContent,
                    websocketUpgrade = false
                )
            )
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertEquals("POST", request.value.method)
        assertEquals("/api/users", request.value.path)
        assertEquals("true", request.value.query["debug"])
        assertEquals("Bearer xyz", request.value.headers["Authorization"])
        assertTrue(request.value.body!!.contentEquals(bodyContent))
        assertEquals(false, request.value.websocketUpgrade)
    }

    @Test
    fun `Response payload serialization and deserialization - minimal`() {
        val responseEnvelope = Envelope(
            correlationId = "test-id-9",
            payload = Response(value = Response.ResponsePayload(statusCode = 200, headers = emptyMap()))
        )

        val bytes = responseEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(responseEnvelope.correlationId, decoded.correlationId)
        assertTrue(decoded.payload is Response)
        val response = decoded.payload
        assertEquals(200, response.value.statusCode)
        assertEquals(emptyMap(), response.value.headers)
    }

    @Test
    fun `Response payload serialization and deserialization - with body`() {
        val bodyContent = "Response body".toByteArray()
        val responseEnvelope = Envelope(
            correlationId = "test-id-10",
            payload = Response(
                value = Response.ResponsePayload(
                    statusCode = 201,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = bodyContent
                )
            )
        )

        val bytes = responseEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Response)
        val response = decoded.payload
        assertEquals(201, response.value.statusCode)
        assertEquals("text/plain", response.value.headers["Content-Type"])
        assertTrue(response.value.body!!.contentEquals(bodyContent))
    }

    @Test
    fun `Response payload serialization and deserialization - with headers`() {
        val responseEnvelope = Envelope(
            correlationId = "test-id-11",
            payload = Response(
                value = Response.ResponsePayload(
                    statusCode = 404,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Error-Code" to "NOT_FOUND"
                    )
                )
            )
        )

        val bytes = responseEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Response)
        val response = decoded.payload
        assertEquals(404, response.value.statusCode)
        assertEquals(2, response.value.headers.size)
        assertEquals("application/json", response.value.headers["Content-Type"])
        assertEquals("NOT_FOUND", response.value.headers["X-Error-Code"])
    }

    @Test
    fun `Error payload serialization and deserialization`() {
        val errorEnvelope = Envelope(
            correlationId = "test-id-12",
            payload = Error(value = Error.ErrorPayload(code = ErrorCode.TIMEOUT, message = "Request timed out"))
        )

        val bytes = errorEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(errorEnvelope.correlationId, decoded.correlationId)
        assertTrue(decoded.payload is Error)
        val error = decoded.payload
        assertEquals(ErrorCode.TIMEOUT, error.value.code)
        assertEquals("Request timed out", error.value.message)
    }

    @Test
    fun `Error payload serialization and deserialization - all error codes`() {
        val errorCodes = ErrorCode.entries

        for (code in errorCodes) {
            val envelope = Envelope(
                correlationId = "test-$code",
                payload = Error(value = Error.ErrorPayload(code = code, message = "Error: $code"))
            )

            val bytes = envelope.toByteArray()
            val decoded = Envelope.fromByteArray(bytes)

            assertTrue(decoded.payload is Error)
            assertEquals(code, decoded.payload.value.code)
            assertEquals("Error: $code", decoded.payload.value.message)
        }
    }


    @Test
    fun `Large body serialization and deserialization`() {
        val largeBody = ByteArray(1024 * 1024) { it.toByte() } // 1 MB body
        val requestEnvelope = Envelope(
            correlationId = "test-id-14",
            payload = Request(value = Request.RequestPayload(method = "POST", path = "/upload", body = largeBody))
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertTrue(request.value.body!!.contentEquals(largeBody))
    }

    @Test
    fun `Empty body serialization and deserialization`() {
        val emptyBody = ByteArray(0)
        val requestEnvelope = Envelope(
            correlationId = "test-id-15",
            payload = Request(value = Request.RequestPayload(method = "GET", path = "/test", body = emptyBody))
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertTrue(request.value.body!!.isEmpty())
    }

    @Test
    fun `Correlation ID preservation`() {
        val correlationId = "unique-correlation-id-12345"
        val envelope = Envelope(
            correlationId = correlationId,
            payload = Request(value = Request.RequestPayload(method = "GET", path = "/test"))
        )

        val bytes = envelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertEquals(correlationId, decoded.correlationId)
    }


    @Test
    fun `Binary content preservation in body`() {
        val binaryContent = byteArrayOf(0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0xFF.toByte(), 0xFE.toByte())
        val requestEnvelope = Envelope(
            correlationId = "test-id-16",
            payload = Request(value = Request.RequestPayload(method = "POST", path = "/binary", body = binaryContent))
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertTrue(request.value.body!!.contentEquals(binaryContent))
    }

    @Test
    fun `UTF-8 content preservation in body`() {
        val utf8Content = "Hello 世界 Привет مرحبا".toByteArray(Charsets.UTF_8)
        val requestEnvelope = Envelope(
            correlationId = "test-id-17",
            payload = Request(value = Request.RequestPayload(method = "POST", path = "/text", body = utf8Content))
        )

        val bytes = requestEnvelope.toByteArray()
        val decoded = Envelope.fromByteArray(bytes)

        assertTrue(decoded.payload is Request)
        val request = decoded.payload
        assertTrue(request.value.body!!.contentEquals(utf8Content))
        assertEquals("Hello 世界 Привет مرحبا", String(request.value.body, Charsets.UTF_8))
    }

    @Test
    fun `Multiple serialization cycles maintain integrity`() {
        val original = Envelope(
            correlationId = "test-id-18",
            payload = Request(
                value = Request.RequestPayload(
                    method = "POST",
                    path = "/api/users",
                    query = mapOf("id" to "123"),
                    headers = mapOf("Auth" to "token"),
                    body = "data".toByteArray()
                )
            )
        )

        var current = original
        (1..5).forEach { _ ->
            val bytes = current.toByteArray()
            current = Envelope.fromByteArray(bytes)
        }

        assertEquals(original.correlationId, current.correlationId)
        assertTrue(current.payload is Request)
        val request = current.payload
        assertEquals("POST", request.value.method)
        assertEquals("/api/users", request.value.path)
        assertEquals("123", request.value.query["id"])
        assertEquals("token", request.value.headers["Auth"])
        assertTrue(request.value.body!!.contentEquals("data".toByteArray()))
    }

    @Test
    fun `Different payload types are correctly distinguished`() {
        val control = Envelope(
            correlationId = "id-1",
            payload = Control(value = Control.ControlPayload(Control.ControlPayload.ControlAction.SHUTDOWN))
        )
        val request = Envelope(
            correlationId = "id-2",
            payload = Request(value = Request.RequestPayload(method = "GET", path = "/"))
        )
        val response = Envelope(
            correlationId = "id-3",
            payload = Response(value = Response.ResponsePayload(statusCode = 200, headers = emptyMap()))
        )
        val error = Envelope(
            correlationId = "id-4",
            payload = Error(value = Error.ErrorPayload(code = ErrorCode.TIMEOUT, message = "timeout"))
        )

        val controlBytes = control.toByteArray()
        val requestBytes = request.toByteArray()
        val responseBytes = response.toByteArray()
        val errorBytes = error.toByteArray()

        assertTrue(Envelope.fromByteArray(controlBytes).payload is Control)
        assertTrue(Envelope.fromByteArray(requestBytes).payload is Request)
        assertTrue(Envelope.fromByteArray(responseBytes).payload is Response)
        assertTrue(Envelope.fromByteArray(errorBytes).payload is Error)
    }

    @Test
    fun `serialize and deserialize StreamErrorCode`() {
        val code = StreamErrorCode.UPSTREAM_TIMEOUT
        val bytes = ProtoBuf.encodeToByteArray(StreamErrorCode.serializer(), code)
        val deserialized = ProtoBuf.decodeFromByteArray(StreamErrorCode.serializer(), bytes)
        assertEquals(code, deserialized)
    }

    @Test
    fun `serialize and deserialize StreamInit`() {
        val init = Envelope(
            correlationId = "test-id",
            payload = StreamInit(StreamInit.StreamInitPayload(
                correlationId = "test-id",
                contentType = "application/pdf",
                contentLength = 10485760L
            ))
        )
        val bytes = init.toByteArray()
        val deserialized = bytes.toEnvelope()
        assertEquals(init.correlationId, deserialized.correlationId)
        assertTrue(deserialized.payload is StreamInit)
        val streamInit = deserialized.payload
        assertEquals("test-id", streamInit.value.correlationId)
        assertEquals("application/pdf", streamInit.value.contentType)
        assertEquals(10485760L, streamInit.value.contentLength)
    }

    @Test
    fun `serialize and deserialize StreamChunk`() {
        val chunk = Envelope(
            correlationId = "test-id",
            payload = StreamChunk(StreamChunk.StreamChunkPayload(
                correlationId = "test-id",
                chunkIndex = 5,
                data = ByteArray(1024) { it.toByte() },
                isLast = false
            ))
        )
        val bytes = chunk.toByteArray()
        val deserialized = bytes.toEnvelope()
        assertEquals(chunk.correlationId, deserialized.correlationId)
        assertTrue(deserialized.payload is StreamChunk)
        val streamChunk = deserialized.payload
        assertEquals("test-id", streamChunk.value.correlationId)
        assertEquals(5L, streamChunk.value.chunkIndex)
        assertTrue(streamChunk.value.data.contentEquals(ByteArray(1024) { it.toByte() }))
        assertEquals(false, streamChunk.value.isLast)
    }

    @Test
    fun `serialize and deserialize StreamAck`() {
        val ack = Envelope(
            correlationId = "test-id",
            payload = StreamAck(StreamAck.StreamAckPayload(
                correlationId = "test-id",
                chunkIndex = 5
            ))
        )
        val bytes = ack.toByteArray()
        val deserialized = bytes.toEnvelope()
        assertEquals(ack.correlationId, deserialized.correlationId)
        assertTrue(deserialized.payload is StreamAck)
        val streamAck = deserialized.payload
        assertEquals("test-id", streamAck.value.correlationId)
        assertEquals(5L, streamAck.value.chunkIndex)
    }

    @Test
    fun `serialize and deserialize StreamError`() {
        val error = Envelope(
            correlationId = "test-id",
            payload = StreamError(StreamError.StreamErrorPayload(
                correlationId = "test-id",
                code = StreamErrorCode.UPSTREAM_TIMEOUT,
                message = "Local app stopped responding"
            ))
        )
        val bytes = error.toByteArray()
        val deserialized = bytes.toEnvelope()
        assertEquals(error.correlationId, deserialized.correlationId)
        assertTrue(deserialized.payload is StreamError)
        val streamError = deserialized.payload
        assertEquals("test-id", streamError.value.correlationId)
        assertEquals(StreamErrorCode.UPSTREAM_TIMEOUT, streamError.value.code)
        assertEquals("Local app stopped responding", streamError.value.message)
    }
}
