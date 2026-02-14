# Serializers Module

The serializers module system provides runtime resolution of contextual and polymorphic serializers, enabling flexible serialization strategies that can be configured at runtime rather than compile time.

## Core Module Classes

### SerializersModule

Container for contextual and polymorphic serializers that can be queried at runtime.

```kotlin { .api }
sealed class SerializersModule {
    @ExperimentalSerializationApi
    abstract fun <T : Any> getContextual(
        kClass: KClass<T>,
        typeArgumentsSerializers: List<KSerializer<*>> = emptyList()
    ): KSerializer<T>?
    
    @ExperimentalSerializationApi
    abstract fun <T : Any> getPolymorphic(
        baseClass: KClass<in T>,
        value: T
    ): SerializationStrategy<T>?
    
    @ExperimentalSerializationApi  
    abstract fun <T : Any> getPolymorphic(
        baseClass: KClass<in T>,
        serializedClassName: String?
    ): DeserializationStrategy<T>?
    
    @ExperimentalSerializationApi
    abstract fun dumpTo(collector: SerializersModuleCollector)
}
```

### SerializersModuleCollector

Base interface for collecting serializer registrations.

```kotlin { .api }
@ExperimentalSerializationApi
interface SerializersModuleCollector {
    fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>)
    fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>
    )
    fun <Base : Any, Sub : Base> polymorphic(
        baseClass: KClass<Base>,
        actualClass: KClass<Sub>,
        actualSerializer: KSerializer<Sub>
    )
    fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?
    )
    fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?
    )
}
```

### SerializersModuleBuilder

Builder for constructing SerializersModule instances.

```kotlin { .api }
class SerializersModuleBuilder : SerializersModuleCollector {
    // Overrides from SerializersModuleCollector (inherited methods)
    override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>)
    override fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>
    )
    override fun <Base : Any, Sub : Base> polymorphic(
        baseClass: KClass<Base>,
        actualClass: KClass<Sub>,
        actualSerializer: KSerializer<Sub>
    )
    override fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?
    )
    override fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?
    )
    
    // Additional methods specific to builder
    fun include(module: SerializersModule)
}
```

## Module Building Functions

### SerializersModule Constructor

Creates a new SerializersModule using a builder pattern.

```kotlin { .api }
fun SerializersModule(builderAction: SerializersModuleBuilder.() -> Unit): SerializersModule
```

### Factory Functions

```kotlin { .api }
fun EmptySerializersModule(): SerializersModule

@Deprecated("Replaced with EmptySerializersModule()")
val EmptySerializersModule: SerializersModule

operator fun SerializersModule.plus(other: SerializersModule): SerializersModule

infix fun SerializersModule.overwriteWith(other: SerializersModule): SerializersModule

fun <T : Any> serializersModuleOf(
    kClass: KClass<T>, 
    serializer: KSerializer<T>
): SerializersModule

inline fun <reified T : Any> serializersModuleOf(
    serializer: KSerializer<T>
): SerializersModule
```

## Polymorphic Module Builder

### PolymorphicModuleBuilder

Specialized builder for configuring polymorphic serialization within a specific base class.

```kotlin { .api }
class PolymorphicModuleBuilder<Base : Any> {
    fun <Sub : Base> subclass(clazz: KClass<Sub>, serializer: KSerializer<Sub>)
    inline fun <reified Sub : Base> subclass(serializer: KSerializer<Sub>)
    
    fun default(defaultSerializerProvider: (value: Base?) -> SerializationStrategy<Base>?)
    fun defaultDeserializer(
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<out Base>?
    )
}
```

**Extension Functions:**
```kotlin { .api }
inline fun <reified T : Any> SerializersModuleBuilder.contextual(serializer: KSerializer<T>)

inline fun <Base : Any> SerializersModuleBuilder.polymorphic(
    baseClass: KClass<Base>,
    baseSerializer: KSerializer<Base>? = null,
    builderAction: PolymorphicModuleBuilder<Base>.() -> Unit
)
```

## Usage Examples

### Basic Module Creation

```kotlin
val module = SerializersModule {
    // Register contextual serializers
    contextual(LocalDateTime::class, LocalDateTimeSerializer)
    contextual<BigDecimal>(BigDecimalSerializer)
    
    // Register polymorphic serializers
    polymorphic(Animal::class) {
        subclass(Dog::class, Dog.serializer())
        subclass(Cat::class, Cat.serializer())
    }
}
```

### Contextual Serialization

```kotlin
// Define a contextual serializer for external types
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}

// Register the contextual serializer
val module = SerializersModule {
    contextual(LocalDateTime::class, LocalDateTimeSerializer)
}

// Use with @Contextual annotation
@Serializable
data class Event(
    val name: String,
    @Contextual
    val timestamp: LocalDateTime
)
```

### Polymorphic Serialization

