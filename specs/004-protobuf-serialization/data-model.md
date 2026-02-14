# Data Model: Kotlin Classes with ProtoBuf Annotations (v2.0.0)

**Date**: 2026-02-14  
**Version**: 2.0.0 (Breaking Change)  
**Purpose**: Annotated Kotlin data classes for kotlinx.serialization ProtoBuf format

---

## Overview

Using kotlinx.serialization's ProtoBuf format with existing data classes. Add `@ProtoNumber` annotations for explicit field numbering and schema evolution. **v2.0.0 uses Protobuf exclusively** - no JSON support.

---

## Dependencies

```kotlin
// shared/build.gradle.kts
// REPLACE kotlinx-serialization-json WITH:
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
}
```

---

## Annotated Classes

### Envelope

Top-level container with union payload type.

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf
import java.time.Instant

/**
 * Top-level envelope for all protocol messages.
 * Uses Protobuf binary format with explicit field numbering.
 */
@Serializable
class Envelope(
    @ProtoNumber(1)
    val correlationId: String,
    
    @ProtoNumber(2)
    val type: MessageType,
    
    @ProtoNumber(3)
    @Contextual  // Uses InstantSerializer
    val timestamp: Instant = Instant.now(),
    
    @ProtoNumber(4)
    val payload: Payload  // Sealed class union
)
```

**Field Numbers**: 1-4 reserved, 5-10 available for future envelope fields

---

### Payload (Sealed Class Union)

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Union type for all payload variants.
 * kotlinx.serialization handles polymorphism via @SerialName.
 */
@Serializable
sealed class Payload {
    @Serializable
    @SerialName("request")
    data class Request(
        @ProtoNumber(1)
        val data: RequestPayload
    ) : Payload()
    
    @Serializable
    @SerialName("response")
    data class Response(
        @ProtoNumber(1)
        val data: ResponsePayload
    ) : Payload()
    
    @Serializable
    @SerialName("error")
    data class Error(
        @ProtoNumber(1)
        val data: ErrorPayload
    ) : Payload()
    
    @Serializable
    @SerialName("control")
    data class Control(
        @ProtoNumber(1)
        val data: ControlPayload
    ) : Payload()
}
```

---

### RequestPayload

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * HTTP request payload for forwarding.
 */
@Serializable
class RequestPayload(
    @ProtoNumber(1)
    val method: String,
    
    @ProtoNumber(2)
    val path: String,
    
    @ProtoNumber(3)
    val headers: Map<String, String>,
    
    @ProtoNumber(4)
    val query: Map<String, String>? = null,
    
    @ProtoNumber(5)
    val body: ByteArray? = null,  // Raw binary (v2.0.0 breaking change)
    
    @ProtoNumber(6)
    val webSocketUpgrade: Boolean = false
)
```

**Field Numbers**: 1-6 reserved, 7-10 available

**Breaking Change (v2.0.0)**: `body` changed from `String` (Base64) to `ByteArray` (raw binary)

---

### ResponsePayload

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * HTTP response payload for returning to caller.
 */
@Serializable
class ResponsePayload(
    @ProtoNumber(1)
    val statusCode: Int,
    
    @ProtoNumber(2)
    val headers: Map<String, String>,
    
    @ProtoNumber(3)
    val body: ByteArray? = null  // Raw binary (v2.0.0 breaking change)
)
```

**Field Numbers**: 1-3 reserved, 4-10 available

**Breaking Change (v2.0.0)**: `body` changed from `String` (Base64) to `ByteArray` (raw binary)

---

### ErrorPayload

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Error indication payload.
 */
