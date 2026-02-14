# Research: Protobuf Library Selection

**Date**: 2026-02-13  
**Purpose**: Evaluate Kotlin Protobuf libraries for Relay tunneling service

## Options Evaluated

### Option 1: kotlinx.serialization-protobuf (SELECTED)

**Description**: Protobuf format for kotlinx.serialization framework

**Pros**:
- Already using kotlinx.serialization for JSON - single framework
- No code generation required
- Works with existing `@Serializable` data classes
- Minimal build configuration changes
- Type-safe with compile-time checks
- @ProtoNumber annotation for field ordering control
- @ProtoOneOf support for union types

**Cons**:
- No native .proto file (but can be documented separately)
- Slightly less efficient than Google protobuf (negligible for our use case)

**Usage**:
```kotlin
import kotlinx.serialization.protobuf.ProtoBuf

// Serialize
val bytes = ProtoBuf.encodeToByteArray(Envelope.serializer(), envelope)

// Deserialize
val envelope = ProtoBuf.decodeFromByteArray(Envelope.serializer(), bytes)
```

### Option 2: protobuf-kotlin (Google Official)

**Description**: Official Google protobuf library with Kotlin DSL support

**Pros**:
- Official Google support, actively maintained
- Native .proto schema files
- Standard protoc plugin ecosystem
- Version 4.x has excellent Kotlin support

**Cons**:
- Requires protoc compiler setup
- Generated code checked in or build-time generation
- Adds complexity to build process
- Two different class hierarchies during migration

**Tessl Tile**: `tessl/maven-com-google-protobuf--protobuf-kotlin@4.31.0` available

**Decision**: Not selected - more complex than needed for our use case

### Option 3: pbandk

**Description**: Pure Kotlin protobuf implementation with code generation

**Pros**:
- Pure Kotlin (no native protoc dependency)
- Good Kotlin-native support
- Nice DSL for building messages

**Cons**:
- Smaller community than Google official
- Less mature ecosystem
- Not as well tested with Quarkus
- Would require additional build configuration

**Decision**: Not selected - smaller ecosystem risk, not needed

## Final Decision

**Selected**: `kotlinx.serialization-protobuf`

**Rationale**:
1. Already using kotlinx.serialization - minimal changes
2. No code generation - faster build, simpler setup
3. Existing data classes work with just annotations added
4. Single serialization framework for both JSON and Protobuf
5. @ProtoNumber provides explicit field control for schema evolution
6. Quarkus compatible (no native code or special configuration)

## Build Integration

**Required Gradle Changes**:

```kotlin
// In shared/build.gradle.kts - minimal change
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")  // ADD
}
```

No plugin changes needed - uses existing kotlinx.serialization plugin.

## kotlinx.serialization-protobuf Features

### Core API

```kotlin
// Format instance (can customize)
val RelayProtoBuf = ProtoBuf {
    encodeDefaults = true
}

// Serialize to ByteArray
fun <T> encode(value: T, serializer: KSerializer<T>): ByteArray

// Deserialize from ByteArray
fun <T> decode(bytes: ByteArray, serializer: KSerializer<T>): T
```

### Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@ProtoNumber(n)` | Explicit field number | `@ProtoNumber(1) val id: String` |
| `@ProtoOneOf` | Union type (oneof) | On sealed class or property |
| `@ProtoPacked` | Packed repeated fields | `@ProtoPacked val items: List<Int>` |
| `@ProtoEnum` | Enum options | `@ProtoEnum(ProtoEnum.Mode.ORDINAL)` |

### Wire Format Compatibility

kotlinx.serialization-protobuf produces standard protobuf binary format:
- Field numbers encoded as varints
- Length-delimited encoding for embedded messages
- Compatible with other protobuf implementations

## Schema Design Decisions

### Payload Encoding: Sealed Class with @ProtoOneOf

**Selected Approach**:
```kotlin
@Serializable
sealed class Payload {
    @Serializable
    @SerialName("request")
    data class Request(@ProtoNumber(1) val data: RequestPayload) : Payload()
    
    @Serializable
    @SerialName("response") 
    data class Response(@ProtoNumber(1) val data: ResponsePayload) : Payload()
    // ...
}
```

**Rationale**:
- kotlinx.serialization handles polymorphism via sealed classes
- @SerialName provides wire format discrimination
- Type-safe union type

### Timestamp Format

**Decision**: `@Contextual` with existing `InstantSerializer`

```kotlin
@Serializable
class Envelope(
    @ProtoNumber(3)
    @Contextual
    val timestamp: Instant = Instant.now()
)
```

**Rationale**:
- Reuse existing InstantSerializer
- Encodes as String (ISO8601) or can customize
- Consistent with JSON format behavior

### Body Encoding

**Decision**: Use `ByteArray` for raw binary

**Rationale**:
- ~33% size reduction vs Base64 encoding (no overhead)
- No encoding/decoding CPU overhead
- Protobuf native binary field support
- Part of the core rationale for moving to Protobuf
- Direct byte transfer without transformation

## Migration Strategy

### Approach: Annotate Existing Classes

**Phase 1**: Add `@ProtoNumber` annotations to existing data classes
**Phase 2**: Create ProtoBuf serializer utilities
**Phase 3**: Implement format negotiation in WebSocket endpoints
**Phase 4**: Add client-side negotiation flag
**Phase 5**: (Future) Remove JSON support once all clients migrated

### Benefits Over Code Generation

| Aspect | kotlinx.serialization | Google protobuf |
|--------|----------------------|-----------------|
| Build time | No change | Slower (codegen) |
| IDE support | Full (existing classes) | Generated code |
| Debugging | Clear stack traces | Generated code layers |
| Flexibility | Kotlin-native features | Protobuf limitations |
| Migration | Incremental annotations | Class replacement |

## Unknowns Resolved

| Unknown | Resolution |
|---------|------------|
| Protobuf library | `kotlinx.serialization-protobuf` |
| Field numbering | `@ProtoNumber(n)` annotation |
| Union types | Sealed class with `@SerialName` |
| Build integration | Add dependency only |
| Timestamp encoding | `@Contextual` with custom serializer |
