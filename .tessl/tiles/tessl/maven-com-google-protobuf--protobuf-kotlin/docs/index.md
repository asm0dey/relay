# protobuf-kotlin

The protobuf-kotlin library provides idiomatic Kotlin language bindings and extensions for Google Protocol Buffers. It offers type-safe extension functions, DSL builders, enhanced collection types, and seamless interoperability with Java protobuf code while providing Kotlin-specific features like nullable types and enhanced type safety.

## Package Information

- **Package Name**: com.google.protobuf:protobuf-kotlin
- **Package Type**: maven
- **Language**: Kotlin
- **Installation**: Add to your Maven dependencies or Gradle build file:

```xml
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-kotlin</artifactId>
    <version>4.31.1</version>
</dependency>
```

```kotlin
implementation("com.google.protobuf:protobuf-kotlin:4.31.1")
```

## Core Imports

```kotlin
import com.google.protobuf.kotlin.*
```

For specific functionality:

```kotlin
// ByteString extensions
import com.google.protobuf.kotlin.toByteStringUtf8
import com.google.protobuf.kotlin.plus
import com.google.protobuf.kotlin.isNotEmpty

// Any message operations
import com.google.protobuf.kotlin.isA
import com.google.protobuf.kotlin.unpack

// Extension field operations
import com.google.protobuf.kotlin.get
import com.google.protobuf.kotlin.set
import com.google.protobuf.kotlin.contains
```

## Basic Usage

```kotlin
import com.google.protobuf.kotlin.*
import com.google.protobuf.ByteString
import com.google.protobuf.Any as ProtoAny

// ByteString operations
val message = "Hello, Protocol Buffers!"
val byteString = message.toByteStringUtf8()
val combined = byteString + " From Kotlin!".toByteStringUtf8()
val isEmpty = byteString.isNotEmpty() // true

// Any message type checking and unpacking
val packedMessage: ProtoAny = // ... some packed message
if (packedMessage.isA<MyMessageType>()) {
    val unpacked = packedMessage.unpack<MyMessageType>()
    // Use unpacked message
}

// Extension field access (with generated message builders)
val builder = MyExtendableMessage.newBuilder()
builder[myExtensionField] = "extension value"
val hasExtension = builder.contains(myExtensionField)
val extensionValue = builder[myExtensionField]
```

## Architecture

The protobuf-kotlin library is built around several key design patterns:

- **Extension Functions**: Kotlin extension functions add idiomatic APIs to existing Java protobuf types
- **Type Safety**: Heavy use of generics and reified types for compile-time type checking
- **Immutable Collections**: All collection wrappers provide unmodifiable views for safety
- **Java Interoperability**: Designed to work seamlessly across Kotlin/Java boundaries
- **Generated Code Support**: Many APIs are optimized for use by protoc-generated Kotlin code
- **DSL Infrastructure**: Provides foundation for type-safe DSL builders in generated code

## Capabilities

### ByteString Extensions

Kotlin idiomatic extensions for ByteString operations including conversion from strings and byte arrays, concatenation, indexing, and utility functions.

```kotlin { .api }
fun String.toByteStringUtf8(): ByteString
operator fun ByteString.plus(other: ByteString): ByteString
operator fun ByteString.get(index: Int): Byte
fun ByteString.isNotEmpty(): Boolean
fun ByteArray.toByteString(): ByteString
fun ByteBuffer.toByteString(): ByteString
```

[ByteString Extensions](./bytestring-extensions.md)

### Any Message Operations

Type-safe operations for protocol buffer Any messages, allowing runtime type checking and unpacking with compile-time type safety through reified generics.

```kotlin { .api }
inline fun <reified T : Message> ProtoAny.isA(): Boolean
inline fun <reified T : Message> ProtoAny.unpack(): T
```

[Any Message Operations](./any-message-operations.md)

### Extension Field Access

Operator overloads for intuitive extension field access on extendable messages and builders, providing get, set, and contains operations with full type safety.

```kotlin { .api }
operator fun <M : GeneratedMessage.ExtendableMessage<M>, B : GeneratedMessage.ExtendableBuilder<M, B>, T : Any> 
    B.set(extension: ExtensionLite<M, T>, value: T)
    
operator fun <M : GeneratedMessage.ExtendableMessage<M>, MorBT : GeneratedMessage.ExtendableMessageOrBuilder<M>, T : Any> 
    MorBT.get(extension: ExtensionLite<M, T>): T
    
operator fun <M : GeneratedMessage.ExtendableMessage<M>, MorBT : GeneratedMessage.ExtendableMessageOrBuilder<M>> 
    MorBT.contains(extension: ExtensionLite<M, *>): Boolean
```

[Extension Field Access](./extension-field-access.md)

### DSL Infrastructure

Core infrastructure for generated protobuf DSL including collection wrappers, proxy types, and annotations for type-safe message building.

```kotlin { .api }
@ProtoDslMarker
@OnlyForUseByGeneratedProtoCode
annotation class ProtoDslMarker

class DslList<E, P : DslProxy>
class DslMap<K, V, P : DslProxy>
abstract class DslProxy

class ExtensionList<E, M : MessageLite>
```

[DSL Infrastructure](./dsl-infrastructure.md)

## Types

### Core Annotations

```kotlin { .api }
@ProtoDslMarker
annotation class ProtoDslMarker

@RequiresOptIn(
    message = "This API is only intended for use by generated protobuf code",
    level = RequiresOptIn.Level.ERROR
)
annotation class OnlyForUseByGeneratedProtoCode
```

### Extension Types

```kotlin { .api }
// From Java protobuf library (imported)
import com.google.protobuf.ExtensionLite
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Message
import com.google.protobuf.MessageLite
import com.google.protobuf.ByteString
import com.google.protobuf.Any as ProtoAny
import java.nio.ByteBuffer
```