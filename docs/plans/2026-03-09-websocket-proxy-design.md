# WebSocket Proxy Design

**Date:** 2026-03-09
**Status:** Draft

## Overview

This design adds WebSocket proxy support to the relay server. External clients can connect to the relay via WebSocket, and the relay transparently proxies these connections to local applications through the existing WebSocket channel. The local application remains unaware it is being proxied.

## Architecture

### High-level Flow

1. External client initiates WebSocket connection to relay at `/ws-upgrade/{domain}`
2. Relay generates a unique `wsId` for this WebSocket tunnel
3. Relay sends `WsUpgrade` message to local app over existing WebSocket channel with the `wsId`, path, query, headers, and subprotocols
4. Local app accepts or rejects (via `WsUpgradeResponse`)
5. If accepted, external WebSocket frames are wrapped in `WsMessage` envelopes and sent to local app
6. Local app sends back `WsMessage` envelopes, which relay unwraps and forwards to external client
7. When either side closes, relay sends `WsClose` and cleans up

### Components

1. **`WsTunnelManager`** - Manages active WebSocket tunnels
   - `openTunnel(wsId, connection)` - Register new tunnel
   - `getTunnel(wsId)` - Retrieve tunnel for message forwarding
   - `closeTunnel(wsId, code, reason)` - Close and cleanup
   - `cleanupForConnection(connectionId)` - Remove all tunnels for a relay-to-local connection

2. **`WsTunnel`** - Represents a single proxy tunnel
   - `wsId: String` - Unique identifier
   - `externalConnection: WebSocketConnection` - The external client connection
   - `localConnection: WebSocketConnection` - The relay-to-local connection
   - `state: TunnelState` - CONNECTING, OPEN, CLOSING, CLOSED

3. **`WsEndpoint`** - Quarkus WebSocket endpoint for external clients
   - `@WebSocket(path = "/ws-upgrade/{domain}")`
   - Handles upgrade handshake
   - Forwards frames to `WsTunnelManager`

4. **Extend `SocketService`** - Handle new WebSocket tunnel messages
   - Handle `WsUpgrade` → initiate with local app
   - Handle `WsUpgradeResponse` → accept/reject external client
   - Handle `WsMessage` → forward to external client
   - Handle `WsClose` → close external connection

## Protocol Messages

Add these message types to `domain.kt`:

```kotlin
@Serializable
data class WsUpgrade(@ProtoNumber(20) val value: WsUpgradePayload) : Payload {
    @Serializable
    data class WsUpgradePayload(
        @ProtoNumber(1) val wsId: String,
        @ProtoNumber(2) val path: String,
        @ProtoNumber(3) val query: Map<String, String>,
        @ProtoNumber(4) val headers: Map<String, String>,
        @ProtoNumber(5) val subprotocols: List<String>
    )
}

@Serializable
data class WsUpgradeResponse(@ProtoNumber(21) val value: WsUpgradeResponsePayload) : Payload {
    @Serializable
    data class WsUpgradeResponsePayload(
        @ProtoNumber(1) val wsId: String,
        @ProtoNumber(2) val accepted: Boolean,
        @ProtoNumber(3) val subprotocol: String? = null,
        @ProtoNumber(4) val statusCode: Int = 101,
        @ProtoNumber(5) val headers: Map<String, String> = emptyMap()
    )
}

@Serializable
data class WsMessage(@ProtoNumber(22) val value: WsMessagePayload) : Payload {
    @Serializable
    data class WsMessagePayload(
        @ProtoNumber(1) val wsId: String,
        @ProtoNumber(2) val type: FrameType,
        @ProtoNumber(3) val data: ByteArray
    )
    @Serializable
    enum class FrameType { TEXT, BINARY, PING, PONG, CLOSE }
}

@Serializable
data class WsClose(@ProtoNumber(23) val value: WsClosePayload) : Payload {
    @Serializable
    data class WsClosePayload(
        @ProtoNumber(1) val wsId: String,
        @ProtoNumber(2) val code: Int = 1000,
        @ProtoNumber(3) val reason: String = ""
    )
}
```

## Data Flow

### External WebSocket Upgrade Flow

```
1. External client → Relay: WebSocket upgrade to /ws-upgrade/{domain}
2. WsEndpoint.onOpen(): Generate wsId, extract headers/subprotocols
3. WsEndpoint → SocketService: Send WsUpgrade envelope
4. SocketService → Local App: Forward WsUpgrade
5. Local App → SocketService: Return WsUpgradeResponse (accepted/rejected)
6. SocketService → WsEndpoint: Forward response
7. If accepted: WsTunnelManager.registerTunnel(wsId, connection)
8. WsEndpoint completes handshake with external client
```

### Message Forwarding (After Connection Established)

```
External Client → WsEndpoint:
  onTextMessage() → WsMessage(wsId, TEXT, data) → SocketService → Local App

Local App → SocketService:
  WsMessage(wsId, TEXT, data) → WsTunnelManager.getTunnel(wsId) → External Client
```

### Close Flow

```
Either side initiates close → WsClose envelope → Other side → Actual close
WsTunnelManager.cleanup(wsId)
```

## Error Handling

1. **Local app rejects upgrade** - Send HTTP 101 response with appropriate status code (e.g., 403, 404) to external client, close connection

2. **Local app connection drops mid-stream** - Send `WsClose` to external client with code `1001 (Going Away)`, close all associated tunnels

3. **External client disconnects** - Send `WsClose` to local app, cleanup tunnel

4. **Protocol errors** - Invalid `wsId`, malformed messages → Send `StreamError` with `PROTOCOL_ERROR` to local app, close tunnel

5. **Timeouts** - No response from local app within configured timeout → Send `WsClose` with code `1000 (Normal Closure)` or custom timeout code

## Configuration

Add these properties to `application.properties` and `ServerConfig`:

```properties
relay.ws-upgrade-timeout=30s
relay.ws-max-tunnels=100
relay.ws-ping-interval=30s
```

## Testing Approach

### Test Scenarios

1. **Basic upgrade flow** - External client connects, local app accepts, messages flow both ways (TEXT and BINARY)

2. **Upgrade rejection** - Local app returns `accepted: false` → external client gets correct close code

3. **Subprotocol negotiation** - External client sends subprotocols, local app selects one

4. **Connection drops** - Local app disconnects → external client gets notified; External client disconnects → local app gets notified

5. **Ping/Pong** - Verify ping/pong frame handling

6. **Concurrent tunnels** - Multiple WebSocket connections over same relay-to-local connection

### Test Infrastructure

- Extend `FirstTest.kt` pattern with WebSocket test utilities
- Use Quarkus test utilities for WebSocket endpoint testing
- Mock local app responses for deterministic testing

## Implementation Notes

- Reuses existing WebSocket infrastructure (Quarkus WebSockets Next)
- Reuses existing Envelope/protobuf serialization
- No changes required to local client code - only server-side implementation
- The `RequestPayload.websocketUpgrade` field (currently unused) can be used for future HTTP-to-WebSocket upgrade proxying if needed
