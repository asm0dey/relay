# Core Serialization API

The core serialization API provides fundamental interfaces, annotations, and utility functions that form the foundation of the kotlinx-serialization framework.

## Serialization Strategy Interfaces

### KSerializer

The main serializer interface that combines serialization and deserialization capabilities.

```kotlin { .api }
interface KSerializer<T> : SerializationStrategy<T>, DeserializationStrategy<T> {
    override val descriptor: SerialDescriptor
}
```

### SerializationStrategy

Defines the contract for encoding objects to serialized form.

```kotlin { .api }
interface SerializationStrategy<in T> {
    val descriptor: SerialDescriptor
    fun serialize(encoder: Encoder, value: T)
}
```

### DeserializationStrategy

Defines the contract for decoding objects from serialized form.

```kotlin { .api }
interface DeserializationStrategy<T> {
    val descriptor: SerialDescriptor
    fun deserialize(decoder: Decoder): T
}
```

## Format Interfaces

### SerialFormat

Base interface for all serialization formats.

```kotlin { .api }
interface SerialFormat {
    val serializersModule: SerializersModule
}
```

### StringFormat

Format that serializes to and from strings.

```kotlin { .api }
interface StringFormat : SerialFormat {
    fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String
    fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T
}
```

**Extension Functions:**
```kotlin { .api }
inline fun <reified T> StringFormat.encodeToString(value: T): String
inline fun <reified T> StringFormat.decodeFromString(string: String): T
```

### BinaryFormat

Format that serializes to and from byte arrays.

```kotlin { .api }
interface BinaryFormat : SerialFormat {
    fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray
    fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T
}
```

**Extension Functions:**
```kotlin { .api }
inline fun <reified T> BinaryFormat.encodeToByteArray(value: T): ByteArray
inline fun <reified T> BinaryFormat.decodeFromByteArray(bytes: ByteArray): T

// Hex encoding utilities
fun <T> BinaryFormat.encodeToHexString(
    serializer: SerializationStrategy<T>,
    value: T,
    lowerCase: Boolean = false
): String
fun <T> BinaryFormat.decodeFromHexString(
    deserializer: DeserializationStrategy<T>,
    hex: String
): T
inline fun <reified T> BinaryFormat.encodeToHexString(
    value: T,
    lowerCase: Boolean = false
): String
inline fun <reified T> BinaryFormat.decodeFromHexString(hex: String): T
```

## Core Annotations

### @Serializable

Marks classes for automatic serialization code generation.

```kotlin { .api }
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable(val with: KClass<out KSerializer<*>> = KSerializer::class)
```

**Usage:**
```kotlin
@Serializable
data class User(val name: String, val email: String)

// Custom serializer
@Serializable(with = UserSerializer::class)
data class CustomUser(val id: Long, val data: String)
```

### @SerialName

Overrides the default property or class name in serialized form.

```kotlin { .api }
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class SerialName(val value: String)
```

**Usage:**
```kotlin
@Serializable
@SerialName("person")
data class User(
    @SerialName("full_name")
    val name: String,
    val email: String
)
```

### @Transient

Excludes properties from serialization.

```kotlin { .api }
@Target(AnnotationTarget.PROPERTY)
annotation class Transient
```

**Usage:**
```kotlin
@Serializable
data class User(
    val name: String,
    @Transient
    val password: String = ""
)
```

### @Required

Marks optional properties as required in input during deserialization.

```kotlin { .api }
@Target(AnnotationTarget.PROPERTY)
annotation class Required
```

### @EncodeDefault

Controls when default values are encoded.

```kotlin { .api }
@ExperimentalSerializationApi
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class EncodeDefault(val mode: Mode = Mode.ALWAYS) {
    enum class Mode {
        ALWAYS,
        NEVER
    }
}
```

### Contextual and Polymorphic Annotations

```kotlin { .api }
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class Contextual

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class Polymorphic

@ExperimentalSerializationApi
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class InheritableSerialInfo

@ExperimentalSerializationApi
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class MetaSerializable

@ExperimentalSerializationApi
@Target(AnnotationTarget.CLASS)
annotation class Serializer(val forClass: KClass<*> = KClass::class)

@Target(AnnotationTarget.FILE)
annotation class UseContextualSerialization(vararg val forClasses: KClass<*>)

@Target(AnnotationTarget.FILE) 
annotation class UseSerializers(vararg val serializerClasses: KClass<out KSerializer<*>>)

@ExperimentalSerializationApi
@Target(AnnotationTarget.CLASS)
annotation class KeepGeneratedSerializer

@ExperimentalSerializationApi
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class SerialInfo
```

## Utility Functions

### Serializer Resolution

```kotlin { .api }
// Get serializer for reified type
inline fun <reified T> serializer(): KSerializer<T>

// Get serializer for KType
fun serializer(type: KType): KSerializer<Any>
```

**Usage:**
```kotlin
val stringSerializer = serializer<String>()
val listSerializer = serializer<List<Int>>()
val customSerializer = serializer<MyClass>()
```

## Exception Classes

### SerializationException

Base exception for all serialization-related errors.

```kotlin { .api }
open class SerializationException(
    message: String, 
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
```

### MissingFieldException

Indicates that a required field is missing during deserialization.

```kotlin { .api }
@ExperimentalSerializationApi
class MissingFieldException(
    val missingFields: List<String>,
    message: String,
    cause: Throwable? = null
) : SerializationException(message, cause)
```

## Serializer Implementations

### ContextualSerializer

Delegates serialization to a contextual serializer from the `SerializersModule`.

```kotlin { .api }
@ExperimentalSerializationApi
class ContextualSerializer<T : Any>(
    val serializableClass: KClass<T>,
    val fallbackSerializer: KSerializer<T>? = null,
    val typeArgumentsSerializers: Array<KSerializer<*>>
) : KSerializer<T>
```

### Polymorphic Serializers

```kotlin { .api }
@ExperimentalSerializationApi
sealed class AbstractPolymorphicSerializer<T : Any> : KSerializer<T>

class PolymorphicSerializer<T : Any>(
    val baseClass: KClass<T>
) : AbstractPolymorphicSerializer<T>()

@ExperimentalSerializationApi
class SealedClassSerializer<T : Any>(
    val serialName: String,
    val baseClass: KClass<T>,
    val subclasses: Array<KClass<out T>>,
    val subSerializers: Array<KSerializer<out T>>
) : AbstractPolymorphicSerializer<T>()
```

## Experimental Annotations

```kotlin { .api }
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalSerializationApi

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)  
annotation class InternalSerializationApi
```

These annotations mark APIs that are either experimental or internal and not intended for public use.