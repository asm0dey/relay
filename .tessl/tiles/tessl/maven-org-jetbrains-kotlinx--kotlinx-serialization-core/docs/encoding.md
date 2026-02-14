# Encoding System

The encoding system provides format-agnostic interfaces for serializing and deserializing data. Format implementations use these interfaces to read and write data without being tied to specific serialization formats.

## Encoder Interfaces

### Encoder

Base interface for encoding primitive values and beginning structured encoding.

```kotlin { .api }
interface Encoder {
    val serializersModule: SerializersModule
    
    // Primitive encoding methods
    fun encodeBoolean(value: Boolean)
    fun encodeByte(value: Byte)
    fun encodeShort(value: Short)
    fun encodeInt(value: Int)
    fun encodeLong(value: Long)
    fun encodeFloat(value: Float)
    fun encodeDouble(value: Double)
    fun encodeChar(value: Char)
    fun encodeString(value: String)
    
    // Special encoding methods
    fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int)
    fun encodeInline(descriptor: SerialDescriptor): Encoder
    
    // Nullable handling (experimental)
    @ExperimentalSerializationApi
    fun encodeNotNullMark()
    @ExperimentalSerializationApi  
    fun encodeNull()
    
    // Structure encoding
    fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder
    fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder
    
    // Serialization helpers
    fun <T : Any?> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T)
    
    @ExperimentalSerializationApi
    fun <T : Any> encodeNullableSerializableValue(
        serializer: SerializationStrategy<T>,
        value: T?
    )
}
```

### CompositeEncoder  

Interface for encoding structured data with multiple elements.

```kotlin { .api }
interface CompositeEncoder {
    val serializersModule: SerializersModule
    
    // Element encoding methods for primitives
    fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean)
    fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte)
    fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short)
    fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int)
    fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long)
    fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float)
    fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double)
    fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char)
    fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String)
    
    // Complex element encoding
    fun <T : Any?> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    )
    
    @ExperimentalSerializationApi
    fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    )
    
    // Inline element encoding
    fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder
    
    // Structure control
    fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder
    fun endStructure(descriptor: SerialDescriptor)
    
    // Encoding behavior control
    @ExperimentalSerializationApi
    fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean
}
```

## Decoder Interfaces

### Decoder

Base interface for decoding primitive values and beginning structured decoding.

```kotlin { .api }
interface Decoder {
    val serializersModule: SerializersModule
    
    // Primitive decoding methods
    fun decodeBoolean(): Boolean
    fun decodeByte(): Byte
    fun decodeShort(): Short
    fun decodeInt(): Int
    fun decodeLong(): Long
    fun decodeFloat(): Float
    fun decodeDouble(): Double
    fun decodeChar(): Char
    fun decodeString(): String
    
    // Special decoding methods
    fun decodeEnum(enumDescriptor: SerialDescriptor): Int
    fun decodeInline(descriptor: SerialDescriptor): Decoder
    
    // Nullable handling (experimental)
    @ExperimentalSerializationApi
    fun decodeNotNullMark(): Boolean
    @ExperimentalSerializationApi
    fun decodeNull(): Nothing?
    
    // Structure decoding
    fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder
    
    // Serialization helpers
    fun <T : Any?> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T
    
    @ExperimentalSerializationApi
    fun <T : Any> decodeNullableSerializableValue(
        deserializer: DeserializationStrategy<T?>
    ): T?
}
```

### CompositeDecoder

Interface for decoding structured data with multiple elements.

```kotlin { .api }
interface CompositeDecoder {
    val serializersModule: SerializersModule
    
    // Element discovery
    fun decodeElementIndex(descriptor: SerialDescriptor): Int
    fun decodeCollectionSize(descriptor: SerialDescriptor): Int
    
    @ExperimentalSerializationApi
    fun decodeSequentially(): Boolean
    
    // Element decoding methods for primitives
    fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean
    fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte
    fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short
    fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int
    fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long
    fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float
    fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double
    fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char
    fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String
    
    // Complex element decoding
    fun <T : Any?> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T? = null
    ): T
    
    @ExperimentalSerializationApi
    fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T? = null  
    ): T?
    
    // Inline element decoding
    fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder
    
    // Structure control
    fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder
    fun endStructure(descriptor: SerialDescriptor)
    
    companion object {
        const val DECODE_DONE: Int = -1
        const val UNKNOWN_NAME: Int = -3
    }
}
```

## Encoding Usage Examples

### Basic Encoder Implementation

