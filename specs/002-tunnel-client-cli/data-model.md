# Data Model: Tunnel Client CLI Ergonomics

**Feature**: 002-tunnel-client-cli  
**Date**: 2026-02-13

## Entities

### TunnelCommand

Picocli command class containing parsed CLI arguments.

**Attributes**:
| Field | Type | Picocli Annotation | Description |
|-------|------|-------------------|-------------|
| port | Int | `@Parameters(index="0", description="...")` | Local service port (1-65535) |
| server | String | `@Option(names=["-s","--server"], required=true)` | Server hostname |
| secretKey | String | `@Option(names=["-k","--key"], required=true)` | Auth secret |
| subdomain | String? | `@Option(names=["-d","--subdomain"])` | Requested subdomain (optional) |
| insecure | Boolean | `@Option(names="--insecure")` | Use ws:// instead of wss:// |
| quiet | Boolean | `@Option(names=["-q","--quiet"])` | Suppress non-error output |
| verbose | Boolean | `@Option(names=["-v","--verbose"])` | Enable debug logging |

**Validation Rules**:
- `port`: Must be 1-65535 (custom validator)
- `subdomain`: If present, must match DNS label pattern
- `server`: Must be valid hostname format

**Behavior**:
- On validation failure: Print error + usage, exit code 1
- On `--help`: Print help, exit code 0
- On success: Build ConnectionParameters, call TunnelClient

---

### ConnectionParameters

Value object for normalized connection settings.

**Attributes**:
| Field | Type | Description |
|-------|------|-------------|
| localUrl | String | Full local URL (e.g., `http://localhost:3000`) |
| serverUrl | String | Full WebSocket URL (e.g., `wss://tun.example.com/ws`) |
| secretKey | String | Authentication secret |
| subdomain | String? | Requested subdomain (may be null) |
| logLevel | LogLevel | Derived from quiet/verbose flags |

**Factory Method**:
```kotlin
fun fromCommand(command: TunnelCommand): ConnectionParameters
```

---

### ClientConfig (Existing)

Quarkus configuration interface. Unchanged except for new source of values.

**Integration**:
- Values now sourced from `ConnectionParameters` instead of direct CLI parsing
- Still supports `@ConfigMapping` for properties file and env vars
- CLI args override all other sources

---

### LogLevel

Enum for output verbosity control.

**Values**:
- `ERROR` - `--quiet` mode
- `INFO` - Default mode
- `DEBUG` - `--verbose` mode

**Mapping**:
| Flags | LogLevel |
|-------|----------|
| `--quiet` | ERROR |
| (none) | INFO |
| `--verbose` | DEBUG |
| `--quiet --verbose` | ERROR (quiet wins) |

## Relationships

```
TunnelCommand --creates--> ConnectionParameters --configures--> ClientConfig
                                              --sets--> LogLevel
```

## State Transitions

### CLI Lifecycle

```
[Parse Args] -> [Validate] -> [Build Params] -> [Connect]
    |              |              |               |
    v              v              v               v
 [Help?]      [Invalid?]    [LogLevel]     [Exit code]
    |              |              |               |
    v              v              v               v
  Exit 0       Exit 1       Configure      0, 2, 3, 130
```

## Validation Reference

### DNS Label Pattern (Subdomain)

Regex: `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`

Rules:
- 1-63 characters
- Lowercase alphanumeric start/end
- Middle may contain hyphens
- No underscores, no uppercase

### Port Validation

Range: 1-65535 (inclusive)

Special ports allowed (no restriction):
- 80, 443, 8080, 3000, etc.

### Hostname Validation

Basic validation:
- Non-empty
- No spaces
- No protocol prefix (no `http://`, `https://`)
- No path (no `/ws`, `/api`)
