# Migration Guide: v1.x to v2.0.0

**Date**: 2026-02-14  
**From**: v1.x (JSON protocol)  
**To**: v2.0.0 (Protobuf protocol)

---

## Breaking Change Notice

**Version 2.0.0 is a breaking change**. The protocol has changed from JSON to Protobuf binary format. **v1.x and v2.0.0 are mutually incompatible**.

---

## What Changed

| Aspect | v1.x | v2.0.0 |
|--------|------|--------|
| **Protocol Format** | JSON text | Protobuf binary |
| **Serialization Library** | kotlinx.serialization-json | kotlinx.serialization-protobuf |
| **First Message** | JSON Envelope | Binary Envelope |
| **HTTP Body Encoding** | Base64 string | Raw ByteArray |
| **Format Negotiation** | Not applicable | **Removed** (Protobuf only) |
| **Backward Compatibility** | N/A | **None** |

---

## Migration Requirements

### Coordinated Upgrade Required

You **must** upgrade both client and server together:

1. **Option A**: Upgrade all clients first, then servers
2. **Option B**: Upgrade all servers first, then clients
3. **Not supported**: Rolling/mixed deployment (v1.x ↔ v2.0.0)

### Version Compatibility

| Client | Server | Result |
|--------|--------|--------|
| v1.x | v1.x | ✅ Works |
| v2.0.0 | v2.0.0 | ✅ Works |
| v1.x | v2.0.0 | ❌ **Fails** (JSON vs Protobuf mismatch) |
| v2.0.0 | v1.x | ❌ **Fails** (Protobuf vs JSON mismatch) |

---

## Client Migration

### Gradle Dependency Update

```kotlin
// build.gradle.kts - REMOVE
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

// build.gradle.kts - ADD (if using shared module, this is transitive)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
```

### Code Changes

**Before (v1.x)**:
```kotlin
// Sending JSON
val json = Json { encodeDefaults = true }
val message = json.encodeToString(envelope)
webSocket.send(message)

// Receiving JSON
val envelope = json.decodeFromString<Envelope>(message)
```

**After (v2.0.0)**:
```kotlin
import kotlinx.serialization.protobuf.ProtoBuf

val protoBuf = ProtoBuf { encodeDefaults = true }

// Sending Protobuf
val bytes = protoBuf.encodeToByteArray(Envelope.serializer(), envelope)
webSocket.send(bytes)

// Receiving Protobuf
val envelope = protoBuf.decodeFromByteArray(Envelope.serializer(), bytes)
```

### Removed: --protobuf Flag

The `--protobuf` flag has been removed. Protobuf is now the only format:

```bash
# v1.x
./relay-client 3000 --server tun.example.com --key secret --protobuf

# v2.0.0
./relay-client 3000 --server tun.example.com --key secret
```

---

## Server Migration

### Gradle Dependency Update

```kotlin
// shared/build.gradle.kts - REPLACE
dependencies {
    // REMOVE
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    
    // ADD
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
}
```

### Code Changes

**Before (v1.x)**:
```kotlin
@OnMessage
fun onMessage(message: String, session: Session) {
    val envelope = Json.decodeFromString<Envelope>(message)
    // ...
}

@OnMessage
fun onMessageBinary(bytes: ByteArray, session: Session) {
    // Handle binary (if supporting Protobuf)
}
```

**After (v2.0.0)**:
```kotlin
@OnMessage
fun onMessage(bytes: ByteArray, session: Session) {
    val envelope = ProtoBuf.decodeFromByteArray<Envelope>(bytes)
    // ...
}
```

### Removed: Format Negotiation

All format negotiation code has been removed. The server expects Protobuf binary immediately:

```kotlin
// REMOVED - No format negotiation
// if (message == "PROTOBUF") { ... }
```

---

## Payload Changes

### HTTP Body Encoding

**Before (v1.x)**:
```kotlin
// Base64 encoded string
val body: String? = Base64.getEncoder().encodeToString(rawBody)
```

**After (v2.0.0)**:
```kotlin
// Raw ByteArray
val body: ByteArray? = rawBody
```

---

## Rollback Plan

If you need to rollback:

1. Stop all v2.0.0 clients
2. Revert server to v1.x
3. Deploy v1.x clients

**Note**: There is no runtime compatibility between versions.

---

## Testing Migration

1. Deploy v2.0.0 server to staging
2. Connect v2.0.0 client to staging server
3. Verify tunnel functionality
4. Test error handling (malformed messages)
5. Run performance benchmarks
6. Deploy to production (coordinated with client updates)

---

## FAQ

**Q: Can I run v1.x and v2.0.0 servers side by side?**  
A: Yes, on different ports or hosts. Clients must connect to the correct version.

**Q: What happens if a v1.x client connects to a v2.0.0 server?**  
A: The connection will fail immediately. The server expects Protobuf binary and will reject JSON.

**Q: Is there a migration period with dual format support?**  
A: No. v2.0.0 is a clean break. Both sides must be on the same version.

**Q: Why remove JSON support entirely?**  
A: Simpler code, less complexity, no format negotiation overhead, clear protocol contract.

---

## Support

For migration assistance:
- Review updated protocol contract: [contracts/websocket-protocol-v2.md](./contracts/websocket-protocol-v2.md)
- Check test specifications: [tests/test-specs.md](./tests/test-specs.md)
- Run quickstart scenarios: [quickstart.md](./quickstart.md)
