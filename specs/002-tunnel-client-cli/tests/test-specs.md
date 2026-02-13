# Test Specifications: Tunnel Client CLI Ergonomics

**Generated**: 2026-02-13T10:31:00Z  
**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

## TDD Assessment

**Determination**: **mandatory**  
**Confidence**: **high**  
**Evidence**: "III. Test-First Verification (NON-NEGOTIABLE) — The Red-Green-Refactor cycle is mandatory: write failing tests, implement to make them pass, then refactor while maintaining green tests."  
**Reasoning**: The constitution explicitly mandates TDD with non-negotiable language and the Red-Green-Refactor cycle. This is the highest confidence determination possible.

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

### TS-001: Connect with minimal arguments and get assigned subdomain

**Source**: spec.md:User Story 1:scenario-1  
**Type**: acceptance  
**Priority**: P1

**Given**: a local HTTP service running on port 3000  
**When**: the user runs `client 3000 -s tun.example.com -k my-secret`  
**Then**: the client connects to the server and displays the assigned public URL (e.g., `https://abc123.tun.example.com`)

**Traceability**: FR-001, FR-002, FR-003, FR-005, FR-006, FR-008, US-001-scenario-1

**Test Implementation Notes**:
- Mock server that accepts WebSocket connection
- Verify connection established to `wss://tun.example.com/ws`
- Verify output contains assigned subdomain URL pattern
- Verify local URL constructed as `http://localhost:3000`

---

### TS-002: Connect with custom subdomain

**Source**: spec.md:User Story 1:scenario-2  
**Type**: acceptance  
**Priority**: P1

**Given**: a local HTTP service on port 8080  
**When**: the user runs `client 8080 -s tun.example.com -d myapp -k secret`  
**Then**: the client connects and the public URL uses the requested subdomain (`https://myapp.tun.example.com`)

**Traceability**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-008, US-001-scenario-2

**Test Implementation Notes**:
- Mock server that accepts WebSocket connection with `subdomain=myapp`
- Verify connection request includes subdomain parameter
- Verify output contains `https://myapp.tun.example.com`

---

### TS-003: Random subdomain assignment when not specified

**Source**: spec.md:User Story 1:scenario-3  
**Type**: acceptance  
**Priority**: P1

**Given**: the user runs `client 3000 -s tun.example.com -k secret` without specifying `-d`  
**Then**: the server assigns a random unique subdomain and the client displays it clearly in the output

**Traceability**: FR-004 (optional behavior), FR-008, US-001-scenario-3

**Test Implementation Notes**:
- Mock server that returns random subdomain in connection response
- Verify client displays the assigned subdomain URL
- Verify subdomain follows expected format (alphanumeric, reasonable length)

---

### TS-004: Help flag displays usage information

**Source**: spec.md:User Story 2:scenario-1  
**Type**: acceptance  
**Priority**: P2

**Given**: the user runs `client --help` or `client -h`  
**Then**: the output displays usage information showing: the positional port argument, the `-s` flag for server URL, the optional `-d` flag for subdomain, and the `-k` flag for secret key with clear examples

**Traceability**: FR-010, US-002-scenario-1

**Test Implementation Notes**:
- Capture stdout/stderr
- Verify output contains "port" or positional argument description
- Verify output contains `-s, --server` option
- Verify output contains `-k, --key` option
- Verify output contains `-d, --subdomain` option
- Verify exit code is 0

---

### TS-005: No arguments shows error and usage

**Source**: spec.md:User Story 2:scenario-2  
**Type**: acceptance  
**Priority**: P2

**Given**: the user runs `client` without any arguments  
**Then**: the client displays a helpful error message explaining that a port number is required, along with a brief usage example

**Traceability**: FR-001 (required), FR-009, FR-010, US-002-scenario-2

**Test Implementation Notes**:
- Run with empty args array
- Verify error message mentions "port" is required
- Verify usage example is displayed
- Verify exit code is 1

---

### TS-006: Missing required flags show specific errors

**Source**: spec.md:User Story 2:scenario-3  
**Type**: acceptance  
**Priority**: P2

**Given**: the user runs `client 3000` without providing `-s` or `-k`  
**Then**: the client displays a specific error indicating which required parameters are missing

**Traceability**: FR-002, FR-003 (required flags), FR-009, US-002-scenario-3

**Test Implementation Notes**:
- Test with only port: `client 3000`
- Verify error mentions missing `--server` / `-s`
- Verify error mentions missing `--key` / `-k`
- Verify exit code is 1

