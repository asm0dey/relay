package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import kotlinx.serialization.SerializationException
import java.time.Instant

/**
 * Tests for ProtobufSerializer - Green phase (TDD)
 *
 * Tests: TS-012 (message type structure), TS-014 (payload types support)
 */
@OptIn(ExperimentalSerializationApi::class)
class ProtobufSerializerTest {

    /**
     * TS-012: Message type preservation through serialization
     */
    @Test
    fun `test message type preservation through serialization`() {
        val envelope = Envelope(
            correlationId = "test-123",
            type = MessageType.REQUEST,
            timestamp = Instant.now(),
            payload = Payload.Request(RequestPayload(
                method = "GET",
                path = "/test",
                query = null,
                headers = mapOf(),
                body = null
            ))
        )

        val bytes = ProtobufSerializer.encodeEnvelope(envelope)
        val deserialized = ProtobufSerializer.decodeEnvelope(bytes)

        assertEquals(MessageType.REQUEST, deserialized.type)
    }

    /**
     * TS-014: All payload types supported
     */
    @Test
    fun `test all payload types supported`() {
        // Test REQUEST with ByteArray body
        val requestEnvelope = Envelope(
            correlationId = "test-REQUEST",
            type = MessageType.REQUEST,
            timestamp = Instant.now(),
            payload = Payload.Request(RequestPayload(
                method = "POST",
                path = "/api/test",
                query = mapOf("key" to "value"),
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"test\": true}".toByteArray()
            ))
        )
        val requestBytes = ProtobufSerializer.encodeEnvelope(requestEnvelope)
        val requestDeserialized = ProtobufSerializer.decodeEnvelope(requestBytes)
        assertEquals(MessageType.REQUEST, requestDeserialized.type)
        assertTrue(requestDeserialized.payload is Payload.Request)

        // Test RESPONSE with ByteArray body
        val responseEnvelope = Envelope(
            correlationId = "test-RESPONSE",
            type = MessageType.RESPONSE,
            timestamp = Instant.now(),
            payload = Payload.Response(ResponsePayload(
                statusCode = 200,
                headers = mapOf("Content-Type" to "text/html"),
                body = "<html>Success</html>".toByteArray()
            ))
        )
        val responseBytes = ProtobufSerializer.encodeEnvelope(responseEnvelope)
        val responseDeserialized = ProtobufSerializer.decodeEnvelope(responseBytes)
        assertEquals(MessageType.RESPONSE, responseDeserialized.type)
        assertTrue(responseDeserialized.payload is Payload.Response)

        // Test ERROR
        val errorEnvelope = Envelope(
            correlationId = "test-ERROR",
            type = MessageType.ERROR,
            timestamp = Instant.now(),
            payload = Payload.Error(ErrorPayload(
                code = ErrorCode.UPSTREAM_ERROR,
                message = "Upstream service unavailable"
            ))
        )
        val errorBytes = ProtobufSerializer.encodeEnvelope(errorEnvelope)
        val errorDeserialized = ProtobufSerializer.decodeEnvelope(errorBytes)
        assertEquals(MessageType.ERROR, errorDeserialized.type)
        assertTrue(errorDeserialized.payload is Payload.Error)

        // Test CONTROL
        val controlEnvelope = Envelope(
            correlationId = "test-CONTROL",
            type = MessageType.CONTROL,
            timestamp = Instant.now(),
            payload = Payload.Control(ControlPayload(
                action = ControlPayload.ACTION_REGISTER,
                subdomain = "test",
                publicUrl = "https://test.relay.example.com"
            ))
        )
        val controlBytes = ProtobufSerializer.encodeEnvelope(controlEnvelope)
        val controlDeserialized = ProtobufSerializer.decodeEnvelope(controlBytes)
        assertEquals(MessageType.CONTROL, controlDeserialized.type)
        assertTrue(controlDeserialized.payload is Payload.Control)
    }

    /**
     * Test error handling for malformed data
     */
    @Test
    fun `test serialization exception on malformed data`() {
        val malformedBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        val exception = assertThrows<SerializationException> {
            ProtobufSerializer.decodeEnvelope(malformedBytes)
        }

        // Verify error message has useful information
        assertTrue(exception.message != null && exception.message!!.isNotEmpty(),
                   "Error message should not be empty")
        assertTrue(exception.message!!.contains("decode") ||
                   exception.message!!.contains("malformed") ||
                   exception.message!!.contains("Invalid") ||
                   exception.message!!.contains("Varint"),
                   "Error message should describe the problem: ${exception.message}")
    }
}
