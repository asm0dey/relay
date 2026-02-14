# WebSocket Protocol Contract v2.0.0

**Version**: 2.0.0  
**Status**: Breaking Change (No JSON Support)  
**Purpose**: Define binary Protobuf message format for client-server communication using kotlinx.serialization

---

## Overview

This contract defines the binary Protobuf protocol for Relay tunnel communication. **Version 2.0.0 is a breaking change** - all messages use Protobuf binary format exclusively. No JSON support is provided.

Uses kotlinx.serialization's ProtoBuf format with annotated Kotlin data classes.

---

## Connection Protocol

### Protocol Flow

```
Client                                         Server
  |                                              |
  |--- WebSocket handshake (HTTP Upgrade) ----->|
  |<-- Connection established ------------------|
  |                                              |
  |--- Binary: Envelope(REQUEST) ------------->|  [Protobuf mode]
  |<-- Binary: Envelope(RESPONSE) --------------|
  |                                              |
```

**No format negotiation** - First message is Protobuf binary immediately.

---

## Message Schema

Messages are Kotlin data classes serialized with kotlinx.serialization ProtoBuf format.

### Envelope

```kotlin
@Serializable
class Envelope(
    @ProtoNumber(1) val correlationId: String,
    @ProtoNumber(2) val type: MessageType,
    @ProtoNumber(3) @Contextual val timestamp: Instant,
    @ProtoNumber(4) val payload: Payload
)
```

### Payload Union (Sealed Class)

```kotlin
@Serializable
sealed class Payload {
    @Serializable @SerialName("request") data class Request(@ProtoNumber(1) val data: RequestPayload) : Payload()
    @Serializable @SerialName("response") data class Response(@ProtoNumber(1) val data: ResponsePayload) : Payload()
    @Serializable @SerialName("error") data class Error(@ProtoNumber(1) val data: ErrorPayload) : Payload()
    @Serializable @SerialName("control") data class Control(@ProtoNumber(1) val data: ControlPayload) : Payload()
    @Serializable @SerialName("websocket_frame") data class WebSocketFrame(@ProtoNumber(1) val data: WebSocketFramePayload) : Payload()
}
```

### RequestPayload

```kotlin
@Serializable
class RequestPayload(
    @ProtoNumber(1) val method: String,
    @ProtoNumber(2) val path: String,
    @ProtoNumber(3) val headers: Map<String, String>,
    @ProtoNumber(4) val query: Map<String, String>?,
    @ProtoNumber(5) val body: ByteArray?,  // Raw binary
    @ProtoNumber(6) val webSocketUpgrade: Boolean
)
```

### ResponsePayload

```kotlin
@Serializable
class ResponsePayload(
    @ProtoNumber(1) val statusCode: Int,
    @ProtoNumber(2) val headers: Map<String, String>,
    @ProtoNumber(3) val body: ByteArray?  // Raw binary
)
```

### ErrorPayload

```kotlin
@Serializable
class ErrorPayload(
    @ProtoNumber(1) val code: ErrorCode,
    @ProtoNumber(2) val message: String
)
```

### ControlPayload

```kotlin
@Serializable
class ControlPayload(
    @ProtoNumber(1) val action: String,
    @ProtoNumber(2) val subdomain: String?,
    @ProtoNumber(3) val publicUrl: String?
)
```

### WebSocketFramePayload

```kotlin
@Serializable
class WebSocketFramePayload(
    @ProtoNumber(1) val type: String,          // "TEXT", "BINARY", "PING", "PONG", "CLOSE"
    @ProtoNumber(2) val data: String? = null,  // Frame data (text or base64-encoded binary)
    @ProtoNumber(3) val isBinary: Boolean = false,
    @ProtoNumber(4) val closeCode: Int? = null,
    @ProtoNumber(5) val closeReason: String? = null
)
```

---

## Enums

### MessageType

| Name | Ordinal | Description |
|------|---------|-------------|
| REQUEST | 0 | Forwarded HTTP request |
| RESPONSE | 1 | HTTP response |
| ERROR | 2 | Error indication |
| CONTROL | 3 | Administrative message |

### ErrorCode

