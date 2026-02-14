# Feature Specification: Protobuf Serialization Migration (v2.0.0)

**Feature Branch**: `004-protobuf-serialization`  
**Created**: 2026-02-13  
**Updated**: 2026-02-14  
**Status**: Draft  
**Version**: 2.0.0 (Breaking Change)  
**Input**: User description: "migrate from json serialization to protobuf"

---

## Overview

This is a **breaking change** that migrates the Relay tunneling service from JSON to Protobuf serialization for all WebSocket protocol messages. **Version 2.0.0** removes JSON support entirely - both client and server MUST use Protobuf binary format.

---

## User Scenarios & Testing

### User Story 1 - Reduced Bandwidth Usage (Priority: P1)

As a tunnel service operator, I want the protocol to use Protobuf instead of JSON so that bandwidth consumption between client and server is reduced, lowering operational costs and improving performance on slower networks.

**Why this priority**: Bandwidth reduction directly impacts hosting costs and user experience on constrained networks. Protobuf's binary format typically reduces payload size by 50-80% compared to JSON.

**Independent Test**: A benchmark comparing JSON vs Protobuf payload sizes for typical REQUEST/RESPONSE messages demonstrates measurable reduction in bytes transmitted.

**Acceptance Scenarios**:

1. **Given** a REQUEST message with typical headers and body payload, **When** serialized to Protobuf vs baseline JSON, **Then** the Protobuf representation uses at least 25% fewer bytes
2. **Given** a tunnel connection under load, **When** measuring total bytes transferred over 1000 requests, **Then** Protobuf serialization shows reduced bandwidth compared to baseline JSON measurements

---

### User Story 2 - Faster Serialization/Deserialization (Priority: P2)

As a developer using the tunnel service, I want message serialization to be faster so that request latency is reduced and more requests can be handled per second.

**Why this priority**: CPU efficiency improvements enable higher throughput and lower latency. Protobuf is typically 5-10x faster than JSON parsing for structured data.

**Independent Test**: A performance benchmark demonstrates that Protobuf encoding/decoding is measurably faster than JSON for the same message types.

**Acceptance Scenarios**:

1. **Given** a suite of representative messages (1KB-100KB typical payloads), **When** measuring encode/decode time for 10,000 iterations, **Then** Protobuf serialization demonstrates operational efficiency
2. **Given** a loaded tunnel with concurrent requests, **When** measuring end-to-end latency, **Then** the Protobuf-based system shows no regression and ideally shows improvement over JSON baseline

---

### Edge Cases

- What happens when a malformed Protobuf message is received? → **FR-007** (validation and rejection)
- How does the system handle version mismatches between client and server protocol definitions? → **FR-009** (fail fast with clear error)
- What is the fallback behavior if Protobuf serialization fails? → **No fallback** (v2.0.0 breaking change removes JSON support)
- How are unknown fields in Protobuf messages handled for forward compatibility? → **Protobuf preserves unknown fields** for future extension compatibility

---

## Requirements

### Functional Requirements

- **FR-001**: System MUST serialize all WebSocket protocol messages using Protobuf binary format
- **FR-002**: System MUST maintain the existing message type structure (REQUEST, RESPONSE, ERROR, CONTROL) with equivalent field mappings
- **FR-003**: System MUST preserve correlation ID semantics for request/response matching
- **FR-004**: System MUST handle timestamp serialization with equivalent precision to current JSON implementation
- **FR-005**: System MUST support all existing payload types: RequestPayload, ResponsePayload, ErrorPayload, ControlPayload
- **FR-006**: System MUST encode HTTP bodies as raw bytes (ByteArray), not Base64 strings
- **FR-007**: System MUST validate incoming Protobuf messages and reject malformed data with appropriate error handling
- **FR-008**: System MUST provide clear error messages when serialization/deserialization failures occur
- **FR-009**: System MUST fail fast with clear error when v1.x (JSON) and v2.0.0 (Protobuf) clients/servers attempt to connect, preventing silent protocol incompatibility

### Key Entities

- **Envelope**: Container message with correlation ID, message type, timestamp, and payload. Remains the top-level protocol wrapper.
- **MessageType**: Enumeration of REQUEST, RESPONSE, ERROR, CONTROL. Preserved in Protobuf enum definition.
- **RequestPayload**: HTTP request data including method, path, headers, query parameters, and body.
- **ResponsePayload**: HTTP response data including status code, headers, and body.
- **ErrorPayload**: Error information with error code and message.
- **ControlPayload**: Control actions for tunnel registration, heartbeats, and status.
- **ErrorCode**: Enumeration of error types (TIMEOUT, UPSTREAM_ERROR, INVALID_REQUEST, SERVER_ERROR, RATE_LIMITED).

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: Protobuf-serialized messages are at least 25% smaller than equivalent JSON messages for typical payloads
- **SC-002**: Serialization and deserialization operations are measurably faster than JSON baseline for typical payloads (1KB-100KB)
- **SC-003**: All existing protocol tests pass with Protobuf serialization without modification to test logic
- **SC-004**: No regression in end-to-end tunnel request latency (p99 latency remains within 10% of JSON baseline)
- **SC-005**: System correctly handles 100% of valid protocol message types through Protobuf encoding/decoding
- **SC-006**: Memory usage during serialization does not exceed 150% of JSON baseline under equivalent load

---

## Protocol Contract Updates

### Previous JSON Format (v1.x - Deprecated)

The previous protocol used JSON envelopes as documented in `specs/001-relay-tunnel/contracts/websocket-protocol.md`. This format is **no longer supported** in v2.0.0.

### Protobuf Format (v2.0.0)

The new protocol uses Protobuf binary format exclusively:

1. **Envelope Structure**: correlation_id (string), type (enum), timestamp (int64), payload (oneof)
2. **Payload Encoding**: Each payload type serializable within the envelope using sealed class union
3. **Field Numbering**: Consistent field numbers for forward/backward compatibility
4. **Wire Format**: Binary protobuf format (not JSON-over-protobuf)

### Compatibility Considerations

- Protocol buffer definitions use field numbers that allow future extension
- Unknown fields are preserved or handled gracefully
- **Breaking Change**: v1.x JSON clients are incompatible with v2.0.0 servers
- **Breaking Change**: v2.0.0 clients are incompatible with v1.x JSON servers

---

## Migration Notes

### Version 2.0.0 Breaking Changes

This release removes all JSON support. Migration requires:

1. **Upgrade both client and server together** - No backward compatibility
2. **Update client dependencies** to v2.0.0
3. **Update server deployment** to v2.0.0
4. **No format negotiation** - All connections use Protobuf immediately

### Version Compatibility Matrix

| Client Version | Server v1.x | Server v2.0.0 |
|----------------|-------------|---------------|
| v1.x (JSON)    | ✅ Compatible | ❌ Incompatible |
| v2.0.0 (Protobuf) | ❌ Incompatible | ✅ Compatible |

---

## Clarifications

### Session 2026-02-13

- Q: Backward compatibility strategy for migration? -> A: **BREAKING CHANGE** - v2.0.0 removes JSON support entirely; client and server must upgrade together
- Q: Which Protobuf library to use for Kotlin Multiplatform? -> A: `kotlinx.serialization-protobuf` - already using kotlinx.serialization, minimal changes
