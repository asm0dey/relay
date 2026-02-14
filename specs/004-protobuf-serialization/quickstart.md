# Quickstart: Testing Protobuf Serialization (v2.0.0)

**Purpose**: Verify Protobuf v2.0.0 implementation  
**Version**: 2.0.0 (Breaking Change - No JSON Support)

---

## Prerequisites

- v2.0.0 Server running
- v2.0.0 Client
- **Note**: v1.x clients are incompatible with v2.0.0 servers

---

## Test Scenarios

### 1. Basic Protobuf Connection

**Setup**: Start server, connect with v2.0.0 client

```bash
# Terminal 1: Start server
./gradlew :server:quarkusDev

# Terminal 2: Start v2.0.0 client
./relay-client 3000 --server tun.example.com --key secret
```

**Expected**: Tunnel establishes immediately, URL displayed

**Verify**: Check server logs for connection:
```
[TunnelWebSocket] Connection from /127.0.0.1:xxxxx - Protobuf v2.0.0
```

---

### 2. v1.x Incompatibility Test (Expected Failure)

**Setup**: Try connecting v1.x client to v2.0.0 server

```bash
# Using v1.x client (JSON)
./relay-client-v1.x 3000 --server tun.example.com --key secret
```

**Expected**: Connection fails immediately  
**Reason**: v1.x sends JSON, v2.0.0 expects Protobuf binary

**Verify**: Server logs show parse error and connection close

---

### 3. Message Size Comparison

**Setup**: Make HTTP request through tunnel, capture WebSocket frames

```bash
# With v2.0.0 client connected
curl https://<subdomain>.tun.example.com/api/test -X POST -d '{"test":"data"}'
```

**Verify**:
- Protobuf frame size < baseline JSON frame size (30%+ reduction)
- Use browser dev tools or `tcpdump` to measure

---

### 4. End-to-End Functionality

**Test Matrix**:

| Client Version | Server Version | Expected Result |
|----------------|----------------|-----------------|
| v2.0.0 | v2.0.0 | ✅ Success |
| v1.x | v2.0.0 | ❌ Connection fails (expected) |
| v2.0.0 | v1.x | ❌ Connection fails (expected) |

**Request Types**:

| Request Type | Expected Result |
|--------------|-----------------|
| GET | Success |
| POST with body | Success |
| WebSocket upgrade | Success |

---

### 5. Error Handling

**Test Cases**:

| Scenario | Expected Behavior |
|----------|-------------------|
| Malformed Protobuf | Connection closed with error |
| Valid Protobuf, invalid payload | Error response sent |
| Truncated message | Connection closed |

---

## Performance Benchmarks

### Serialization Speed

```bash
# Run benchmark test
./gradlew :shared:test --tests "*ProtobufBenchmark*"
```

**Expected Results**:
- Protobuf encode < 1ms for 1MB payload
- Protobuf decode < 1ms for 1MB payload
- Protobuf faster than JSON baseline

---

### Bandwidth Measurement

```bash
# Using integration test
./gradlew :server:test --tests "*BandwidthComparison*"
```

**Expected Results**:
- Protobuf messages 30%+ smaller than JSON baseline
- Bandwidth savings scale with message volume

---

## Debugging

### Inspect Protobuf Messages

```bash
# Save raw protobuf to file
curl ... > request.pb

# Decode using protoc (requires .proto file)
protoc --decode=Envelope relay.proto < request.pb
```

### Enable Verbose Logging

```bash
# Server
QUARKUS_LOG_LEVEL=DEBUG ./gradlew :server:quarkusDev

# Client
./relay-client 3000 ... --verbose
```

### Verify Metrics

Server metrics endpoint:
```bash
curl http://localhost:8080/q/metrics | grep websocket_protobuf
```

Expected output:
```
websocket_protobuf_messages_total{type="request"} 1000
websocket_protobuf_bytes_total 45000
```

---

## Regression Testing

Run full test suite:

```bash
./gradlew test
```

All tests should pass (assuming all updated for v2.0.0 Protobuf).

---

## v2.0.0 Verification Checklist

- [ ] v2.0.0 client connects to v2.0.0 server successfully
- [ ] v1.x client fails to connect to v2.0.0 server (expected)
- [ ] Message size reduced 30%+
- [ ] Serialization faster than JSON baseline
- [ ] No latency regression
- [ ] Error handling works correctly
- [ ] Malformed messages rejected properly
- [ ] All existing tests updated and passing
