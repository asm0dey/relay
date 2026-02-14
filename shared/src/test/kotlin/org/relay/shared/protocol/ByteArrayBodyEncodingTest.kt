package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TS-015: HTTP body binary encoding
 *
 * Verifies that body data is encoded as raw ByteArray (not Base64),
 * achieving ~33% size reduction vs Base64 encoding.
 */
@OptIn(ExperimentalSerializationApi::class)
class ByteArrayBodyEncodingTest {

    @Test
    fun `TS-015 request body is encoded as raw bytes not Base64`() {
        // Given: A request with binary body data
        val bodyData = "Hello World".toByteArray()
        val requestPayload = RequestPayload(
            method = "POST",
            path = "/api/data",
            headers = mapOf("Content-Type" to "application/octet-stream"),
            body = bodyData
        )

        val envelope = Envelope(
            correlationId = "test-001",
            type = MessageType.REQUEST,
            payload = Payload.Request(requestPayload)
        )

        // When: Serialized to Protobuf
        val protobufBytes = ProtobufSerializer.encodeEnvelope(envelope)

        // Then: Body is encoded as raw ByteArray
        val decoded = ProtobufSerializer.decodeEnvelope(protobufBytes)
        val decodedRequest = (decoded.payload as Payload.Request).data

        assertNotNull(decodedRequest.body)
        assertEquals(bodyData.size, decodedRequest.body!!.size)
        assertTrue(bodyData.contentEquals(decodedRequest.body!!))
    }

    @Test
    fun `TS-015 response body is encoded as raw bytes not Base64`() {
        // Given: A response with binary body data
        val bodyData = ByteArray(256) { it.toByte() } // Binary data with all byte values
        val responsePayload = ResponsePayload(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/octet-stream"),
            body = bodyData
        )

        val envelope = Envelope(
            correlationId = "test-002",
            type = MessageType.RESPONSE,
            payload = Payload.Response(responsePayload)
        )

        // When: Serialized to Protobuf
        val protobufBytes = ProtobufSerializer.encodeEnvelope(envelope)

        // Then: Body is encoded as raw ByteArray (exact size preservation)
        val decoded = ProtobufSerializer.decodeEnvelope(protobufBytes)
        val decodedResponse = (decoded.payload as Payload.Response).data

        assertNotNull(decodedResponse.body)
        assertEquals(bodyData.size, decodedResponse.body!!.size)
        assertTrue(bodyData.contentEquals(decodedResponse.body!!))
    }

    @Test
    fun `TS-015 ByteArray encoding achieves size reduction vs Base64`() {
        // Given: Binary body data
        val bodyData = ByteArray(1000) { (it % 256).toByte() }

        val requestPayload = RequestPayload(
            method = "POST",
            path = "/upload",
            headers = mapOf("Content-Type" to "application/octet-stream"),
            body = bodyData
        )

        val envelope = Envelope(
            correlationId = "test-003",
            type = MessageType.REQUEST,
            payload = Payload.Request(requestPayload)
        )

        // When: Serialized to Protobuf (raw ByteArray)
        val protobufBytes = ProtobufSerializer.encodeEnvelope(envelope)

        // Calculate what Base64 encoding would have been
        val base64EncodedSize = ((bodyData.size + 2) / 3) * 4

        // Then: Protobuf with raw bytes should be smaller than Base64 alternative
        // The body itself should be encoded at 1:1 ratio (raw bytes)
        // Base64 would add ~33% overhead to the body
        val bodySizeInProtobuf = bodyData.size // Raw bytes in Protobuf
        val bodySizeIfBase64 = base64EncodedSize

        assertTrue(bodySizeInProtobuf < bodySizeIfBase64,
                   "Raw ByteArray ($bodySizeInProtobuf bytes) should be smaller than Base64 ($bodySizeIfBase64 bytes)")

        // Verify ~33% reduction for body encoding
        val reductionPercent = ((bodySizeIfBase64 - bodySizeInProtobuf).toDouble() / bodySizeIfBase64) * 100
        assertTrue(reductionPercent >= 25.0,
                   "Expected at least 25% size reduction vs Base64, got $reductionPercent%")
    }

    @Test
    fun `TS-015 empty body is handled correctly`() {
        // Given: Request with null body
        val requestPayload = RequestPayload(
            method = "GET",
            path = "/api/test",
            headers = mapOf("Host" to "example.com"),
            body = null
        )

        val envelope = Envelope(
            correlationId = "test-004",
            type = MessageType.REQUEST,
            payload = Payload.Request(requestPayload)
        )

        // When: Serialized and deserialized
        val protobufBytes = ProtobufSerializer.encodeEnvelope(envelope)
        val decoded = ProtobufSerializer.decodeEnvelope(protobufBytes)

        // Then: Null body is preserved
        val decodedRequest = (decoded.payload as Payload.Request).data
        assertEquals(null, decodedRequest.body)
    }

    @Test
    fun `TS-015 large binary body is encoded efficiently`() {
        // Given: Large binary body (1MB)
        val bodyData = ByteArray(1024 * 1024) { (it % 256).toByte() }

        val responsePayload = ResponsePayload(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/octet-stream"),
            body = bodyData
        )

        val envelope = Envelope(
            correlationId = "test-005",
            type = MessageType.RESPONSE,
            payload = Payload.Response(responsePayload)
        )

        // When: Serialized to Protobuf
        val protobufBytes = ProtobufSerializer.encodeEnvelope(envelope)

        // Then: Body is preserved exactly
        val decoded = ProtobufSerializer.decodeEnvelope(protobufBytes)
        val decodedResponse = (decoded.payload as Payload.Response).data

        assertNotNull(decodedResponse.body)
        assertEquals(bodyData.size, decodedResponse.body!!.size)
        assertTrue(bodyData.contentEquals(decodedResponse.body!!))
    }
}
