package org.relay.shared.protocol

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolMessageTest {

    @Test
    fun `MessageType serialization - all enum values serialize correctly to strings`() {
        MessageType.entries.forEach { messageType ->
            val json = RelayJson.encodeToString(messageType)
            assertEquals("\"${messageType.type}\"", json)
        }
    }

    @Test
    fun `MessageType deserialization - all string values deserialize correctly`() {
        MessageType.entries.forEach { messageType ->
            val json = "\"${messageType.type}\""
            val deserialized = RelayJson.decodeFromString<MessageType>(json)
            assertEquals(messageType, deserialized)
        }
    }

    @Test
    fun `MessageType fromString - finds correct enum values`() {
        assertEquals(MessageType.REQUEST, MessageType.fromString("REQUEST"))
        assertEquals(MessageType.RESPONSE, MessageType.fromString("RESPONSE"))
        assertEquals(MessageType.ERROR, MessageType.fromString("ERROR"))
        assertEquals(MessageType.CONTROL, MessageType.fromString("CONTROL"))
        assertNull(MessageType.fromString("UNKNOWN"))
    }

    @Test
    fun `Envelope serialization - round-trip JSON serialization and deserialization`() {
        val payload = WebSocketFramePayload("","x", false, null, null).toJsonElement()
        val envelope = Envelope(
            correlationId = "test-correlation-123",
            type = MessageType.REQUEST,
            timestamp = Instant.parse("2024-01-15T10:30:00Z"),
            payload = payload
        )

        val json = envelope.toJson()
        val deserialized = json.toEnvelope()

        assertEquals(envelope.correlationId, deserialized.correlationId)
        assertEquals(envelope.type, deserialized.type)
        assertEquals(envelope.timestamp, deserialized.timestamp)
        assertEquals(envelope.payload, deserialized.payload)
    }

    @Test
    fun `RequestPayload serialization - serializes correctly including base64 body`() {
        val originalBody = "Hello, World!"
        val base64Body = Base64.getEncoder().encodeToString(originalBody.toByteArray())
        
        val requestPayload = RequestPayload(
            method = "POST",
            path = "/api/test",
            query = mapOf("param1" to "value1", "param2" to "value2"),
            headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer token123"),
            body = base64Body
        )

        val json = RelayJson.encodeToString(requestPayload)
        val deserialized = RelayJson.decodeFromString<RequestPayload>(json)

        assertEquals(requestPayload.method, deserialized.method)
        assertEquals(requestPayload.path, deserialized.path)
        assertEquals(requestPayload.query as Any?, deserialized.query as Any?)
        assertEquals(requestPayload.headers, deserialized.headers)
        assertEquals(requestPayload.body as Any?, deserialized.body as Any?)
        
        val decodedBody = String(Base64.getDecoder().decode(deserialized.body!!))
        assertEquals(originalBody, decodedBody)
    }

    @Test
    fun `RequestPayload serialization - handles null body and query`() {
        val requestPayload = RequestPayload(
            method = "GET",
            path = "/api/simple",
            query = null,
            headers = mapOf("Accept" to "application/json"),
            body = null
        )

        val json = RelayJson.encodeToString(requestPayload)
        val deserialized = RelayJson.decodeFromString<RequestPayload>(json)

        assertEquals("GET", deserialized.method)
        assertEquals("/api/simple", deserialized.path)
        assertNull(deserialized.query)
        assertNull(deserialized.body)
    }

    @Test
    fun `ResponsePayload serialization - round-trip serialization`() {
        val responsePayload = ResponsePayload(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json", "X-Request-Id" to "req-123"),
            body = "{\"success\": true}"
        )

        val json = RelayJson.encodeToString(responsePayload)
        val deserialized = RelayJson.decodeFromString<ResponsePayload>(json)

        assertEquals(responsePayload.statusCode as Any, deserialized.statusCode as Any)
        assertEquals(responsePayload.headers, deserialized.headers)
        assertEquals(responsePayload.body as Any?, deserialized.body as Any?)
    }

    @Test
    fun `ResponsePayload serialization - handles null body`() {
        val responsePayload = ResponsePayload(
            statusCode = 204,
            headers = emptyMap(),
            body = null
        )

        val json = RelayJson.encodeToString(responsePayload)
        val deserialized = RelayJson.decodeFromString<ResponsePayload>(json)

        assertEquals(204 as Any, deserialized.statusCode as Any)
        assertTrue(deserialized.headers.isEmpty())
        assertNull(deserialized.body)
    }

    @Test
    fun `ErrorPayload serialization - with all error codes`() {
        ErrorCode.entries.forEach { errorCode ->
            val errorPayload = ErrorPayload(
                code = errorCode,
                message = "Test error message for ${errorCode.code}"
            )

            val json = RelayJson.encodeToString(errorPayload)
            val deserialized = RelayJson.decodeFromString<ErrorPayload>(json)

            assertEquals(errorCode, deserialized.code)
            assertEquals(errorPayload.message, deserialized.message)
        }
    }

    @Test
    fun `ErrorCode fromString - finds correct enum values`() {
        assertEquals(ErrorCode.TIMEOUT, ErrorCode.fromString("TIMEOUT"))
        assertEquals(ErrorCode.UPSTREAM_ERROR, ErrorCode.fromString("UPSTREAM_ERROR"))
        assertEquals(ErrorCode.INVALID_REQUEST, ErrorCode.fromString("INVALID_REQUEST"))
        assertEquals(ErrorCode.SERVER_ERROR, ErrorCode.fromString("SERVER_ERROR"))
        assertEquals(ErrorCode.RATE_LIMITED, ErrorCode.fromString("RATE_LIMITED"))
        assertNull(ErrorCode.fromString("UNKNOWN_ERROR"))
    }

    @Test
    fun `ControlPayload serialization - with REGISTERED action`() {
        val controlPayload = ControlPayload(
            action = ControlPayload.ACTION_REGISTER,
            subdomain = "abc123xyz789",
            publicUrl = "https://abc123xyz789.relay.example.com"
        )

        val json = RelayJson.encodeToString(controlPayload)
        val deserialized = RelayJson.decodeFromString<ControlPayload>(json)

        assertEquals(ControlPayload.ACTION_REGISTER, deserialized.action)
        assertEquals("abc123xyz789", deserialized.subdomain)
        assertEquals("https://abc123xyz789.relay.example.com", deserialized.publicUrl)
    }

    @Test
    fun `ControlPayload serialization - with other actions`() {
        val actions = listOf(
            ControlPayload.ACTION_UNREGISTER,
            ControlPayload.ACTION_HEARTBEAT,
            ControlPayload.ACTION_STATUS
        )

        actions.forEach { action ->
            val controlPayload = ControlPayload(
                action = action,
                subdomain = null,
                publicUrl = null
            )

            val json = RelayJson.encodeToString(controlPayload)
            val deserialized = RelayJson.decodeFromString<ControlPayload>(json)

            assertEquals(action, deserialized.action)
            assertNull(deserialized.subdomain)
            assertNull(deserialized.publicUrl)
        }
    }

    @Test
    fun `Correlation ID matching - correlationId is preserved through serialization`() {
        val correlationId = "corr-12345-abcde-67890"
        val payload = mapOf("data" to "test").toJsonElement()
        val envelope = Envelope(
            correlationId = correlationId,
            type = MessageType.RESPONSE,
            timestamp = Instant.now(),
            payload = payload
        )

        val json = envelope.toJson()
        assertTrue(json.contains("\"correlationId\":\"$correlationId\""))
        
        val deserialized = json.toEnvelope()
        assertEquals(correlationId, deserialized.correlationId)
    }

    @Test
    fun `Timestamp handling - timestamp is set correctly and serialized`() {
        val beforeCreation = Instant.now()
        val payload = mapOf("test" to "data").toJsonElement()
        val envelope = Envelope(
            correlationId = "test-123",
            type = MessageType.CONTROL,
            payload = payload
        )
        val afterCreation = Instant.now()

        assertTrue(envelope.timestamp.toEpochMilli() >= beforeCreation.toEpochMilli())
        assertTrue(envelope.timestamp.toEpochMilli() <= afterCreation.toEpochMilli())

        val json = envelope.toJson()
        val deserialized = json.toEnvelope()

        assertEquals(envelope.timestamp, deserialized.timestamp)
        assertTrue(json.contains("\"timestamp\":\""))
    }

    @Test
    fun `Complete message flow - request to response with same correlationId`() {
        val correlationId = "flow-123-456"
        val requestPayload = RequestPayload(
            method = "GET",
            path = "/test",
            headers = mapOf("Accept" to "application/json")
        )
        
        val requestEnvelope = Envelope(
            correlationId = correlationId,
            type = MessageType.REQUEST,
            payload = requestPayload.toJsonElement()
        )

        val responsePayload = ResponsePayload(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"result\": \"ok\"}"
        )
        
        val responseEnvelope = Envelope(
            correlationId = correlationId,
            type = MessageType.RESPONSE,
            payload = responsePayload.toJsonElement()
        )

        assertEquals(requestEnvelope.correlationId, responseEnvelope.correlationId)
        
        val requestJson = requestEnvelope.toJson()
        val responseJson = responseEnvelope.toJson()
        
        val deserializedRequest = requestJson.toEnvelope()
        val deserializedResponse = responseJson.toEnvelope()
        
        assertEquals(correlationId, deserializedRequest.correlationId)
        assertEquals(correlationId, deserializedResponse.correlationId)
        assertEquals(MessageType.REQUEST, deserializedRequest.type)
        assertEquals(MessageType.RESPONSE, deserializedResponse.type)
    }
}
