# code-with-quarkus

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Kotlin ([guide](https://quarkus.io/guides/kotlin)): Write your services in Kotlin

## Large File Streaming

The relay supports streaming for large responses to avoid WebSocket message size limitations. Streaming is automatically enabled for responses exceeding the configured threshold.

### Configuration

Streaming behavior can be configured via application properties:

| Property | Default | Description |
|----------|---------|-------------|
| `relay.stream-threshold` | 1048576 (1MB) | Content length threshold above which streaming is used |
| `relay.chunk-size` | 65536 (64KB) | Size of each chunk sent over WebSocket |
| `relay.max-in-flight-chunks` | 10 | Maximum number of chunks that can be sent without waiting for acknowledgment |
| `relay.chunk-timeout` | 30s | Timeout for waiting for chunk acknowledgment |
| `relay.local-app-idle-timeout` | 60s | Timeout for local app idle detection |

### Streaming Protocol

The streaming protocol uses these message types:

1. **StreamInit** - Sent by the client to initiate a stream. Contains correlation ID, content type, content length, and headers.
2. **StreamChunk** - Data chunks sent over WebSocket. Each chunk contains correlation ID, chunk index, data, and is-last flag.
3. **StreamAck** - Acknowledgment sent by the server when it receives a chunk. Contains correlation ID and chunk index.
4. **StreamError** - Error message sent when streaming fails. Contains correlation ID, error code, and message.

### Stream Error Codes

Possible error codes in streaming:

- `CHUNK_OUT_OF_ORDER` - Chunk received out of expected order
- `CHUNK_MISSING` - Expected chunk is missing
- `STREAM_CANCELLED` - Stream was cancelled
- `TIMEOUT` - Operation timed out
- `INVALID_REQUEST` - Invalid streaming request
- `PROTOCOL_ERROR` - Protocol-level error
- `UPSTREAM_TIMEOUT` - Upstream (local app) timeout
- `UPSTREAM_ERROR` - Upstream error

## WebSocket Proxy Support

The relay supports WebSocket proxying, allowing external clients to connect to local applications through WebSocket.

### Connecting via WebSocket

External clients can connect to the relay at:
```
ws://relay-host/ws-upgrade/{domain}
```

The `{domain}` parameter should match the subdomain of the registered local application.

### Configuration

WebSocket proxy behavior can be configured via application properties:

| Property | Default | Description |
|----------|---------|-------------|
| `relay.ws-upgrade-timeout` | 30s | Timeout for WebSocket upgrade handshake |
| `relay.ws-max-tunnels` | 100 | Maximum concurrent WebSocket tunnels per connection |
| `relay.ws-ping-interval` | 30s | Keepalive ping interval |

### Protocol

WebSocket connections use these message types over the relay-to-local WebSocket channel:

1. **WsUpgrade** - External client initiates WebSocket upgrade. Contains wsId, path, query, headers, and subprotocols.
2. **WsUpgradeResponse** - Local app accepts/rejects upgrade. Contains accepted flag, selected subprotocol, status code, and headers.
3. **WsMessage** - Wraps WebSocket frames (TEXT, BINARY, PING, PONG, CLOSE).
4. **WsClose** - Notifies of connection close. Contains close code and reason.

