# Test Specifications: Relay Tunnel Service

**Generated**: 2026-02-11T22:54:11Z  
**Updated**: 2026-02-12T00:18:00Z  
**Integrity Hash**: `69a23184cdc5355c`  
**Feature**: `spec.md` | **Plan**: `plan.md`

## TDD Assessment

**Determination**: mandatory
**Confidence**: high
**Evidence**: "III. Test-First Verification (NON-NEGOTIABLE): All functionality must be verifiable through automated tests. Test specifications define expected behavior before implementation. The Red-Green-Refactor cycle is mandatory: write failing tests, implement to make them pass, then refactor while maintaining green tests."
**Reasoning**: The constitution explicitly requires Test-First Verification as non-negotiable. The plan outlines integration tests as part of Phase 2. Test specifications must be generated before implementation begins.

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

### TS-001: Client Connection with Valid Secret Key

**Source**: spec.md:User Story 1:Acceptance Scenario 1
**Type**: acceptance
**Priority**: P1

**Given**: The relay server is running with domain `tun.example.com` configured
**When**: A client connects with a valid secret key
**Then**: The server assigns a random subdomain (e.g., `abc123.tun.example.com`) and the client receives confirmation with the assigned URL

**Traceability**: FR-001, FR-002, SC-001, US1-scenario-1

---

### TS-002: Client Connection with Invalid Secret Key

**Source**: spec.md:User Story 1:Acceptance Scenario 2
**Type**: acceptance
**Priority**: P1

**Given**: A client is attempting to connect
**When**: The client provides an invalid secret key
**Then**: The connection is rejected with an authentication error and the client is informed of the failure

**Traceability**: FR-003, SC-004, US1-scenario-2

---

### TS-003: Server Unreachable Connection Error

**Source**: spec.md:User Story 1:Acceptance Scenario 3
**Type**: acceptance
**Priority**: P1

**Given**: The relay server is unreachable
**When**: The client attempts to connect
**Then**: The client receives a clear connection error and can retry

**Traceability**: FR-001, US1-scenario-3

---

### TS-004: HTTP GET Request Forwarding

**Source**: spec.md:User Story 2:Acceptance Scenario 1
**Type**: acceptance
**Priority**: P1

**Given**: A client has an active tunnel with subdomain `abc123.tun.example.com`
**When**: An HTTP GET request is made to `http://abc123.tun.example.com/api/status`
**Then**: The request is forwarded to the client's local application and the response is returned to the original requester

**Traceability**: FR-004, FR-005, FR-006, SC-002, SC-005, US2-scenario-1

---

### TS-005: HTTP POST with Body Forwarding

**Source**: spec.md:User Story 2:Acceptance Scenario 2
**Type**: acceptance
**Priority**: P1

**Given**: An active tunnel exists
**When**: A POST request with a JSON body is made to the public subdomain
**Then**: The complete request (headers, body, method) is preserved and forwarded to the local application

**Traceability**: FR-004, FR-005, SC-005, US2-scenario-2

---

### TS-006: Local Application Unavailable Error

**Source**: spec.md:User Story 2:Acceptance Scenario 3
**Type**: acceptance
**Priority**: P1

**Given**: A tunnel exists but the local application is not responding
**When**: A request is made to the public subdomain
**Then**: The requester receives an appropriate error response indicating the upstream service is unavailable

**Traceability**: FR-006, US2-scenario-3

---

### TS-007: Multiple Concurrent Tunnels

**Source**: spec.md:User Story 3:Acceptance Scenario 1
**Type**: acceptance
**Priority**: P2

**Given**: The server is running
**When**: Three clients connect with different valid secret keys
**Then**: Each client receives a unique random subdomain and all three can maintain simultaneous connections

**Traceability**: FR-007, SC-003, SC-006, US3-scenario-1

---

### TS-008: Request Routing to Correct Client

**Source**: spec.md:User Story 3:Acceptance Scenario 2
**Type**: acceptance
**Priority**: P2

**Given**: Three active tunnels with unique subdomains
**When**: Requests are made to each subdomain
**Then**: Each request is forwarded to the correct corresponding client

**Traceability**: FR-004, FR-007, SC-003, US3-scenario-2

---

### TS-009: Duplicate Secret Key Behavior

**Source**: spec.md:User Story 3:Acceptance Scenario 3
**Type**: acceptance
**Priority**: P2

**Given**: Two clients connect with the same secret key
**When**: The second client connects
**Then**: Both clients receive unique subdomains and operate independently

**Traceability**: FR-007, US3-scenario-3, Clarifications

---

### TS-010: Server Domain Configuration

**Source**: spec.md:User Story 4:Acceptance Scenario 1
**Type**: acceptance
**Priority**: P3

**Given**: A configuration file or environment variables specifying `BASE_DOMAIN=tun.mycompany.com`
**When**: The server starts
**Then**: Assigned subdomains follow the pattern `*.tun.mycompany.com`

