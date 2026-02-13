# Implementation Plan: Tunnel Client CLI Ergonomics

**Branch**: `002-tunnel-client-cli` | **Date**: 2026-02-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-tunnel-client-cli/spec.md`

**Note**: This plan implements improved CLI ergonomics for the Relay tunnel client using Quarkus Picocli.

## Summary

Replace the existing manual CLI argument parsing in the Relay tunnel client with Quarkus Picocli to provide a cleaner command-line interface. The new interface uses a positional port argument instead of full URLs: `client 3000 -s tun.example.com -k secret`. This reduces typing friction while maintaining backward compatibility with configuration files and environment variables.

## Technical Context

**Language/Version**: Kotlin 2.3.0, Java 21
**Primary Dependencies**: Quarkus 3.31.3, Quarkus Picocli, Jakarta WebSocket, OkHttp
**Storage**: N/A (configuration via properties files)
**Testing**: JUnit 5 via Quarkus, Testcontainers for integration tests
**Target Platform**: JVM 21+ (Linux/macOS/Windows)
**Project Type**: Single CLI application
**Performance Goals**: Startup time < 2 seconds, connection establishment < 5 seconds
**Constraints**: Must maintain backward compatibility with existing config, must not break WebSocket connection logic
**Scale/Scope**: Single-user CLI tool, no concurrent usage scenarios

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Specification-Driven | ✅ PASS | Spec has 15 FRs, 5 SCs, 3 user stories |
| II. Progressive Disclosure | ✅ PASS | Picocli is simplest CLI solution for Quarkus |
| III. Test-First (NON-NEGOTIABLE) | ⚠️ PENDING | Tests MUST be written before implementation |
| IV. Modularity | ✅ PASS | CLI layer separate from connection logic |
| V. Observability | ✅ PASS | Logging levels already supported |

**Action Required**: All implementation tasks MUST include test-first approach per Principle III.

## Project Structure

### Documentation (this feature)

```text
specs/002-tunnel-client-cli/
  plan.md              # This file
  research.md          # Phase 0 output
  data-model.md        # Phase 1 output
  quickstart.md        # Phase 1 output
  contracts/           # Phase 1 output
  tasks.md             # Phase 2 output
```

### Source Code (repository root)

Current client structure:

```text
client/
  src/main/kotlin/org/relay/client/
    TunnelClient.kt              # Main entry point - REFACTOR
    config/
      ClientConfig.kt            # Existing config interface
    proxy/
      LocalHttpProxy.kt          # Unchanged
    retry/
      ReconnectionHandler.kt     # Unchanged
    websocket/
      WebSocketClientEndpoint.kt # Unchanged
      LocalWebSocketProxy.kt     # Unchanged
      LocalWebSocketClientEndpoint.kt # Unchanged
  src/test/kotlin/org/relay/client/
    config/
      ClientConfigTest.kt        # Expand for new CLI tests
```

**Structure Decision**: Minimal changes - only `TunnelClient.kt` requires significant refactoring to replace manual argument parsing with Picocli annotations. New command class `TunnelCommand.kt` will be added to house Picocli-specific logic.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | - | - |

**No violations detected.**

## Phase 0: Research & Decisions

*See [research.md](./research.md) for detailed findings.*

### Key Decisions

1. **Picocli over manual parsing**: Quarkus Picocli provides built-in help generation, validation, and type conversion. Already in dependencies.

2. **Single command class**: Use `@Command` annotated class with `@Parameters` and `@Option` for clean separation of CLI logic from business logic.

3. **Config precedence**: CLI args > Environment variables > Properties file > Defaults (Quarkus Config standard)

4. **Preserve existing connection logic**: The WebSocket endpoint and reconnection handler remain unchanged; only argument parsing changes.

## Phase 1: Design & Contracts

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    TunnelCommand (Picocli)                   │
│  - @Parameters port                                          │
│  - @Option server, key, subdomain, insecure, quiet, verbose  │
└─────────────────────────────┬───────────────────────────────┘
                              │ builds ClientConfig
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ClientConfig (existing)                   │
│  - @ConfigMapping for Quarkus config                         │
└─────────────────────────────┬───────────────────────────────┘
                              │ injected into
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    TunnelClient (refactored)                 │
│  - Connection management                                     │
│  - WebSocket lifecycle                                       │
└─────────────────────────────┬───────────────────────────────┘
                              │ uses
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              WebSocketClientEndpoint (existing)              │
└─────────────────────────────────────────────────────────────┘
```

### Data Model

*See [data-model.md](./data-model.md) for entity details.*

Key entities:
- **TunnelCommand**: Picocli command with parsed arguments
- **ClientConfig**: Existing configuration interface (unchanged)
- **ConnectionParameters**: Value object for normalized connection settings

### API Contracts

*See [contracts/](./contracts/) for detailed specifications.*

**CLI Interface Contract**:
```
Usage: client [OPTIONS] <port>

Positional Arguments:
  <port>                Local HTTP service port (1-65535)

Options:
  -s, --server=<host>   Tunnel server hostname (required)
  -k, --key=<secret>    Authentication secret key (required)
  -d, --subdomain=<name> Requested subdomain (optional)
      --insecure        Use ws:// instead of wss://
  -q, --quiet           Suppress non-error output
  -v, --verbose         Enable debug logging
  -h, --help            Show this help message and exit
```

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Invalid arguments or configuration |
| 2 | Connection failed |
| 3 | Authentication failed |
| 130 | Interrupted (SIGINT) |

## Implementation Approach

### Step 1: Create TunnelCommand (Test-First)

Write unit tests for:
- Port validation (1-65535)
- Subdomain validation (DNS label rules)
- URL construction (http://localhost:{port}, wss://{host}/ws)
- Exit code mapping

Then implement `TunnelCommand.kt` with Picocli annotations.

### Step 2: Refactor TunnelClient (Test-First)

Write integration tests for:
- CLI argument parsing integration
- Config precedence (args > env > file > defaults)
- Help output format

Then refactor `TunnelClient.kt` to delegate to `TunnelCommand`.

### Step 3: Update Existing Tests

Ensure existing `ClientConfigTest` passes with new CLI integration.

### Step 4: Manual Verification

Test scenarios:
```bash
# Basic usage
./client 3000 -s tun.example.com -k secret

# With subdomain
./client 8080 -s tun.example.com -d myapp -k secret

# Insecure mode
./client 3000 -s localhost:8080 -k secret --insecure

# Quiet mode
./client 3000 -s tun.example.com -k secret --quiet

# Help
./client --help
```

## Constitution Re-Check (Post-Design)

| Principle | Validation |
|-----------|------------|
| I. Spec-Driven | All 15 FRs mapped to implementation steps |
| II. Progressive Disclosure | Picocli is standard, no over-engineering |
| III. Test-First | Each step explicitly includes test-first requirement |
| IV. Modularity | CLI layer cleanly separated |
| V. Observability | Logging levels preserved, exit codes explicit |

**Status**: ✅ ALL PRINCIPLES VALIDATED

## Tessl Integration

Tessl not installed. Proceeding without tile-based documentation.

## Next Steps

1. **Create research.md** with detailed technology decisions
2. **Create data-model.md** with entity specifications
3. **Create quickstart.md** with test scenarios
4. **Run `/iikit-04-checklist`** for quality validation
5. **Run `/iikit-05-testify`** for test specifications (REQUIRED by constitution)
6. **Run `/iikit-06-tasks`** for task breakdown