```kotlin
@Serializable
sealed class Animal {
    abstract val name: String
}

@Serializable
@SerialName("dog")
data class Dog(override val name: String, val breed: String) : Animal()

@Serializable
@SerialName("cat")  
data class Cat(override val name: String, val isIndoor: Boolean) : Animal()

// Configure polymorphic module
val module = SerializersModule {
    polymorphic(Animal::class) {
        subclass(Dog::class, Dog.serializer())
        subclass(Cat::class, Cat.serializer())
    }
}

// Usage with polymorphic property
@Serializable
data class Owner(
    val name: String,
    @Polymorphic
    val pet: Animal
)
```

### Advanced Polymorphic Configuration

```kotlin
sealed class Shape

@Serializable
@SerialName("circle")
data class Circle(val radius: Double) : Shape()

@Serializable  
@SerialName("rectangle")
data class Rectangle(val width: Double, val height: Double) : Shape()

val module = SerializersModule {
    polymorphic(Shape::class) {
        subclass(Circle::class, Circle.serializer())
        subclass(Rectangle::class, Rectangle.serializer())
        
        // Default serializer for unknown types during serialization
        default { value ->
            when (value) {
                is Circle -> Circle.serializer()
                is Rectangle -> Rectangle.serializer()
                else -> null
            }
        }
        
        // Default deserializer for unknown class names during deserialization
        defaultDeserializer { className ->
            when (className) {
                "legacy_circle" -> Circle.serializer()
                "legacy_rect" -> Rectangle.serializer()
                else -> null
            }
        }
    }
}
```

### Generic Type Contextual Serializers

```kotlin
// Custom serializer for generic types
class ListAsStringSerializer<T>(
    private val elementSerializer: KSerializer<T>
) : KSerializer<List<T>> {
    override val descriptor = PrimitiveSerialDescriptor("ListAsString", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: List<T>) {
        val json = value.joinToString(",") { element ->
            Json.encodeToString(elementSerializer, element)
        }
        encoder.encodeString("[$json]")
    }
    
    override fun deserialize(decoder: Decoder): List<T> {
        val jsonString = decoder.decodeString()
        // Parse and deserialize elements...
        TODO("Implement parsing logic")
    }
}

// Register with type parameter support
val module = SerializersModule {
    contextual(List::class) { typeArgs ->
        @Suppress("UNCHECKED_CAST")
        ListAsStringSerializer(typeArgs[0] as KSerializer<Any>)
    }
}
```

### Module Composition

```kotlin
val baseModule = SerializersModule {
    contextual(UUID::class, UUIDSerializer)
    contextual(LocalDate::class, LocalDateSerializer)
}

val extendedModule = SerializersModule {
    include(baseModule)
    
    contextual(ZonedDateTime::class, ZonedDateTimeSerializer)
    
    polymorphic(Event::class) {
        subclass(UserEvent::class, UserEvent.serializer())
        subclass(SystemEvent::class, SystemEvent.serializer())
    }
}

// Combine modules using plus operator
val combinedModule = baseModule + extendedModule
```

### Runtime Serializer Resolution

```kotlin
val module = SerializersModule {
    contextual(BigInteger::class, BigIntegerSerializer)
    polymorphic(Animal::class) {
        subclass(Dog::class, Dog.serializer())
        subclass(Cat::class, Cat.serializer())
    }
}

// Query contextual serializers
val bigIntSerializer = module.getContextual(BigInteger::class)
println("Found contextual serializer: ${bigIntSerializer != null}")

// Query polymorphic serializers
val dogInstance = Dog("Buddy", "Golden Retriever")
val dogSerializer = module.getPolymorphic(Animal::class, dogInstance)
println("Found polymorphic serializer: ${dogSerializer != null}")

// Query by class name for deserialization
val catDeserializer = module.getPolymorphic(Animal::class, "cat")
println("Found deserializer for 'cat': ${catDeserializer != null}")
```

### Integration with Formats

```kotlin
val module = SerializersModule {
    contextual(LocalDateTime::class, LocalDateTimeSerializer)
    polymorphic(Shape::class) {
        subclass(Circle::class, Circle.serializer())
        subclass(Rectangle::class, Rectangle.serializer())
    }
}

// Use with JSON format (requires kotlinx-serialization-json)
val json = Json {
    serializersModule = module
    // Other JSON configuration...
}

@Serializable
data class Drawing(
    val title: String,
    @Contextual
    val createdAt: LocalDateTime,
    @Polymorphic
    val shapes: List<Shape>
)

val drawing = Drawing(
    title = "My Drawing",
    createdAt = LocalDateTime.now(),
    shapes = listOf(
        Circle(5.0),
        Rectangle(10.0, 20.0)
    )
)

val jsonString = json.encodeToString(drawing)
val decoded = json.decodeFromString<Drawing>(jsonString)
```

## Best Practices

1. **Module Scope**: Create modules at application startup and reuse them
2. **Composition**: Use `include()` and `+` operator to compose modules from smaller, focused modules
3. **Default Providers**: Use default serializer/deserializer providers for handling unknown types gracefully
4. **Type Safety**: Prefer sealed classes for polymorphic hierarchies when possible
5. **Performance**: Cache module lookups in performance-critical code paths