# Quickstart: Tunnel Client CLI

**Feature**: 002-tunnel-client-cli  
**Date**: 2026-02-13

## Installation

The client is built as a Quarkus application:

```bash
./gradlew :client:build
```

Run the client:
```bash
java -jar client/build/quarkus-app/quarkus-run.jar [ARGS]
```

Or create a native executable:
```bash
./gradlew :client:build -Dquarkus.native.enabled=true
./client/build/client-1.0.0-SNAPSHOT-runner [ARGS]
```

## Basic Usage

### Minimal Command

Expose local service on port 3000:

```bash
client 3000 -s tun.example.com -k my-secret-key
```

Output:
```
Tunnel ready: https://abc123.tun.example.com -> localhost:3000
```

### With Custom Subdomain

```bash
client 3000 -s tun.example.com -d myapp -k my-secret-key
```

Output:
```
Tunnel ready: https://myapp.tun.example.com -> localhost:3000
```

### Local Development (Insecure)

```bash
client 3000 -s localhost:8080 -k test-key --insecure
```

Uses `ws://` instead of `wss://` for local server testing.

## Configuration File

Create `~/.relay/config.properties`:

```properties
relay.client.server-url=wss://tun.example.com/ws
relay.client.secret-key=my-default-key
```

Then run with just the port:
```bash
client 3000
```

Config precedence (highest to lowest):
1. CLI arguments (`-s`, `-k`)
2. Environment variables (`RELAY_SERVER_URL`, `RELAY_SECRET_KEY`)
3. Properties file (`~/.relay/config.properties`)
4. Defaults

## All Options

```
Usage: client [OPTIONS] <port>

Positional Arguments:
  <port>                Local HTTP service port (1-65535)

Options:
  -s, --server=<host>   Tunnel server hostname (required)
  -k, --key=<secret>    Authentication secret key (required)
  -d, --subdomain=<name> Requested subdomain [a-z0-9-]{1,63}
      --insecure        Use ws:// instead of wss://
  -q, --quiet           Suppress non-error output
  -v, --verbose         Enable debug logging
  -h, --help            Show help message and exit
```

## Exit Codes

| Code | Meaning | Example |
|------|---------|---------|
| 0 | Success | Normal shutdown |
| 1 | Invalid args/config | Missing required flag, invalid port |
| 2 | Connection failed | Server unreachable |
| 3 | Authentication failed | Invalid secret key |
| 130 | Interrupted | Ctrl+C pressed |

## Testing Scenarios

### Validation Tests

```bash
# Invalid port (should exit 1)
client 70000 -s tun.example.com -k secret

# Missing required flag (should exit 1)
client 3000 -s tun.example.com

# Invalid subdomain format (should exit 1)
client 3000 -s tun.example.com -k secret -d "invalid_domain"
```

### Connection Tests

```bash
# Help (should exit 0)
client --help

# Quiet mode (minimal output)
client 3000 -s tun.example.com -k secret --quiet

# Verbose mode (debug logging)
client 3000 -s tun.example.com -k secret --verbose
```

### Integration Test

```bash
# Start a local HTTP server on port 3000
python3 -m http.server 3000 &

# Create tunnel
client 3000 -s tun.example.com -k secret

# In another terminal, verify tunnel works
curl https://<subdomain>.tun.example.com
```

## Troubleshooting

### Connection Refused

```
Failed to connect to tun.example.com: Connection refused
```

- Check server URL is correct
- Verify server is running
- Try `--insecure` flag for local testing

### Authentication Failed

```
Authentication failed: Invalid secret key
```

- Verify secret key is correct
- Check key is registered with server

### Port Already in Use

```
Failed to forward: Connection refused to localhost:3000
```

- Verify local service is running on specified port
- Check correct port number
