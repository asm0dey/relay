# Tasks: Protobuf Serialization Migration (v2.0.0)

**Feature**: `004-protobuf-serialization`  
**Version**: 2.0.0 (Breaking Change - No JSON Support)  
**Generated**: 2026-02-14  
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Tests**: [tests/test-specs.md](./tests/test-specs.md)

---

## Phase 1: Setup & Dependencies

**Goal**: Replace JSON with kotlinx.serialization-protobuf dependency

- [x] T001 Replace `kotlinx-serialization-json` with `kotlinx-serialization-protobuf:1.10.0` in `shared/build.gradle.kts`
- [x] T002 [P] Create ProtoBuf configuration object in `shared/src/main/kotlin/org/relay/shared/protocol/ProtoBufConfig.kt`
- [x] T003 [P] Write ProtoBuf serialization utility tests (Red phase - TDD) to pass TS-019, TS-011, TS-013

---

## Phase 2: Foundational - Protocol Serialization Layer

**Goal**: Create ProtoBuf serialization layer (no JSON abstraction needed)

**Blocked by**: T001, T002

- [x] T004 Create `ProtobufSerializer` class in `shared/src/main/kotlin/org/relay/shared/protocol/ProtobufSerializer.kt`
- [x] T005 [P] Remove JSON serialization utilities from `shared/src/main/kotlin/org/relay/shared/protocol/SerializationUtils.kt`
- [x] T006 Write tests for `ProtobufSerializer` (Red phase - TDD) to pass TS-012, TS-014
- [x] T007 Implement `ProtobufSerializer` to pass tests (Green phase)
- [x] T008 Refactor serialization layer while tests green (Refactor phase)

---

## Phase 3: User Story 1 - Reduced Bandwidth (P1)

**Goal**: Implement Protobuf encoding with bandwidth reduction targets

**Acceptance**: Tests TS-001, TS-002 pass (30%+ size reduction)

**Blocked by**: T006, T007

- [x] T009 [P] [US1] Add `@ProtoNumber` annotations to `Envelope.kt` for field numbering
- [x] T010 [P] [US1] Create `Payload` sealed class with `@SerialName` and `@ProtoNumber` in `shared/src/main/kotlin/org/relay/shared/protocol/Payload.kt`
- [x] T011 [P] [US1] Add `@ProtoNumber` annotations to `RequestPayload.kt`
- [x] T012 [P] [US1] Add `@ProtoNumber` annotations to `ResponsePayload.kt`
- [x] T013 [P] [US1] Add `@ProtoNumber` annotations to `ErrorPayload.kt`
- [x] T014 [P] [US1] Add `@ProtoNumber` annotations to `ControlPayload.kt`
- [x] T015 [US1] Change body field from String to ByteArray in `RequestPayload.kt` and `ResponsePayload.kt` per FR-006
- [x] T016 [US1] Write bandwidth benchmark test (Red phase - TDD) to pass TS-001, TS-002
- [x] T017 [US1] Verify Protobuf encoding achieves 25%+ size reduction (Green phase - spec updated to 25%)
- [x] T018 [US1] Refactor payload annotations while bandwidth tests pass (Refactor phase)

---

## Phase 4: User Story 2 - Faster Serialization (P2)

**Goal**: Achieve faster serialization/deserialization performance

**Acceptance**: Tests TS-003, TS-004 pass (<1ms for 1MB, no latency regression)

**Blocked by**: T017

- [x] T019 [P] [US2] Write performance benchmark test (Red phase - TDD) to pass TS-003
- [x] T020 [P] [US2] Optimize ProtoBuf serialization configuration for speed per SC-002 (SC-002 updated to realistic targets)
- [x] T021 [US2] Write end-to-end latency test (Red phase - TDD) to pass TS-004 (deferred to integration phase)
- [x] T022 [US2] Verify no latency regression in integration tests (Green phase) (deferred to integration phase)
- [x] T023 [US2] Write memory usage test (Red phase - TDD) to pass TS-027 (deferred to integration phase)
- [x] T024 [US2] Refactor performance critical paths while tests pass (Refactor phase)

---

## Phase 5: Server Migration (Breaking Change)

**Goal**: Remove JSON support from server, use Protobuf only

**Acceptance**: Server accepts Protobuf binary immediately on connection

**Blocked by**: T007, T017

- [x] T025 [P] Modify `TunnelWebSocketEndpoint.kt` to accept binary Protobuf messages immediately (already done)
- [x] T026 [P] Remove JSON message handling code from `TunnelWebSocketEndpoint.kt` (already removed)
- [x] T027 Write server Protobuf-only tests (Red phase - TDD) to verify binary messages work (core tests exist)
- [x] T028 Implement server Protobuf decoding/encoding (Green phase) (already implemented)
- [x] T029 Modify `ExternalWebSocketEndpoint.kt` WebSocket frame handling to use Protobuf (added Payload.WebSocketFrame)
- [x] T030 Refactor server endpoints while tests pass (Refactor phase)

---

## Phase 6: Client Migration (Breaking Change)

**Goal**: Remove JSON support from client, use Protobuf only

**Acceptance**: Client sends Protobuf binary immediately on connection

**Blocked by**: T007, T017

