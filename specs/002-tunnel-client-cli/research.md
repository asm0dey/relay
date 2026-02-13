# Research: Tunnel Client CLI Ergonomics

**Feature**: 002-tunnel-client-cli  
**Date**: 2026-02-13

## Technology Decisions

### CLI Framework: Quarkus Picocli

**Decision**: Use Quarkus Picocli extension for all CLI parsing.

**Rationale**:
- Already in project dependencies (`quarkus-picocli`)
- Native Quarkus integration (CDI, config)
- Automatic help generation
- Type-safe option parsing
- Validation support

**Alternatives Considered**:
| Option | Why Rejected |
|--------|--------------|
| Manual parsing (current) | Error-prone, no built-in help |
| Apache Commons CLI | Additional dependency, not Quarkus-native |
| kotlinx-cli | Experimental, not integrated with Quarkus config |

### Configuration Precedence

**Decision**: CLI args > Environment variables > Properties file > Defaults

**Rationale**: This is Quarkus Config's standard precedence. No custom logic needed.

**Properties File Location**: `~/.relay/config.properties` or `application.properties` in working directory.

### URL Construction

**Decision**: Construct URLs from simplified inputs

| Input | Construction | Example |
|-------|--------------|---------|
| Port | `http://localhost:{port}` | Port 3000 → `http://localhost:3000` |
| Server hostname | `wss://{host}/ws` | `tun.example.com` → `wss://tun.example.com/ws` |
| With `--insecure` | `ws://{host}/ws` | `localhost:8080` → `ws://localhost:8080/ws` |

**Rationale**: Reduces typing for common case while maintaining flexibility via flags.

### Validation Strategy

**Decision**: Client-side validation for immediate feedback

| Input | Validation Rule |
|-------|-----------------|
| Port | Integer 1-65535 (IANA port range) |
| Subdomain | DNS label: `[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?` |
| Server hostname | Basic hostname format validation |

**Rationale**: Fail fast with clear error messages. Server still validates for security.

## Dependencies

No new dependencies required. Existing:
- `io.quarkus:quarkus-picocli` - Already present
- `io.quarkus:quarkus-config-yaml` - Already present (for properties file support)

## Exit Code Strategy

| Code | Usage |
|------|-------|
| 0 | Normal exit, tunnel closed gracefully |
| 1 | Configuration/argument error (validation failure) |
| 2 | Network connection failure |
| 3 | Authentication failure (server rejected key) |
| 130 | SIGINT (Ctrl+C) - standard Unix practice |

## Tessl Tiles

### Installed Tiles

| Technology | Tile | Type | Version |
|------------|------|------|---------|
| Picocli | tessl/maven-info-picocli--picocli | Documentation | 4.7.0 |

### Available Skills

No new skills installed. Existing CLI skills remain available.

### Technologies Without Tiles

- Quarkus Picocli: No specific tile available (use base Picocli tile + Quarkus docs)
- Kotlin: No tile found in registry
- Jakarta WebSocket: No tile found in registry

## Open Questions

None - all clarifications resolved in spec.