| Name | Ordinal | Description |
|------|---------|-------------|
| TIMEOUT | 0 | Request timed out |
| UPSTREAM_ERROR | 1 | Local app error/unreachable |
| INVALID_REQUEST | 2 | Malformed request |
| SERVER_ERROR | 3 | Internal server error |
| RATE_LIMITED | 4 | Rate limit exceeded |
| PROTOCOL_ERROR | 5 | Malformed Protobuf message (v2.0.0) |

---

## Control Actions

| Action | Direction | Description |
|--------|-----------|-------------|
| REGISTER | C→S | Request tunnel registration |
| REGISTERED | S→C | Confirm registration with subdomain |
| UNREGISTER | Both | Initiate disconnect |
| HEARTBEAT | Both | Keepalive ping/pong |
| STATUS | Both | Status update/query |

---

## Connection Lifecycle

1. WebSocket handshake
2. Server sends CONTROL(REGISTERED) with assigned subdomain (first Protobuf message)
3. Normal request/response flow
4. Either party may send CONTROL(UNREGISTER) to close

---

## Timeout Handling

- Request timeout: 30 seconds (server-side)
- Heartbeat: Recommended every 30 seconds

---

## Wire Format Details

kotlinx.serialization-protobuf produces standard Protocol Buffers binary format:

- **Field numbers**: Encoded as varints (field_number << 3 | wire_type)
- **Strings/embedded messages**: Length-delimited (wire type 2)
- **Enums**: Varint (ordinal value)
- **Maps**: Repeated key-value message pairs
- **Sealed classes**: Discriminated by @SerialName, payload nested

---

## Breaking Changes from v1.0.0 (JSON)

| Aspect | v1.0.0 (JSON) | v2.0.0 (Protobuf) |
|--------|---------------|-------------------|
| **Format** | JSON text | Protobuf binary |
| **Library** | kotlinx.serialization-json | kotlinx.serialization-protobuf |
| **First Message** | JSON Envelope | Binary Envelope |
| **Negotiation** | N/A | **None** (Protobuf only) |
| **Timestamp** | ISO8601 string | Encoded via InstantSerializer |
| **Body** | Base64 string | **ByteArray (raw binary)** |
| **Envelope** | @Serializable class | @Serializable + @ProtoNumber |
| **Compatibility** | v1.x only | **v2.0.0 only** |

---

## Version Compatibility Matrix

| Client Version | Server v1.x | Server v2.0.0 |
|----------------|-------------|---------------|
| v1.x (JSON)    | ✅ Compatible | ❌ **Incompatible** |
| v2.0.0 (Protobuf) | ❌ **Incompatible** | ✅ Compatible |

**Important**: v1.x and v2.0.0 are mutually incompatible. Upgrade both client and server together.

---

## Implementation Notes

- Uses `kotlinx.serialization.protobuf.ProtoBuf` format
- `@ProtoNumber` annotations ensure consistent field numbering
- `@Contextual` timestamp uses existing `InstantSerializer`
- No .proto file - schema defined in Kotlin source
- Binary format compatible with standard protobuf decoders
- **Breaking change**: No fallback to JSON on parse errors

---

## Error Handling

If a client sends invalid data:
- **Malformed Protobuf**: Server sends ERROR with PROTOCOL_ERROR code, logs structured error with correlation ID, session ID, and message size
- **Empty message**: SerializationException with "Cannot decode Protobuf from empty byte array"
- **Valid Protobuf, invalid payload**: Error response sent with appropriate ErrorCode
- **Version mismatch (JSON from v1.x)**: Treated as malformed Protobuf, PROTOCOL_ERROR sent

No attempt is made to detect or support JSON format. All error messages include contextual information for debugging.

### Metrics and Observability

The following Micrometer metrics are tracked:
- `relay.protobuf.messages.received` - Counter by type and subdomain
- `relay.protobuf.messages.sent` - Counter by type and message_type
- `relay.protobuf.messages.by_type` - Counter by message_type and direction
- `relay.protobuf.message.size.bytes` - Summary by type and direction

All serialization errors include:
- Correlation ID (if available from envelope)
- Session ID
- Message size in bytes
- Error type and message