---

### TS-007: Successful connection displays tunnel ready message

**Source**: spec.md:User Story 3:scenario-1  
**Type**: acceptance  
**Priority**: P3

**Given**: a successful connection  
**When**: the client establishes the tunnel  
**Then**: it displays a clear message showing the public URL (e.g., `Tunnel ready: https://abc123.tun.example.com -> localhost:3000`)

**Traceability**: FR-008, US-003-scenario-1

**Test Implementation Notes**:
- Mock successful WebSocket connection
- Verify output contains "Tunnel ready" or similar confirmation
- Verify output contains both public URL and local URL mapping
- Verify format is user-friendly

---

### TS-008: Unreachable server shows actionable error

**Source**: spec.md:User Story 3:scenario-2  
**Type**: acceptance  
**Priority**: P3

**Given**: the server is unreachable  
**When**: the client attempts to connect  
**Then**: it displays a clear, actionable error message (e.g., `Failed to connect to tun.example.com: Connection refused`)

**Traceability**: FR-002 (server URL), US-003-scenario-2

**Test Implementation Notes**:
- Attempt connection to non-existent server/port
- Verify error message includes server hostname
- Verify error is actionable (suggests check server address)
- Verify exit code is 2

---

### TS-009: Invalid secret key shows auth failure and exits

**Source**: spec.md: User Story 3:scenario-3  
**Type**: acceptance  
**Priority**: P3

**Given**: the secret key is invalid  
**When**: the client attempts authentication  
**Then**: it displays an authentication failure message and exits with a non-zero status code

**Traceability**: FR-003 (secret key validation), US-003-scenario-3

**Test Implementation Notes**:
- Mock server that rejects authentication
- Verify error message mentions authentication or secret key
- Verify exit code is 3 (auth failed)

---

## From spec.md (Functional Requirement Tests)

### TS-010: Port validation accepts valid range

**Source**: spec.md:FR-007  
**Type**: validation  
**Priority**: P1

**Given**: CLI argument parsing  
**When**: port values 1, 80, 443, 3000, 8080, 65535 are provided  
**Then**: all are accepted as valid

**Traceability**: FR-001, FR-007

---

### TS-011: Port validation rejects out of range values

**Source**: spec.md:FR-007  
**Type**: validation  
**Priority**: P1

**Given**: CLI argument parsing  
**When**: port values 0, -1, 65536, 99999 are provided  
**Then**: validation fails with error message indicating valid range is 1-65535

**Traceability**: FR-007, FR-009

---

### TS-012: Subdomain validation accepts valid DNS labels

**Source**: spec.md:FR-014  
**Type**: validation  
**Priority**: P1

**Given**: CLI argument parsing  
**When**: subdomain values "myapp", "test-123", "a", "sub-domain-1" are provided  
**Then**: all are accepted as valid (match `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`)

**Traceability**: FR-004, FR-014

---

### TS-013: Subdomain validation rejects invalid formats

**Source**: spec.md:FR-014  
**Type**: validation  
**Priority**: P1

**Given**: CLI argument parsing  
**When**: subdomain values "-invalid", "invalid-", "Invalid", "under_score", "a.b", "" (empty), "64chars0123456789012345678901234567890123456789012345678901234567890" are provided  
**Then**: validation fails with error message explaining DNS label rules

**Traceability**: FR-014, FR-009

---

### TS-014: Local URL construction from port

**Source**: spec.md:FR-005  
**Type**: unit  
**Priority**: P1

**Given**: port number 3000  
**When**: ConnectionParameters is built  
**Then**: localUrl equals `http://localhost:3000`

**Traceability**: FR-005

---

### TS-015: Secure WebSocket URL construction from hostname

**Source**: spec.md:FR-006  
**Type**: unit  
**Priority**: P1

**Given**: server hostname `tun.example.com`  
**When**: ConnectionParameters is built without `--insecure`  
**Then**: serverUrl equals `wss://tun.example.com/ws`

**Traceability**: FR-006

---

### TS-016: Insecure flag uses ws:// protocol

**Source**: spec.md:FR-013  
**Type**: unit  
**Priority**: P2

**Given**: server hostname `localhost:8080` and `--insecure` flag  
**When**: ConnectionParameters is built  
**Then**: serverUrl equals `ws://localhost:8080/ws`

**Traceability**: FR-013

---

### TS-017: Quiet mode sets ERROR log level

