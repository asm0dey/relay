# Test Specifications: Protobuf Serialization Migration (v2.0.0)

**Generated**: 2026-02-14  
**Version**: 2.0.0 (Breaking Change)  
**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

## TDD Assessment

**Determination**: MANDATORY  
**Confidence**: HIGH  
**Evidence**: "Test-First Verification (NON-NEGOTIABLE) - All functionality must be verifiable through automated tests. Test specifications define expected behavior before implementation. The Red-Green-Refactor cycle is mandatory: write failing tests, implement to make them pass, then refactor while maintaining green tests."  
**Reasoning**: Constitution explicitly requires test-first development with mandatory Red-Green-Refactor cycle. This is marked as NON-NEGOTIABLE.

---

<!--
DO NOT MODIFY TEST ASSERTIONS

These test specifications define the expected behavior derived from requirements.
During implementation:
- Fix code to pass tests, don't modify test assertions
- Structural changes (file organization, naming) are acceptable with justification
- Logic changes to assertions require explicit justification and re-review

If requirements change, re-run /iikit-05-testify to regenerate test specs.
-->

## From spec.md (Acceptance Tests)

### TS-001: Bandwidth reduction - single message size

**Source**: spec.md:User Story 1:scenario-1  
**Type**: acceptance  
**Priority**: P1

**Given**: A REQUEST message with typical headers and body payload (1KB-100KB representative data)
**When**: Serialized to Protobuf vs baseline JSON
**Then**: The Protobuf representation uses at least 25% fewer bytes

**Traceability**: FR-001, SC-001, US-001-scenario-1

---

### TS-002: Bandwidth reduction - connection load

**Source**: spec.md:User Story 1:scenario-2  
**Type**: acceptance  
**Priority**: P1

**Given**: A tunnel connection under load with 1000 sequential requests  
**When**: Measuring total bytes transferred  
**Then**: Protobuf serialization shows reduced bandwidth compared to baseline JSON measurements

**Traceability**: FR-001, SC-001, US-001-scenario-2

---

### TS-003: Serialization performance - encode/decode speed

**Source**: spec.md:User Story 2:scenario-1  
**Type**: acceptance  
**Priority**: P2

**Given**: A suite of representative messages (REQUEST, RESPONSE, ERROR, CONTROL)  
**When**: Measuring encode/decode time for 10,000 iterations  
**Then**: Protobuf serialization completes in less time than JSON serialization

**Traceability**: FR-001, SC-002, US-002-scenario-1

---

### TS-004: End-to-end latency - no regression

**Source**: spec.md:User Story 2:scenario-2  
**Type**: acceptance  
**Priority**: P2

**Given**: A loaded tunnel with concurrent requests (load test scenario)  
**When**: Measuring end-to-end latency  
**Then**: The Protobuf-based system shows no regression (p99 within 10% of JSON baseline) and ideally shows improvement

**Traceability**: SC-004, US-002-scenario-2

---

### TS-005: Migration documentation exists

**Source**: spec.md:MIGRATION.md requirement  
**Type**: acceptance  
**Priority**: P1

**Given**: A developer upgrading from v1.x to v2.0.0  
**When**: Reviewing MIGRATION.md  
**Then**: Clear breaking change documentation exists explaining the v2.0.0 upgrade

**Traceability**: Breaking change documentation requirement

---

## From spec.md (Functional Requirement Tests)

### TS-010: Malformed Protobuf message handling

**Source**: spec.md:FR-007, Edge Case #1  
**Type**: functional  
**Priority**: P1

**Given**: A WebSocket connection established  
**When**: A malformed Protobuf message is received (invalid binary, truncated, unknown type)  
**Then**: The message is rejected with appropriate error handling and clear error message

**Traceability**: FR-007, FR-008

---

### TS-011: Correlation ID preservation

**Source**: spec.md:FR-003  
**Type**: functional  
**Priority**: P1

**Given**: A REQUEST message with correlation ID "abc-123"  
**When**: Serialized to Protobuf and deserialized  
**Then**: The correlation ID "abc-123" is preserved exactly