**Traceability**: FR-008, US4-scenario-1

---

### TS-011: Secret Key Validation Configuration

**Source**: spec.md:User Story 4:Acceptance Scenario 2
**Type**: acceptance
**Priority**: P3

**Given**: Configuration for secret key validation
**When**: The server processes client connections
**Then**: Only requests with keys matching the configured validation rules are accepted

**Traceability**: FR-003, FR-008, US4-scenario-2

---

### TS-012: WebSocket Connection Upgrade

**Source**: spec.md:User Story 5:Acceptance Scenario 1
**Type**: acceptance
**Priority**: P2

**Given**: A client has an active tunnel
**When**: An external user opens a WebSocket connection to `wss://abc123.tun.example.com/ws`
**Then**: The WebSocket upgrade request is forwarded to the local application and the bidirectional connection is established

**Traceability**: FR-010, US5-scenario-1

---

### TS-013: WebSocket Message Forwarding to Local App

**Source**: spec.md:User Story 5:Acceptance Scenario 2
**Type**: acceptance
**Priority**: P2

**Given**: An active WebSocket tunnel
**When**: The external user sends a message
**Then**: The message is forwarded to the local application

**Traceability**: FR-010, US5-scenario-2

---

### TS-014: WebSocket Message Forwarding to External User

**Source**: spec.md:User Story 5:Acceptance Scenario 3
**Type**: acceptance
**Priority**: P2

**Given**: An active WebSocket tunnel
**When**: The local application sends a message
**Then**: The message is forwarded to the external user

**Traceability**: FR-010, US5-scenario-3

---

### TS-015: Configurable Server Shutdown - Graceful

**Source**: spec.md:FR-011
**Type**: acceptance
**Priority**: P2

**Given**: Server is running with graceful shutdown configured and active tunnels with in-flight requests
**When**: Server receives shutdown signal
**Then**: Server waits up to 30 seconds for in-flight requests to complete, notifies clients, then closes

**Traceability**: FR-011

---

### TS-016: Configurable Server Shutdown - Immediate

**Source**: spec.md:FR-011
**Type**: acceptance
**Priority**: P2

**Given**: Server is running with immediate shutdown configured
**When**: Server receives shutdown signal
**Then**: Server closes immediately, all in-flight requests receive 503 errors

**Traceability**: FR-011

---

### TS-017: Client Reconnection with Exponential Backoff

**Source**: spec.md:FR-012
**Type**: acceptance
**Priority**: P2

**Given**: Client is connected and configured with reconnection enabled
**When**: Connection drops unexpectedly
**Then**: Client waits 1 second, then retries with delay doubling each time (2s, 4s, 8s...) up to 60s cap, retrying indefinitely

**Traceability**: FR-012

---

## From plan.md (Contract Tests)

### TS-101: WebSocket Envelope Format Validation

**Source**: plan.md:contracts/websocket-protocol.md:Envelope Format
**Type**: contract
**Priority**: P1

**Given**: A valid WebSocket connection
**When**: A message is sent with envelope format containing correlationId, type, timestamp, and payload
**Then**: The message is accepted and processed

**Traceability**: Message Protocol Contract

---

### TS-102: Request Message Serialization

**Source**: plan.md:contracts/websocket-protocol.md:REQUEST Type
**Type**: contract
**Priority**: P1

**Given**: An HTTP request to forward
**When**: The request is serialized to a REQUEST message
**Then**: The payload contains method, path, query, headers, and base64-encoded body

**Traceability**: Message Protocol Contract

---

### TS-103: Response Message Serialization

**Source**: plan.md:contracts/websocket-protocol.md:RESPONSE Type
**Type**: contract
**Priority**: P1

**Given**: An HTTP response to return
**When**: The response is serialized to a RESPONSE message
**Then**: The payload contains statusCode, headers, and base64-encoded body with matching correlationId

**Traceability**: Message Protocol Contract

---

### TS-104: Error Message Format

**Source**: plan.md:contracts/websocket-protocol.md:ERROR Type
**Type**: contract
**Priority**: P1

**Given**: An error condition occurs
**When**: An ERROR message is sent
**Then**: The payload contains a valid error code (TIMEOUT, UPSTREAM_ERROR, INVALID_REQUEST, SERVER_ERROR, RATE_LIMITED) and descriptive message

**Traceability**: Message Protocol Contract

---

### TS-105: Tunnel Registration Control Message

**Source**: plan.md:contracts/websocket-protocol.md:CONTROL Type
**Type**: contract
**Priority**: P1

**Given**: A client has successfully authenticated
**When**: The server sends a CONTROL message with action "REGISTERED"
**Then**: The payload contains the assigned subdomain and publicUrl

**Traceability**: Message Protocol Contract

---

### TS-106: Correlation ID Matching