@Serializable
class ErrorPayload(
    @ProtoNumber(1)
    val code: ErrorCode,
    
    @ProtoNumber(2)
    val message: String
)
```

**Field Numbers**: 1-2 reserved, 3-10 available

---

### ControlPayload

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Control message payload for administrative actions.
 */
@Serializable
class ControlPayload(
    @ProtoNumber(1)
    val action: String,
    
    @ProtoNumber(2)
    val subdomain: String? = null,
    
    @ProtoNumber(3)
    val publicUrl: String? = null
) {
    companion object {
        const val ACTION_REGISTER = "REGISTER"
        const val ACTION_REGISTERED = "REGISTERED"
        const val ACTION_UNREGISTER = "UNREGISTER"
        const val ACTION_HEARTBEAT = "HEARTBEAT"
        const val ACTION_STATUS = "STATUS"
    }
}
```

**Field Numbers**: 1-3 reserved, 4-10 available

---

## Enums

### MessageType

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable

/**
 * Message type discriminator.
 * Enum ordinal becomes ProtoBuf enum value.
 */
@Serializable
enum class MessageType {
    REQUEST,    // = 0
    RESPONSE,   // = 1
    ERROR,      // = 2
    CONTROL     // = 3
}
```

---

### ErrorCode

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.Serializable

/**
 * Error classification codes.
 */
@Serializable
enum class ErrorCode {
    TIMEOUT,           // = 0
    UPSTREAM_ERROR,    // = 1
    INVALID_REQUEST,   // = 2
    SERVER_ERROR,      // = 3
    RATE_LIMITED       // = 4
}
```

---

## Serialization Utilities

### ProtoBuf Configuration

```kotlin
package org.relay.shared.protocol

import kotlinx.serialization.protobuf.ProtoBuf
import java.time.Instant

/**
 * Global ProtoBuf configuration for Relay protocol (v2.0.0).
 */
val RelayProtoBuf = ProtoBuf {
    encodeDefaults = true
}

/**
 * Extension functions for ProtoBuf serialization.
 */
inline fun <reified T> T.toProtoBuf(): ByteArray = 
    RelayProtoBuf.encodeToByteArray(serializer(), this)

inline fun <reified T> ByteArray.toObject(): T = 
    RelayProtoBuf.decodeFromByteArray(serializer(), this)

fun Envelope.toProtoBufBytes(): ByteArray = toProtoBuf()
fun ByteArray.toEnvelope(): Envelope = toObject()
```

**v2.0.0 Breaking Change**: JSON serialization utilities removed. Use `toProtoBuf()` and `toObject()` only.

---

## Field Numbering Strategy

### Reserved Ranges

| Class | Reserved | Available |
|-------|----------|-----------|
| Envelope | 1-4 | 5-10 |
| RequestPayload | 1-6 | 7-10 |
| ResponsePayload | 1-3 | 4-10 |
| ErrorPayload | 1-2 | 3-10 |
| ControlPayload | 1-3 | 4-10 |

### Extension Strategy

- Add new fields with next available number
- Never reuse field numbers
- Deprecate by removing usage, not number
- Document reserved numbers for removed fields

---

## Backward Compatibility

### v2.0.0 Breaking Changes

| Change | Impact |
|--------|--------|
| `body: String` → `body: ByteArray` | Breaking - raw binary instead of Base64 |
| JSON serialization removed | Breaking - no JSON support |
| Protobuf only | Breaking - v1.x clients incompatible |

### Safe Changes (within v2.x)

- Adding new optional fields
- Adding new enum values
- Adding new payload types (to sealed class)

### Breaking Changes (require major version bump)

- Removing fields
- Changing field types
- Changing field numbers
- Removing enum values

---

## Wire Format

kotlinx.serialization-protobuf produces standard protobuf binary:

| Feature | Encoding |
|---------|----------|
| Field numbers | Varint (key = field_number << 3 \| wire_type) |
| Strings | Length-delimited (wire type 2) |
| Embedded messages | Length-delimited |
| Enums | Varint (ordinal value) |
| Maps | Repeated key-value messages |

---

## Size Comparison

Typical REQUEST payload:

| Format | Size | Notes |
|--------|------|-------|
| JSON (v1.x) | ~350 bytes | Human-readable, field names repeated |
| Protobuf (v2.0.0) | ~150 bytes | Binary, field numbers, raw binary body |
| **Savings** | **~57%** | Exceeds 30% target |