**Traceability**: FR-003

---

### TS-012: Message type structure preservation

**Source**: spec.md:FR-002  
**Type**: functional  
**Priority**: P1

**Given**: All four message types (REQUEST, RESPONSE, ERROR, CONTROL)  
**When**: Each is serialized to Protobuf and deserialized  
**Then**: The message type structure is preserved with equivalent field mappings

**Traceability**: FR-002

---

### TS-013: Timestamp precision preservation

**Source**: spec.md:FR-004  
**Type**: functional  
**Priority**: P2

**Given**: An Envelope with timestamp "2026-02-13T10:30:00.123Z"  
**When**: Serialized to Protobuf and deserialized  
**Then**: Timestamp precision is equivalent to JSON implementation (millisecond accuracy)

**Traceability**: FR-004

---

### TS-014: All payload types supported

**Source**: spec.md:FR-005  
**Type**: functional  
**Priority**: P1

**Given**: Payloads of types RequestPayload, ResponsePayload, ErrorPayload, ControlPayload  
**When**: Each is embedded in an Envelope and serialized to Protobuf  
**Then**: All payload types are correctly serialized and deserialized

**Traceability**: FR-005, SC-005

---

### TS-015: HTTP body binary encoding

**Source**: spec.md:FR-006, plan.md:Body Encoding  
**Type**: functional  
**Priority**: P1

**Given**: A RequestPayload or ResponsePayload with binary body data  
**When**: Serialized to Protobuf  
**Then**: The body is encoded as raw ByteArray (not Base64), achieving ~33% size reduction vs Base64

**Traceability**: FR-006

---

### TS-016: Unknown field handling

**Source**: spec.md:Edge Case #4, Compatibility Considerations  
**Type**: functional  
**Priority**: P2

**Given**: A Protobuf message with unknown fields (forward compatibility test)  
**When**: Deserialized by current schema  
**Then**: Unknown fields are preserved or handled gracefully without error

**Traceability**: Compatibility Considerations

---

## From plan.md (Contract Tests)

### TS-019: WebSocket protocol contract - ProtoBuf envelope structure

**Source**: plan.md:data-model.md:Envelope  
**Type**: contract  
**Priority**: P1

**Given**: API expects Envelope with correlation_id, type, timestamp, payload  
**When**: Request is made with valid Protobuf binary Envelope  
**Then**: Response envelope follows same structure with correct field numbers

**Traceability**: data-model.md:Envelope

---

### TS-020: WebSocket protocol contract - Payload union

**Source**: plan.md:data-model.md:Payload sealed class  
**Type**: contract  
**Priority**: P1

**Given**: Payload union type with Request, Response, Error, Control variants  
**When**: Each variant is serialized and deserialized  
**Then**: Correct variant is identified and data is preserved

**Traceability**: data-model.md:Payload

---

## From data-model.md (Validation Tests)

### TS-022: RequestPayload validation - method field

**Source**: data-model.md:RequestPayload  
**Type**: validation  
**Priority**: P2

**Given**: RequestPayload with method field  
**When**: Method is empty or invalid  
**Then**: Validation error is returned (serialization fails or validation catches it)

**Traceability**: data-model.md:RequestPayload

---

### TS-023: RequestPayload validation - path field

**Source**: data-model.md:RequestPayload  
**Type**: validation  
**Priority**: P2

**Given**: RequestPayload with path field  
**When**: Path does not start with "/"  
**Then**: Validation error is returned

**Traceability**: data-model.md:RequestPayload

---

### TS-024: ResponsePayload validation - status code range

**Source**: data-model.md:ResponsePayload  
**Type**: validation  
**Priority**: P2

**Given**: ResponsePayload with status_code field  
**When**: Status code is outside valid HTTP range (100-599)  
**Then**: Validation error is returned

**Traceability**: data-model.md:ResponsePayload

---

### TS-025: ByteArray body size limits

**Source**: data-model.md:RequestPayload, data-model.md:ResponsePayload  
**Type**: validation  
**Priority**: P2

