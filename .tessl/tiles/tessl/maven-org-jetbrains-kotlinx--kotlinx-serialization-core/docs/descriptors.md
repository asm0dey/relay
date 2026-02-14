# Descriptors System

The descriptors system provides structural metadata and introspection capabilities for serializable types. It enables format implementations to understand the structure of serializable classes without relying on reflection.

## Core Descriptor Interface

### SerialDescriptor

The main interface for describing the structure and metadata of serializable types.

```kotlin { .api }
interface SerialDescriptor {
    val serialName: String
    val kind: SerialKind
    val elementsCount: Int
    val annotations: List<Annotation>
    val isNullable: Boolean
    
    fun getElementName(index: Int): String
    fun getElementIndex(name: String): Int
    fun getElementAnnotations(index: Int): List<Annotation>
    fun getElementDescriptor(index: Int): SerialDescriptor
    fun isElementOptional(index: Int): Boolean
}
```

**Properties:**
- `serialName`: Unique name identifying the serializable class
- `kind`: The kind of serializable structure (class, list, map, etc.)
- `elementsCount`: Number of elements/properties in the descriptor
- `annotations`: Annotations applied to the class
- `isNullable`: Whether the type can be null

**Methods:**
- `getElementName(index)`: Gets the name of the element at the given index
- `getElementIndex(name)`: Gets the index of the element with the given name
- `getElementAnnotations(index)`: Gets annotations for the element at the given index
- `getElementDescriptor(index)`: Gets the nested descriptor for the element at the given index
- `isElementOptional(index)`: Checks if the element at the given index is optional

## Serial Kinds

Serial kinds categorize different types of serializable structures.

### Base SerialKind

```kotlin { .api }
sealed class SerialKind {
    object ENUM : SerialKind()
    object CONTEXTUAL : SerialKind()
}
```

### PrimitiveKind

Represents primitive types.

```kotlin { .api }
sealed class PrimitiveKind : SerialKind() {
    object BOOLEAN : PrimitiveKind()
    object BYTE : PrimitiveKind()
    object CHAR : PrimitiveKind()
    object SHORT : PrimitiveKind()
    object INT : PrimitiveKind()
    object LONG : PrimitiveKind()
    object FLOAT : PrimitiveKind()
    object DOUBLE : PrimitiveKind()
    object STRING : PrimitiveKind()
}
```

### StructureKind

Represents structured types.

```kotlin { .api }
sealed class StructureKind : SerialKind() {
    object CLASS : StructureKind()
    object LIST : StructureKind()
    object MAP : StructureKind()
    object OBJECT : StructureKind()
}
```

### PolymorphicKind

Represents polymorphic types.

```kotlin { .api }
@ExperimentalSerializationApi
sealed class PolymorphicKind : SerialKind() {
    object SEALED : PolymorphicKind()
    object OPEN : PolymorphicKind()
}
```

## Descriptor Builder Functions

### buildClassSerialDescriptor

Creates a descriptor for class-like structures.

```kotlin { .api }
fun buildClassSerialDescriptor(
    serialName: String,
    vararg typeParameters: SerialDescriptor,
    builderAction: ClassSerialDescriptorBuilder.() -> Unit = {}
): SerialDescriptor
```

**Usage:**
```kotlin
val userDescriptor = buildClassSerialDescriptor("User") {
    element<String>("name")
    element<String>("email")
    element<Int>("age", isOptional = true)
}
```

### buildSerialDescriptor

Creates a generic descriptor with a specified kind.

```kotlin { .api }
fun buildSerialDescriptor(
    serialName: String,
    kind: SerialKind,
    vararg typeParameters: SerialDescriptor,
    builder: SerialDescriptorBuilder.() -> Unit = {}
): SerialDescriptor
```

### Collection Descriptor Builders

```kotlin { .api }
@ExperimentalSerializationApi
fun listSerialDescriptor(elementDescriptor: SerialDescriptor): SerialDescriptor

@ExperimentalSerializationApi
inline fun <reified T> listSerialDescriptor(): SerialDescriptor

@ExperimentalSerializationApi
fun mapSerialDescriptor(
    keyDescriptor: SerialDescriptor,
    valueDescriptor: SerialDescriptor
): SerialDescriptor

@ExperimentalSerializationApi
inline fun <reified K, reified V> mapSerialDescriptor(): SerialDescriptor

@ExperimentalSerializationApi
fun setSerialDescriptor(elementDescriptor: SerialDescriptor): SerialDescriptor

@ExperimentalSerializationApi
inline fun <reified T> setSerialDescriptor(): SerialDescriptor
```

