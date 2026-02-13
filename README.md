# Relay - HTTP Tunnel Service

Relay is a tunneling service that exposes local HTTP services to the internet, built with Quarkus and Kotlin.

## Project Structure

- **`client/`** - Tunnel client CLI (exposes local services)
- **`server/`** - Tunnel server (manages connections)
- **`shared/`** - Shared protocol definitions

**Technology Stack**: Quarkus 3.31.3, Kotlin 2.3.0, WebSockets, Picocli

## Quick Start

### 1. Build the Project

```bash
./gradlew build
```

### 2. Start the Server

```bash
java -jar server/build/quarkus-app/quarkus-run.jar
```

### 3. Run the Client

Expose a local HTTP service running on port 3000:

```bash
java -jar client/build/quarkus-app/quarkus-run.jar 3000 -s tun.example.com -k your-secret-key
```

Output:
```
Tunnel ready: https://abc123.tun.example.com -> localhost:3000
```

## Client CLI Usage

### Basic Commands

**Minimal (random subdomain)**:
```bash
client 3000 -s tun.example.com -k secret-key
```

**Custom subdomain**:
```bash
client 3000 -s tun.example.com -d myapp -k secret-key
# → https://myapp.tun.example.com
```

**Insecure mode (local testing)**:
```bash
client 3000 -s localhost:8080 -k test-key --insecure
```

### All Options

```
Usage: client [OPTIONS] <port>

Positional:
  <port>                 Local HTTP service port (1-65535)

Required:
  -s, --server=<host>    Tunnel server hostname
  -k, --key=<secret>     Authentication secret key

Optional:
  -d, --subdomain=<name> Request specific subdomain
      --insecure         Use ws:// instead of wss://
  -q, --quiet            Suppress non-error output
  -v, --verbose          Enable debug logging
  -h, --help             Show help and exit
```

### Exit Codes

| Code | Meaning | Cause |
|------|---------|-------|
| 0 | Success | Tunnel established or help displayed |
| 1 | Invalid arguments | Missing flags, invalid port/subdomain |
| 2 | Connection failed | Server unreachable, network error |
| 3 | Authentication failed | Invalid secret key |
| 130 | Interrupted | User pressed Ctrl+C |

### Configuration File

Create `~/.relay/config.properties`:
```properties
relay.client.server-url=wss://tun.example.com/ws
relay.client.secret-key=my-default-key
```

Then use short form:
```bash
client 3000
```

**Config precedence**: CLI args > environment variables > properties file > defaults

## Development

### Run in Dev Mode

```bash
./gradlew :client:quarkusDev
# or
./gradlew :server:quarkusDev
```

### Run Tests

```bash
./gradlew test
```

### Build Native Executable

```bash
./gradlew build -Dquarkus.native.enabled=true
```

## Architecture

**Client** → WebSocket connection → **Server** → HTTP forwarding → **Internet**

- Client opens persistent WebSocket to server
- Server assigns subdomain and registers tunnel
- Incoming HTTP requests are forwarded through WebSocket to client
- Client proxies to local HTTP service

## Documentation

- [Feature Specifications](specs/) - Detailed feature specs
- [Client Quickstart](specs/002-tunnel-client-cli/quickstart.md) - CLI usage guide
- [Project Constitution](CONSTITUTION.md) - Development principles

## Contributing

See [CONSTITUTION.md](CONSTITUTION.md) for development guidelines and principles.
