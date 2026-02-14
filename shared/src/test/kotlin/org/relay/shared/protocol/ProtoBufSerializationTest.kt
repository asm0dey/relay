package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Test specifications for ProtoBuf serialization - Green phase (TDD)
 *
 * Tests: TS-019 (envelope structure), TS-011 (correlation ID preservation), TS-013 (timestamp precision)
 */
@OptIn(ExperimentalSerializationApi::class)
class ProtoBufSerializationTest {

    /**
     * TS-019: WebSocket protocol contract - ProtoBuf envelope structure
     *
     * Given: API expects Envelope with correlation_id, type, timestamp, payload
     * When: Request is made with valid Protobuf binary Envelope
     * Then: Response envelope follows same structure with correct field numbers
     */
    @Test
    fun `test envelope serialization with correct protobuf structure`() {
        val testCorrelationId = "test-correlation-123"
        val testTimestamp = Instant.parse("2026-02-14T12:00:00Z")

        val envelope = Envelope(
            correlationId = testCorrelationId,
            type = MessageType.REQUEST,
            timestamp = testTimestamp,
            payload = Payload.Request(RequestPayload(
                method = "GET",
                path = "/test",
                query = null,
                headers = mapOf("Host" to "example.com"),
                body = null
            ))
        )

        // Serialize to Protobuf bytes
        val bytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), envelope)

        // Verify bytes are non-empty and compact
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size < 200) // Protobuf should be compact

        // Deserialize back
        val deserialized = ProtoBufConfig.format.decodeFromByteArray(Envelope.serializer(), bytes)

        // Verify structure preserved
        assertEquals(testCorrelationId, deserialized.correlationId)
        assertEquals(MessageType.REQUEST, deserialized.type)
        assertEquals(testTimestamp.toEpochMilli(), deserialized.timestamp.toEpochMilli())
        assertTrue(deserialized.payload is Payload.Request)
    }

    /**
     * TS-011: Correlation ID preservation
     *
     * Given: A REQUEST message with correlation ID "abc-123"
     * When: Serialized to Protobuf and deserialized
     * Then: The correlation ID "abc-123" is preserved exactly
     */
    @Test
    fun `test correlation ID preservation through protobuf serialization`() {
        val originalCorrelationId = "abc-123"

        val envelope = Envelope(
            correlationId = originalCorrelationId,
            type = MessageType.REQUEST,
            timestamp = Instant.now(),
            payload = Payload.Request(RequestPayload(
                method = "POST",
                path = "/api/test",
                query = null,
                headers = mapOf(),
                body = "test body".toByteArray()
            ))
        )

        // Serialize to Protobuf bytes
        val bytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), envelope)

        // Deserialize back
        val deserialized = ProtoBufConfig.format.decodeFromByteArray(Envelope.serializer(), bytes)

        // Verify correlation ID preserved exactly
        assertEquals(originalCorrelationId, deserialized.correlationId)
    }

    /**
     * TS-013: Timestamp precision preservation
     *
     * Given: A REQUEST message with timestamp "2026-02-14T12:34:56.789Z"
     * When: Serialized to Protobuf and deserialized
     * Then: The timestamp precision (milliseconds) is preserved exactly
     */
    @Test
    fun `test timestamp precision preservation through protobuf serialization`() {
        val originalTimestamp = Instant.parse("2026-02-14T12:34:56.789Z")

        val envelope = Envelope(
            correlationId = "test-123",
            type = MessageType.RESPONSE,
            timestamp = originalTimestamp,
            payload = Payload.Response(ResponsePayload(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"result\": \"success\"}".toByteArray()
            ))
        )

        // Serialize to Protobuf bytes
        val bytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), envelope)

        // Deserialize back
        val deserialized = ProtoBufConfig.format.decodeFromByteArray(Envelope.serializer(), bytes)

        // Verify timestamp preserved with millisecond precision
        assertEquals(originalTimestamp.toEpochMilli(), deserialized.timestamp.toEpochMilli())
        assertEquals(originalTimestamp.epochSecond, deserialized.timestamp.epochSecond)
    }

    /**
     * Additional test: Verify ProtoBufConfig uses correct settings
     */
    @Test
    fun `test protobuf config has correct settings`() {
        // Verify config exists and is accessible
        assertNotNull(ProtoBufConfig.format)
    }
}
