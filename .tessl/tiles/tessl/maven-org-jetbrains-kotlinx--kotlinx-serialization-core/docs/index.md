# kotlinx-serialization-core

Kotlin multiplatform reflectionless serialization library providing format-agnostic serialization for classes marked with `@Serializable`. The core module contains fundamental serialization infrastructure supporting multiple target platforms including JVM, JavaScript, and Native targets.

**Note**: Some APIs are marked with `@ExperimentalSerializationApi` and may change in future versions. Stable APIs form the foundation for production use, while experimental APIs provide access to advanced features.

## Package Information

- **Package Name**: kotlinx-serialization-core
- **Package Type**: maven
- **Language**: Kotlin
- **Installation**: 
  ```kotlin
  dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
  }
  ```

## Core Imports

```kotlin
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import kotlinx.serialization.builtins.*
```

## Basic Usage

```kotlin
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class User(val name: String, val email: String)

fun main() {
    val user = User("John Doe", "john@example.com")
    
    // Serialization to JSON (requires kotlinx-serialization-json)
    val json = Json.encodeToString(user)
    println(json) // {"name":"John Doe","email":"john@example.com"}
    
    // Deserialization from JSON
    val deserializedUser = Json.decodeFromString<User>(json)
    println(deserializedUser) // User(name=John Doe, email=john@example.com)
}
```

## Architecture

The library uses a plugin-based architecture:
- **Compiler Plugin**: Generates serialization code for `@Serializable` classes at compile time
- **Runtime Library**: Provides serialization infrastructure, format interfaces, and built-in serializers
- **Format Abstraction**: Supports any serialization format through `Encoder`/`Decoder` interfaces
- **Modular Design**: Core API is format-agnostic, with separate format-specific libraries (JSON, ProtoBuf, etc.)
- **Multiplatform Support**: Works across JVM, JavaScript, and Native platforms

## Capabilities

### Core Serialization API

Fundamental serialization interfaces and annotations for marking classes and controlling serialization behavior.

```kotlin { .api }
// Main serializer interface
interface KSerializer<T> : SerializationStrategy<T>, DeserializationStrategy<T> {
    override val descriptor: SerialDescriptor
}

// Core annotations
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable(val with: KClass<out KSerializer<*>> = KSerializer::class)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class SerialName(val value: String)
```

[Core Serialization API](./core-api.md)

### Descriptors System

Type descriptor system providing structural metadata and introspection capabilities for serializable types.

```kotlin { .api }
interface SerialDescriptor {
    val serialName: String
    val kind: SerialKind
    val elementsCount: Int
    fun getElementName(index: Int): String
    fun getElementDescriptor(index: Int): SerialDescriptor
}

fun buildClassSerialDescriptor(
    serialName: String,
    vararg typeParameters: SerialDescriptor,
    builderAction: ClassSerialDescriptorBuilder.() -> Unit = {}
): SerialDescriptor
```

[Descriptors System](./descriptors.md)

### Encoding System

Format-agnostic encoding and decoding interfaces that form the foundation for all serialization formats.

```kotlin { .api }
interface Encoder {
    val serializersModule: SerializersModule
    fun encodeBoolean(value: Boolean)
    fun encodeString(value: String)
    fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder
}

interface Decoder {
    val serializersModule: SerializersModule
    fun decodeBoolean(): Boolean
    fun decodeString(): String
    fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder
}
```

[Encoding System](./encoding.md)

### Serializers Module

Runtime system for contextual and polymorphic serializer resolution, enabling flexible serialization strategies.

```kotlin { .api }
class SerializersModule {
    fun getContextual(kClass: KClass<*>): KSerializer<*>?
    fun getPolymorphic(baseClass: KClass<out Any>, value: Any): SerializationStrategy<Any>?
}

fun SerializersModule(builderAction: SerializersModuleBuilder.() -> Unit): SerializersModule
```

[Serializers Module](./modules.md)

### Built-in Serializers

Comprehensive collection of serializers for Kotlin standard library types including primitives, collections, and special types.

```kotlin { .api }
// Primitive serializers
fun Boolean.Companion.serializer(): KSerializer<Boolean>
fun String.Companion.serializer(): KSerializer<String>

// Collection serializers
fun <T> ListSerializer(elementSerializer: KSerializer<T>): KSerializer<List<T>>
fun <K, V> MapSerializer(
    keySerializer: KSerializer<K>, 
    valueSerializer: KSerializer<V>
): KSerializer<Map<K, V>>
```

[Built-in Serializers](./builtins.md)

## Common Types

```kotlin { .api }
// Core serialization exception
open class SerializationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

// Missing field exception for required properties
class MissingFieldException(
    val missingFields: List<String>,
    message: String,
    cause: Throwable? = null
) : SerializationException(message, cause)

// Serial kinds for descriptor system
sealed class SerialKind {
    object ENUM : SerialKind()
    object CONTEXTUAL : SerialKind()
}

sealed class PrimitiveKind : SerialKind() {
    object BOOLEAN : PrimitiveKind()
    object STRING : PrimitiveKind()
    object INT : PrimitiveKind()
    // ... other primitive kinds
}

sealed class StructureKind : SerialKind() {
    object CLASS : StructureKind()
    object LIST : StructureKind()
    object MAP : StructureKind()
    object OBJECT : StructureKind()
}
```