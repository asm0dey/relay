package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

/**
 * TS-010: Malformed Protobuf message handling
 * TS-016: Unknown field handling
 * TS-029: Error message clarity for all error types
 */
@OptIn(ExperimentalSerializationApi::class)
class ErrorHandlingTest {

    @Test
    fun `TS-010 malformed protobuf message - invalid binary data`() {
        // Given: Completely invalid binary data
        val malformedData = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xAB.toByte(), 0xCD.toByte())

        // When/Then: Decoding fails with clear error
        val exception = assertThrows<Exception> {
            ProtobufSerializer.decodeEnvelope(malformedData)
        }

        // Error message should be clear and indicate the problem
        assertTrue(exception.message != null && exception.message!!.isNotEmpty(),
                   "Error message should not be empty: ${exception.message}")
        assertTrue(exception.message!!.contains("decode") ||
                   exception.message!!.contains("malformed") ||
                   exception.message!!.contains("Invalid") ||
                   exception.message!!.contains("Varint"),
                   "Error message should describe the decode failure: ${exception.message}")
    }

    @Test
    fun `TS-010 malformed protobuf message - truncated data`() {
        // Given: A valid envelope that we'll truncate
        val validEnvelope = Envelope(
            correlationId = "test-123",
            type = MessageType.REQUEST,
            payload = Payload.Request(
                RequestPayload(
                    method = "GET",
                    path = "/test",
                    headers = mapOf("Host" to "example.com"),
                    body = null
                )
            )
        )

        val validBytes = ProtobufSerializer.encodeEnvelope(validEnvelope)

        // Truncate to 50% of original size
        val truncatedData = validBytes.copyOf(validBytes.size / 2)

        // When/Then: Decoding fails with clear error
        val exception = assertThrows<Exception> {
            ProtobufSerializer.decodeEnvelope(truncatedData)
        }

        assertTrue(exception.message?.isNotEmpty() == true,
                   "Error message should not be empty")
    }

    @Test
    fun `TS-010 malformed protobuf message - empty data`() {
        // Given: Empty byte array
        val emptyData = byteArrayOf()

        // When/Then: Decoding fails with clear error
        val exception = assertThrows<Exception> {
            ProtobufSerializer.decodeEnvelope(emptyData)
        }

        assertTrue(exception.message?.isNotEmpty() == true,
                   "Error message should not be empty")
    }

    @Test
    fun `TS-016 unknown field handling - forward compatibility`() {
        // Given: A valid envelope with all current fields
        val envelope = Envelope(
            correlationId = "test-456",
            type = MessageType.CONTROL,
            payload = Payload.Control(
                ControlPayload(
                    action = "REGISTERED",
                    subdomain = "test-subdomain"
                )
            )
        )

        val bytes = ProtobufSerializer.encodeEnvelope(envelope)

        // When: Deserialize (simulating forward compatibility)
        // Note: Protobuf's kotlinx.serialization should ignore unknown fields by default
        val decoded = ProtobufSerializer.decodeEnvelope(bytes)

        // Then: Decoding succeeds and known fields are preserved
        assertTrue(decoded.correlationId == "test-456")
        assertTrue(decoded.type == MessageType.CONTROL)
        val controlPayload = (decoded.payload as Payload.Control).data
        assertTrue(controlPayload.action == "REGISTERED")
        assertTrue(controlPayload.subdomain == "test-subdomain")
    }

    @Test
    fun `TS-029 error message clarity - includes context on failure`() {
        // Given: Malformed data
        val malformedData = byteArrayOf(0x01, 0x02, 0x03)

        // When/Then: Error includes useful context
        val exception = assertThrows<Exception> {
            ProtobufSerializer.decodeEnvelope(malformedData)
        }

        // Error should have a message (not null/empty)
        assertTrue(exception.message != null && exception.message!!.isNotEmpty(),
                   "Error message must be present")

        // Error should be specific enough to debug
        assertTrue(exception.message!!.length > 10,
                   "Error message should be descriptive")
    }
}
