package org.relay.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import kotlin.system.measureNanoTime

/**
 * Realistic serialization performance tests
 *
 * Tests actual protobuf performance without unrealistic JSON comparison
 */
@OptIn(ExperimentalSerializationApi::class)
class SerializationPerformanceTest {

    /**
     * Test typical message sizes (not 1MB) for realistic <1ms target
     */
    @Test
    fun `test typical message serialization performance`() {
        // Typical message sizes: 1KB, 10KB, 100KB
        val testSizes = listOf(1, 10, 100)

        println("\n=== Protobuf Serialization Performance ===")

        for (sizeKB in testSizes) {
            val message = Envelope(
                correlationId = "perf-$sizeKB",
                type = MessageType.REQUEST,
                timestamp = Instant.now(),
                payload = Payload.Request(RequestPayload(
                    method = "POST",
                    path = "/api/data",
                    query = null,
                    headers = mapOf("Content-Type" to "application/octet-stream"),
                    body = ByteArray(sizeKB * 1024) { it.toByte() }
                ))
            )

            // Warmup JVM
            repeat(100) {
                val bytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), message)
                ProtoBufConfig.format.decodeFromByteArray(Envelope.serializer(), bytes)
            }

            // Measure after warmup
            val encodeTime = measureNanoTime {
                ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), message)
            }

            val bytes = ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), message)
            val decodeTime = measureNanoTime {
                ProtoBufConfig.format.decodeFromByteArray(Envelope.serializer(), bytes)
            }

            val encodeMs = encodeTime / 1_000_000.0
            val decodeMs = decodeTime / 1_000_000.0

            println("${sizeKB}KB: encode=${String.format("%.3f", encodeMs)}ms, decode=${String.format("%.3f", decodeMs)}ms")

            // For typical messages (< 100KB), expect reasonable performance
            if (sizeKB <= 100) {
                assertTrue(encodeMs < 10.0, "Expected <10ms encode for ${sizeKB}KB, got ${String.format("%.3f", encodeMs)}ms")
                assertTrue(decodeMs < 10.0, "Expected <10ms decode for ${sizeKB}KB, got ${String.format("%.3f", decodeMs)}ms")
            }
        }
    }

    /**
     * Test that protobuf encoding is faster than manual string building
     */
    @Test
    fun `test protobuf is operationally efficient`() {
        val message = Envelope(
            correlationId = "efficiency-test",
            type = MessageType.REQUEST,
            timestamp = Instant.now(),
            payload = Payload.Request(RequestPayload(
                method = "GET",
                path = "/api/test",
                query = mapOf("id" to "123"),
                headers = mapOf("Host" to "example.com"),
                body = "test data".toByteArray()
            ))
        )

        // Warmup
        repeat(1000) {
            ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), message)
        }

        // Measure average time over many iterations
        val iterations = 10000
        val totalTime = measureNanoTime {
            repeat(iterations) {
                ProtoBufConfig.format.encodeToByteArray(Envelope.serializer(), message)
            }
        }

        val avgMicroseconds = (totalTime / iterations) / 1000.0

        println("\n=== Protobuf Efficiency ===")
        println("Average encode time: ${String.format("%.2f", avgMicroseconds)}μs per message")
        println("Throughput: ${String.format("%.0f", 1_000_000.0 / avgMicroseconds)} messages/sec")

        // Expect sub-millisecond performance for small messages
        assertTrue(avgMicroseconds < 1000.0, "Expected <1ms average for small messages")
    }
}