```kotlin
class CustomEncoder : Encoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    
    override fun encodeString(value: String) {
        // Custom string encoding logic
        println("Encoding string: $value")
    }
    
    override fun encodeInt(value: Int) {
        // Custom integer encoding logic
        println("Encoding int: $value")
    }
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        println("Beginning structure: ${descriptor.serialName}")
        return CustomCompositeEncoder()
    }
    
    // ... implement other required methods
}
```

### Basic Decoder Implementation

```kotlin
class CustomDecoder(private val data: Map<String, Any>) : Decoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    
    override fun decodeString(): String {
        // Custom string decoding logic
        return data["current"] as String
    }
    
    override fun decodeInt(): Int {
        // Custom integer decoding logic
        return data["current"] as Int
    }
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        println("Beginning structure: ${descriptor.serialName}")
        return CustomCompositeDecoder(data)
    }
    
    // ... implement other required methods
}
```

### Composite Encoding Pattern

```kotlin
class CustomCompositeEncoder : CompositeEncoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    
    override fun encodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: String
    ) {
        val elementName = descriptor.getElementName(index)
        println("Encoding element '$elementName' at index $index: $value")
    }
    
    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        val elementName = descriptor.getElementName(index)
        println("Encoding serializable element '$elementName' at index $index")
        
        // Create nested encoder for the element
        val elementEncoder = createNestedEncoder()
        serializer.serialize(elementEncoder, value)
    }
    
    override fun endStructure(descriptor: SerialDescriptor) {
        println("Ending structure: ${descriptor.serialName}")
    }
    
    // ... implement other required methods
}
```

### Composite Decoding Pattern

```kotlin
class CustomCompositeDecoder(
    private val data: Map<String, Any>
) : CompositeDecoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    private var currentIndex = 0
    
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // Return next available element index or DECODE_DONE when finished
        return if (currentIndex < descriptor.elementsCount) {
            currentIndex++
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }
    
    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int
    ): String {
        val elementName = descriptor.getElementName(index)
        return data[elementName] as String
    }
    
    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        val elementName = descriptor.getElementName(index)
        val elementData = data[elementName] as Map<String, Any>
        
        // Create nested decoder for the element
        val elementDecoder = CustomDecoder(elementData)
        return deserializer.deserialize(elementDecoder)
    }
    
    // ... implement other required methods
}
```

## Advanced Encoding Features

### Inline Value Handling

```kotlin
// In custom encoder
override fun encodeInline(descriptor: SerialDescriptor): Encoder {
    // Return encoder for inline value content
    return this // or create specialized inline encoder
}

// In custom decoder  
override fun decodeInline(descriptor: SerialDescriptor): Decoder {
    // Return decoder for inline value content
    return this // or create specialized inline decoder
}
```

### Null Handling

```kotlin
// Encoding nulls
override fun encodeNull() {
    println("Encoding null value")
}

override fun encodeNotNullMark() {
    println("Encoding not-null marker")
}

// Decoding nulls
override fun decodeNotNullMark(): Boolean {
    // Return true if next value is not null
    return true
}

override fun decodeNull(): Nothing? {
    println("Decoding null value")
    return null
}
```

### Default Value Handling

```kotlin
// In CompositeEncoder
override fun shouldEncodeElementDefault(
    descriptor: SerialDescriptor,
    index: Int
): Boolean {
    // Control whether default values should be encoded
    val elementName = descriptor.getElementName(index)
    return !elementName.startsWith("optional")
}
```

## Extension Functions

### Encoder Extensions

```kotlin { .api }
// Structure encoding helpers
inline fun Encoder.encodeStructure(
    descriptor: SerialDescriptor, 
    crossinline block: CompositeEncoder.() -> Unit
)

inline fun Encoder.encodeCollection(
    descriptor: SerialDescriptor,
    collectionSize: Int,
    crossinline block: CompositeEncoder.() -> Unit
)

inline fun <E> Encoder.encodeCollection(
    descriptor: SerialDescriptor,
    collection: Collection<E>,
    crossinline block: CompositeEncoder.(index: Int, E) -> Unit
)
```

### Decoder Extensions

```kotlin { .api }
// Structure decoding helpers
inline fun <T> Decoder.decodeStructure(
    descriptor: SerialDescriptor,
    crossinline block: CompositeDecoder.() -> T
): T
```

## Format Implementation Tips

1. **Stateful Encoders/Decoders**: Maintain state for tracking current position, nesting levels, etc.
2. **Error Handling**: Throw descriptive `SerializationException`s for format-specific errors
3. **Performance**: Cache frequently accessed data like element names and descriptors
4. **Validation**: Validate data structure matches descriptor expectations
5. **Null Safety**: Properly handle nullable types and null markers