**Source**: spec.md:FR-015 (quiet)  
**Type**: unit  
**Priority**: P3

**Given**: `--quiet` flag is provided  
**When**: LogLevel is determined  
**Then**: level equals ERROR

**Traceability**: FR-015

---

### TS-018: Verbose mode sets DEBUG log level

**Source**: spec.md:FR-015 (verbose)  
**Type**: unit  
**Priority**: P3

**Given**: `--verbose` flag is provided  
**When**: LogLevel is determined  
**Then**: level equals DEBUG

**Traceability**: FR-015

---

### TS-019: Default mode sets INFO log level

**Source**: spec.md:FR-015  
**Type**: unit  
**Priority**: P3

**Given**: neither `--quiet` nor `--verbose` flag is provided  
**When**: LogLevel is determined  
**Then**: level equals INFO

**Traceability**: FR-015

---

### TS-020: Quiet wins when both quiet and verbose provided

**Source**: data-model.md:LogLevel mapping  
**Type**: unit  
**Priority**: P3

**Given**: both `--quiet` and `--verbose` flags are provided  
**When**: LogLevel is determined  
**Then**: level equals ERROR (quiet wins)

**Traceability**: FR-015

---

## From plan.md (Exit Code Tests)

### TS-021: Exit code 0 on success

**Source**: plan.md:Exit Codes  
**Type**: contract  
**Priority**: P1

**Given**: valid arguments and successful connection  
**When**: client completes normally  
**Then**: exit code is 0

**Traceability**: FR-010, plan.md exit code table

---

### TS-022: Exit code 1 on invalid arguments

**Source**: plan.md:Exit Codes  
**Type**: contract  
**Priority**: P1

**Given**: invalid port, missing required flag, or invalid subdomain  
**When**: validation fails  
**Then**: exit code is 1

**Traceability**: FR-007, FR-009, FR-014, plan.md exit code table

---

### TS-023: Exit code 2 on connection failure

**Source**: plan.md:Exit Codes  
**Type**: contract  
**Priority**: P1

**Given**: valid arguments but server unreachable  
**When**: connection attempt fails  
**Then**: exit code is 2

**Traceability**: plan.md exit code table

---

### TS-024: Exit code 3 on authentication failure

**Source**: plan.md:Exit Codes  
**Type**: contract  
**Priority**: P1

**Given**: valid arguments but invalid secret key  
**When**: authentication fails  
**Then**: exit code is 3

**Traceability**: plan.md exit code table

---

### TS-025: Exit code 130 on interrupt

**Source**: plan.md:Exit Codes  
**Type**: contract  
**Priority**: P2

**Given**: client is running  
**When**: SIGINT (Ctrl+C) is received  
**Then**: exit code is 130

**Traceability**: plan.md exit code table

---

## From data-model.md (Validation Tests)

### TS-026: DNS label regex matches valid subdomains

**Source**: data-model.md:DNS Label Pattern  
**Type**: validation  
**Priority**: P1

**Given**: DNS label regex `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`  
**When**: validating "myapp", "a", "z9-8-7"  
**Then**: all match

**Traceability**: FR-014, data-model.md validation rules

---

### TS-027: DNS label regex rejects invalid subdomains

**Source**: data-model.md:DNS Label Pattern  
**Type**: validation  
**Priority**: P1

**Given**: DNS label regex `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`  
**When**: validating "-start", "end-", "UPPER", "a_b", "toolong0123456789012345678901234567890123456789012345678901234567890" (64 chars)  
**Then**: all are rejected

**Traceability**: FR-014, data-model.md validation rules

---

## Summary

| Source | Count | Types |
|--------|-------|-------|
| spec.md (Acceptance) | 9 | acceptance |
| spec.md (Functional) | 11 | validation, unit |
| plan.md (Exit Codes) | 5 | contract |
| data-model.md | 2 | validation |
| **Total** | **27** | |

### Priority Breakdown

| Priority | Count | Tests |
|----------|-------|-------|
| P1 | 16 | TS-001, TS-002, TS-003, TS-010, TS-011, TS-012, TS-013, TS-014, TS-015, TS-021, TS-022, TS-023, TS-024, TS-026, TS-027 |
| P2 | 6 | TS-004, TS-005, TS-006, TS-016, TS-025 |
| P3 | 5 | TS-007, TS-008, TS-009, TS-017, TS-018, TS-019, TS-020 |

---

**Assertion Integrity Hash**: `80f9af908e37`  
**Status**: LOCKED