**Usage:**
```kotlin
val stringListDescriptor = listSerialDescriptor(String.serializer().descriptor)
val intStringMapDescriptor = mapSerialDescriptor(
    Int.serializer().descriptor,
    String.serializer().descriptor
)
```

## Descriptor Builder Classes

### SerialDescriptorBuilder

Base builder for creating descriptors.

```kotlin { .api }
abstract class SerialDescriptorBuilder {
    abstract fun element(
        elementName: String,
        descriptor: SerialDescriptor,
        annotations: List<Annotation> = emptyList(),
        isOptional: Boolean = false
    )
    
    inline fun <reified T> element(
        elementName: String,
        serializer: KSerializer<T> = serializer(),
        annotations: List<Annotation> = emptyList(),
        isOptional: Boolean = false
    )
}
```

### ClassSerialDescriptorBuilder

Specialized builder for class descriptors.

```kotlin { .api }
class ClassSerialDescriptorBuilder(serialName: String) : SerialDescriptorBuilder() {
    override fun element(
        elementName: String,
        descriptor: SerialDescriptor,
        annotations: List<Annotation>,
        isOptional: Boolean
    )
}
```

**Usage:**
```kotlin
val descriptor = buildClassSerialDescriptor("Person") {
    element<String>("firstName")
    element<String>("lastName")
    element<Int>("age", isOptional = true)
    element("address", Address.serializer().descriptor, isOptional = true)
}
```

## Predefined Descriptors

### Primitive Descriptors

```kotlin { .api }
val BOOLEAN_DESCRIPTOR: SerialDescriptor
val BYTE_DESCRIPTOR: SerialDescriptor
val CHAR_DESCRIPTOR: SerialDescriptor
val SHORT_DESCRIPTOR: SerialDescriptor
val INT_DESCRIPTOR: SerialDescriptor
val LONG_DESCRIPTOR: SerialDescriptor
val FLOAT_DESCRIPTOR: SerialDescriptor
val DOUBLE_DESCRIPTOR: SerialDescriptor
val STRING_DESCRIPTOR: SerialDescriptor
val UNIT_DESCRIPTOR: SerialDescriptor
```

## Descriptor Annotations

### @ContextAware

Marks descriptors that require serialization context.

```kotlin { .api }
@Target(AnnotationTarget.CLASS)
annotation class ContextAware
```

## Descriptor Utilities

### Inline Descriptors

For inline value classes and primitive wrappers.

```kotlin { .api }
fun SerialDescriptor.getInlinedSerializer(): KSerializer<*>?
fun buildInlineDescriptor(
    inlineSerializerName: String,
    inlineValueDescriptor: SerialDescriptor
): SerialDescriptor
```

### Descriptor Equality and Hashing

```kotlin { .api }
fun SerialDescriptor.hashCodeImpl(): Int
fun SerialDescriptor.equalsImpl(other: SerialDescriptor): Boolean
```

## Usage Examples

### Custom Descriptor Creation

```kotlin
// Creating a descriptor for a custom data structure
val customDescriptor = buildClassSerialDescriptor("CustomData") {
    element<String>("id")
    element<List<String>>("tags")
    element<Map<String, Any>>("metadata", isOptional = true)
}

// Using the descriptor to understand structure
println("Serial name: ${customDescriptor.serialName}")
println("Kind: ${customDescriptor.kind}")
println("Elements count: ${customDescriptor.elementsCount}")

// Using extension properties
for ((index, elementDescriptor) in customDescriptor.elementDescriptors.withIndex()) {
    val elementName = customDescriptor.getElementName(index)
    val isOptional = customDescriptor.isElementOptional(index)
    
    println("Element $index: $elementName (${elementDescriptor.serialName}) - Optional: $isOptional")
}

// Iterate over element names
for (elementName in customDescriptor.elementNames) {
    val index = customDescriptor.getElementIndex(elementName)
    println("Element '$elementName' is at index $index")
}
```

### Working with Collections

```kotlin
// Descriptor for List<User>
val userListDescriptor = listSerialDescriptor(User.serializer().descriptor)

// Descriptor for Map<String, User>
val userMapDescriptor = mapSerialDescriptor(
    String.serializer().descriptor,
    User.serializer().descriptor
)

// Check descriptor properties
println("List kind: ${userListDescriptor.kind}") // StructureKind.LIST
println("Map kind: ${userMapDescriptor.kind}")   // StructureKind.MAP
```