**Source**: plan.md:contracts/websocket-protocol.md:Correlation ID Requirements
**Type**: contract
**Priority**: P1

**Given**: A REQUEST message with correlationId "550e8400-e29b-41d4-a716-446655440000"
**When**: The client responds
**Then**: The RESPONSE or ERROR message contains the identical correlationId

**Traceability**: Message Protocol Contract

---

## Integration Tests

### TS-201: End-to-End Request Forwarding Flow

**Source**: plan.md:Phase 2:Integration Tests
**Type**: integration
**Priority**: P1

**Given**: Server running, client connected with valid secret key, local application responding on port 3000
**When**: An external HTTP POST request with JSON body is made to the assigned subdomain
**Then**: The local application receives the request with correct method, headers, and body; the external requester receives the local application's response

**Traceability**: FR-004, FR-005, FR-006, US1, US2

---

### TS-202: Client Disconnection Handling

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P1

**Given**: An active tunnel with requests in flight
**When**: The client disconnects unexpectedly
**Then**: In-flight requests receive appropriate error responses; new requests to the subdomain return 404 or 503

**Traceability**: Edge Case: Client disconnection

---

### TS-203: Subdomain Collision Prevention

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P2

**Given**: Multiple clients connecting simultaneously
**When**: The random generator would produce a duplicate subdomain
**Then**: The collision is detected and a new unique subdomain is generated

**Traceability**: SC-006, Edge Case: Subdomain collision

---

### TS-204: Large Body Handling

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P2

**Given**: An active tunnel
**When**: A request with 5MB body is made
**Then**: The complete body is forwarded to the local application and the response is returned correctly

**Traceability**: Edge Case: Large request/response bodies

---

### TS-205: Request Timeout Handling

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P2

**Given**: An active tunnel with local application that takes >30 seconds to respond
**When**: A request is forwarded to the local application
**Then**: After 30 seconds, the external requester receives a 504 Gateway Timeout

**Traceability**: Edge Case: Local application timeout

---

### TS-206: Non-HTTP Response from Local Application

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P2

**Given**: An active tunnel where local application returns malformed or non-HTTP response
**When**: A request is forwarded to the local application
**Then**: The server returns 502 Bad Gateway to the external requester

**Traceability**: Edge Case: Non-HTTP local response

---

### TS-207: Invalid WebSocket Message Format

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P2

**Given**: An active WebSocket tunnel
**When**: A WebSocket message with invalid format or unknown message type is received
**Then**: The connection is closed with error code 1008 (policy violation)

**Traceability**: Edge Case: Invalid WebSocket message

---

### TS-208: Resource Limit Rejection

**Source**: spec.md:Edge Cases
**Type**: integration
**Priority**: P3

**Given**: Server has reached resource limits (memory, file descriptors)
**When**: A new client attempts to connect
**Then**: The connection is rejected with 503 Service Unavailable

**Traceability**: Edge Case: Resource exhaustion

---

## Unit Tests

### TS-301: TunnelRegistry Operations

**Source**: plan.md:Phase 2:Unit Tests
**Type**: unit
**Priority**: P1

**Given**: An empty TunnelRegistry
**When**: Tunnels are registered, looked up by subdomain, and unregistered
**Then**: Each operation succeeds and the registry maintains correct state

**Traceability**: Server Component: TunnelRegistry

---

### TS-302: Subdomain Generation Uniqueness

**Source**: plan.md:Phase 2:Unit Tests
**Type**: unit
**Priority**: P1

**Given**: A subdomain generator
**When**: 1000 subdomains are generated
**Then**: All subdomains are unique and match the configured format (12 lowercase alphanumeric characters)

**Traceability**: SC-006, Server Component: Subdomain Generation

---

### TS-303: Configuration Parsing

**Source**: plan.md:Phase 2:Unit Tests
**Type**: unit
**Priority**: P2

**Given**: Various configuration inputs (valid YAML, environment variables, missing values)
**When**: Configuration is parsed
**Then**: Valid configurations load successfully; invalid configurations produce clear error messages

**Traceability**: Server/Client Configuration

---

## Summary

| Source | Count | Types |
|--------|-------|-------|
| spec.md | 17 | acceptance |
| plan.md (contracts) | 6 | contract |
| plan.md (integration) | 8 | integration |
| plan.md (unit) | 3 | unit |
| **Total** | **34** | |

---

## Implementation Order

1. **Phase 1**: Unit tests (TS-301-303) - Test core utilities first
2. **Phase 2**: Contract tests (TS-101-106) - Verify message protocol
3. **Phase 3**: Core acceptance tests (TS-001-006) - MVP functionality (connection + HTTP forwarding)
4. **Phase 4**: Integration tests (TS-201-208) - End-to-end scenarios including edge cases
5. **Phase 5**: Extended features (TS-007-014) - Concurrent tunnels, config, WebSocket
6. **Phase 6**: Resilience features (TS-015-017) - Shutdown, reconnection