**Given**: RequestPayload or ResponsePayload with body field  
**When**: ByteArray body exceeds 10MB  
**Then**: Validation error or size limit enforcement occurs

**Traceability**: data-model.md:RequestPayload, data-model.md:ResponsePayload

---

## From Success Criteria (Performance/Quality Tests)

### TS-026: Serialization time under 1ms

**Source**: spec.md:SC-002  
**Type**: performance  
**Priority**: P1

**Given**: Messages up to 1MB in size  
**When**: Serialization and deserialization operations are performed  
**Then**: Each operation completes in under 1ms (measured average over 1000 iterations)

**Traceability**: SC-002

---

### TS-027: Memory usage constraint

**Source**: spec.md:SC-006  
**Type**: performance  
**Priority**: P2

**Given**: Equivalent load with JSON and Protobuf serializers  
**When**: Measuring memory usage during serialization  
**Then**: Protobuf memory usage does not exceed 150% of JSON baseline

**Traceability**: SC-006

---

### TS-028: Protocol test compatibility

**Source**: spec.md:SC-003
**Type**: regression
**Priority**: P1

**Given**: All existing protocol tests
**When**: Run with Protobuf serialization instead of JSON
**Then**: All tests pass with updated assertions for Protobuf format

**Traceability**: SC-003

---

### TS-029: Error message clarity for all error types

**Source**: spec.md:FR-008, FR-009
**Type**: functional
**Priority**: P1

**Given**: Various error conditions occur (malformed Protobuf, serialization failure, version incompatibility, validation failure)
**When**: Error is reported to client or logged
**Then**: Error message includes correlation ID (if available), message type context, specific failure reason, and actionable guidance

**Acceptance Criteria**:
- Malformed Protobuf error includes: "Invalid Protobuf binary" + byte offset if available
- Serialization failure includes: field name that failed + expected type
- Version incompatibility (FR-009) includes: "Protocol version mismatch: v1.x JSON client incompatible with v2.0.0 Protobuf server"
- Validation failure includes: which validation rule failed (e.g., "Missing required field: correlationId")
- All errors include correlation ID when available for request tracing

**Traceability**: FR-008, FR-009

---

## Summary

| Source | Count | Types |
|--------|-------|-------|
| spec.md (acceptance) | 5 | acceptance |
| spec.md (functional) | 7 | functional |
| plan.md (contract) | 2 | contract |
| data-model.md (validation) | 4 | validation |
| Success Criteria | 3 | performance/regression |
| **Total** | **21** | |

---

## Test Priority Summary

| Priority | Count | Test IDs |
|----------|-------|----------|
| P1 | 13 | TS-001, TS-002, TS-005, TS-010, TS-011, TS-012, TS-014, TS-015, TS-019, TS-020, TS-026, TS-028, TS-029 |
| P2 | 8 | TS-003, TS-004, TS-013, TS-016, TS-022, TS-023, TS-024, TS-025, TS-027 |

---

## Removed Tests (v2.0.0 Breaking Change)

| Test ID | Original Purpose | Reason for Removal |
|---------|------------------|-------------------|
| TS-006 | Mixed environment support | Not applicable - no JSON support |
| TS-007 | Protobuf format negotiation | Not applicable - no negotiation |
| TS-008 | JSON default mode | Not applicable - no JSON support |
| TS-009 | Format negotiation timeout | Not applicable - no negotiation |
| TS-017 | Invalid format after negotiation | Not applicable - no negotiation |
| TS-018 | Format mode isolation | Not applicable - single format |
| TS-021 | Format negotiator contract | Not applicable - class removed |

---

## Implementation Notes

- Tests should be written BEFORE implementation (TDD mandatory per constitution)
- Each test should fail initially (Red), pass after implementation (Green)
- Refactor while maintaining green tests
- Performance tests (TS-026, TS-027) need baseline measurements for JSON first
- Contract tests verify format compatibility with protocol specification
- Breaking change: All tests assume Protobuf-only protocol