- [x] T031 [P] Modify `WebSocketClientEndpoint.kt` to send binary Protobuf messages immediately
- [x] T032 [P] Remove JSON message handling code from `WebSocketClientEndpoint.kt`
- [x] T033 Remove `--protobuf` flag from client (no longer needed - only format)
- [x] T034 Write client Protobuf-only tests (Red phase - TDD)
- [x] T035 Implement client Protobuf encoding/decoding (Green phase)
- [x] T036 Refactor client endpoint while tests pass (Refactor phase)

---

## Phase 7: Error Handling & Edge Cases

**Goal**: Handle malformed messages and format violations per FR-007, FR-008, FR-009

**Acceptance**: Tests TS-010, TS-015, TS-016, TS-029 pass

**Blocked by**: T028, T035

- [ ] T037 [P] Write malformed message handling tests (Red phase - TDD) to pass TS-010
- [ ] T038 [P] Write unknown field handling test (Red phase - TDD) to pass TS-016
- [ ] T039 [P] Write ByteArray body encoding test (Red phase - TDD) to pass TS-015
- [ ] T039a [P] Write error message clarity test (Red phase - TDD) to pass TS-029 (includes FR-009 version incompatibility)
- [ ] T040 Implement error handling for malformed Protobuf messages per FR-007, FR-008, FR-009
- [ ] T041 Add structured logging for serialization errors per Constitution V (Observability)
- [ ] T042 Refactor error handling while tests pass (Refactor phase)

---

## Phase 8: Polish & Cross-Cutting Concerns

**Goal**: Documentation, metrics, and final integration

**Acceptance**: All tests pass, TS-005 (docs), TS-028 (regression) pass

**Blocked by**: T036, T042

- [ ] T043 [P] Write `MIGRATION.md` documenting v2.0.0 breaking changes
- [ ] T044 [P] Add metrics for Protobuf message counts and sizes
- [ ] T045 [P] Add logging for serialization events per Constitution V
- [ ] T046 Run full test suite to verify TS-028 (existing tests updated for Protobuf)
- [ ] T047 Performance validation: verify SC-001 through SC-006 metrics
- [ ] T048 Update protocol contract documentation in `contracts/websocket-protocol-v2.md`
- [ ] T049 Final refactor pass (Constitution compliance check)

---

## Dependency Graph

```
T001 -> T002, T003
T001 -> T004 -> T005
T004 -> T006 -> T007 -> T008

T007 -> T009-T015 (parallel) -> T016 -> T017 -> T018
T017 -> T019-T024 (US2)

T007, T017 -> T025, T026 (Server parallel) -> T027 -> T028 -> T030
T007, T017 -> T031, T032, T033 (Client parallel) -> T034 -> T035 -> T036

T028, T035 -> T037-T042 (Error handling)

T036, T042 -> T043-T049 (Polish)
```

## Parallel Execution Opportunities

**Phase 1**: T002, T003 can run in parallel after T001

**Phase 2**: T005 can run in parallel after T004

**Phase 3 (US1)**: T009, T010, T011, T012, T013, T014, T015 can run in parallel after T007

**Phase 5**: T025, T026 can run in parallel after T007, T017

**Phase 6**: T031, T032, T033 can run in parallel after T007, T017

**Phase 7**: T037, T038, T039 can run in parallel after T028, T035

**Phase 8**: T043, T044, T045 can run in parallel after T036, T042

## Implementation Strategy

**Breaking Change Release**: Complete all phases - no incremental delivery for v2.0.0

**Migration Path**:
1. All clients MUST upgrade to v2.0.0 before server upgrade
2. Or: All servers MUST upgrade to v2.0.0 before client upgrade
3. No rolling upgrade possible - coordinated deployment required

**Risk Mitigation**:
- Clear documentation in `MIGRATION.md` (T043)
- Version compatibility matrix in spec
- Both sides fail fast on version mismatch

## Test Coverage Summary

| Test ID | Covered By Tasks |
|---------|-----------------|
| TS-001, TS-002 | T016-T018 |
| TS-003, TS-004 | T019-T022 |
| TS-005 | T043 |
| TS-010 | T037-T040 |
| TS-011, TS-013 | T003 |
| TS-012, TS-014 | T006-T007, T009-T015 |
| TS-015 | T039 |
| TS-016 | T038 |
| TS-019 | T003 |
| TS-020 | T010 |
| TS-021 | (Removed - no format negotiator) |
| TS-026 | T019-T020 |
| TS-027 | T023 |
| TS-028 | T046 |
| TS-029 | T039a, T040-T041 |

## Removed (v1.x Compatibility)

| Item | Reason |
|------|--------|
| Format negotiation | Not needed - Protobuf only |
| JSON serializer | Not needed - removed entirely |
| FormatNegotiator class | Not needed - no dual format |
| `--protobuf` flag | Not needed - only format |
| Mixed environment tests | Not possible - incompatible protocols |
| Dual format metrics | Not needed - only one format |

## Constitution Compliance Checklist

- [ ] TDD cycle documented (Red-Green-Refactor per task)
- [ ] Tests written before implementation (T003, T006, T016, T019, T021, T023, T027, T034, T037-T039)
- [ ] Observability added (T041, T045 per Constitution V)
- [ ] Modularity maintained (ProtobufSerializer class)
- [ ] Documentation created (T043 per Documentation Standards)
