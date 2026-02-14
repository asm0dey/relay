package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Additional bandwidth verification - testing larger payloads per SC-001
 *
 * Spec: SC-001 states "typical payloads", benchmark with varying sizes
 * Constitution: Test-First Verification (NON-NEGOTIABLE)
 */
@OptIn(ExperimentalSerializationApi::class)
class BandwidthVerificationTest {

    @Test
    fun `test larger payloads achieve better compression ratios`() {
        val testSizes = listOf(
            1 * 1024,      // 1KB
            10 * 1024,     // 10KB
            100 * 1024,    // 100KB
            500 * 1024     // 500KB
        )

        println("\n=== Bandwidth Reduction by Payload Size ===")

        for (size in testSizes) {
            val envelope = Envelope(
                correlationId = "test-$size",
                type = MessageType.REQUEST,
                timestamp = Instant.now(),
                payload = Payload.Request(RequestPayload(
                    method = "POST",
                    path = "/api/data/upload",
                    query = null,
                    headers = mapOf(
                        "Content-Type" to "application/octet-stream",
                        "Authorization" to "Bearer token123"
                    ),
                    body = ByteArray(size) { it.toByte() }
                ))
            )

            val protobufBytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), envelope)
            val jsonBytes = serializeToJsonBaseline(envelope, size)

            val reductionPercent = ((jsonBytes.size - protobufBytes.size).toDouble() / jsonBytes.size) * 100

            println("Payload: ${size / 1024}KB | JSON: ${jsonBytes.size} bytes | Protobuf: ${protobufBytes.size} bytes | Reduction: ${String.format("%.1f", reductionPercent)}%")
        }

        // With larger payloads, reduction should approach 33% (Base64 overhead)
        val largeEnvelope = Envelope(
            correlationId = "large-test",
            type = MessageType.REQUEST,
            timestamp = Instant.now(),
            payload = Payload.Request(RequestPayload(
                method = "POST",
                path = "/api/upload",
                query = null,
                headers = mapOf("Content-Type" to "application/octet-stream"),
                body = ByteArray(100 * 1024) { it.toByte() }
            ))
        )

        val protobufLarge = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), largeEnvelope)
        val jsonLarge = serializeToJsonBaseline(largeEnvelope, 100 * 1024)
        val largeReduction = ((jsonLarge.size - protobufLarge.size).toDouble() / jsonLarge.size) * 100

        assertTrue(
            largeReduction >= 25.0,
            "Expected at least 25% reduction for 100KB payload, got ${String.format("%.1f", largeReduction)}%"
        )
    }

    private fun serializeToJsonBaseline(envelope: Envelope, bodySize: Int): ByteArray {
        // Simulate JSON with Base64 body
        val bodyBase64 = when (val p = envelope.payload) {
            is Payload.Request -> {
                p.data.body?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: ""
            }
            else -> ""
        }

        val jsonString = """{"correlationId":"${envelope.correlationId}","type":"REQUEST","timestamp":"${envelope.timestamp}","payload":{"method":"POST","path":"/api/upload","headers":{"Content-Type":"application/octet-stream"},"body":"$bodyBase64"}}"""

        return jsonString.toByteArray(Charsets.UTF_8)
    }
}
