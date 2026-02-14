# Built-in Serializers

kotlinx-serialization-core provides a comprehensive collection of built-in serializers for Kotlin standard library types. These serializers handle primitives, collections, arrays, and other common types without requiring custom implementation.

## Primitive Serializers

### Basic Types

```kotlin { .api }
fun Boolean.Companion.serializer(): KSerializer<Boolean>
fun Byte.Companion.serializer(): KSerializer<Byte>
fun Short.Companion.serializer(): KSerializer<Short>
fun Int.Companion.serializer(): KSerializer<Int>
fun Long.Companion.serializer(): KSerializer<Long>
fun Float.Companion.serializer(): KSerializer<Float>
fun Double.Companion.serializer(): KSerializer<Double>
fun Char.Companion.serializer(): KSerializer<Char>
fun String.Companion.serializer(): KSerializer<String>
fun Unit.Companion.serializer(): KSerializer<Unit>
```

**Usage:**
```kotlin
val intSerializer = Int.serializer()
val stringSerializer = String.serializer()
val booleanSerializer = Boolean.serializer()
```

### Unsigned Types

```kotlin { .api }
fun UByte.Companion.serializer(): KSerializer<UByte>
fun UShort.Companion.serializer(): KSerializer<UShort>
fun UInt.Companion.serializer(): KSerializer<UInt>
fun ULong.Companion.serializer(): KSerializer<ULong>
```

## Nullable Serializers

### Nullable Extension

```kotlin { .api }
val <T> KSerializer<T>.nullable: KSerializer<T?>
```

**Usage:**
```kotlin
val nullableStringSerializer = String.serializer().nullable
val nullableIntSerializer = Int.serializer().nullable

// For custom types
val nullableUserSerializer = User.serializer().nullable
```

## Collection Serializers

### Lists and Sets

```kotlin { .api }
fun <T> ListSerializer(elementSerializer: KSerializer<T>): KSerializer<List<T>>
fun <T> SetSerializer(elementSerializer: KSerializer<T>): KSerializer<Set<T>>
fun <T> LinkedHashSetSerializer(elementSerializer: KSerializer<T>): KSerializer<LinkedHashSet<T>>
fun <T> HashSetSerializer(elementSerializer: KSerializer<T>): KSerializer<HashSet<T>>
```

**Usage:**
```kotlin
val stringListSerializer = ListSerializer(String.serializer())
val intSetSerializer = SetSerializer(Int.serializer())
val userListSerializer = ListSerializer(User.serializer())

// For nested collections
val listOfListsSerializer = ListSerializer(ListSerializer(String.serializer()))
```

### Maps  

```kotlin { .api }
fun <K, V> MapSerializer(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
): KSerializer<Map<K, V>>

fun <K, V> LinkedHashMapSerializer(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
): KSerializer<LinkedHashMap<K, V>>

fun <K, V> HashMapSerializer(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
): KSerializer<HashMap<K, V>>
```

**Usage:**
```kotlin
val stringIntMapSerializer = MapSerializer(String.serializer(), Int.serializer())
val userDataMapSerializer = MapSerializer(String.serializer(), User.serializer())

// Complex nested maps
val nestedMapSerializer = MapSerializer(
    String.serializer(),
    MapSerializer(String.serializer(), Int.serializer())
)
```

## Array Serializers

### Generic Arrays

```kotlin { .api }
@ExperimentalSerializationApi
inline fun <reified T : Any, reified E : T?> ArraySerializer(
    elementSerializer: KSerializer<E>
): KSerializer<Array<E>>

@ExperimentalSerializationApi
fun <T : Any, E : T?> ArraySerializer(
    kClass: KClass<T>, 
    elementSerializer: KSerializer<E>
): KSerializer<Array<E>>
```

**Usage:**
```kotlin
@OptIn(ExperimentalSerializationApi::class)
val stringArraySerializer = ArraySerializer(String.serializer())
val userArraySerializer = ArraySerializer(User::class, User.serializer())
```

### Primitive Arrays

```kotlin { .api }
fun ByteArraySerializer(): KSerializer<ByteArray>
fun ShortArraySerializer(): KSerializer<ShortArray>
fun IntArraySerializer(): KSerializer<IntArray>
fun LongArraySerializer(): KSerializer<LongArray>
fun FloatArraySerializer(): KSerializer<FloatArray>
fun DoubleArraySerializer(): KSerializer<DoubleArray>
fun CharArraySerializer(): KSerializer<CharArray>
fun BooleanArraySerializer(): KSerializer<BooleanArray>
```

### Unsigned Primitive Arrays

```kotlin { .api }
@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
fun UByteArraySerializer(): KSerializer<UByteArray>

@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
fun UShortArraySerializer(): KSerializer<UShortArray>

@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
fun UIntArraySerializer(): KSerializer<UIntArray>

@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
fun ULongArraySerializer(): KSerializer<ULongArray>
```

**Usage:**
```kotlin
val byteArraySerializer = ByteArraySerializer()
val intArraySerializer = IntArraySerializer()

@OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
val uintArraySerializer = UIntArraySerializer()
```

## Tuple Serializers

### Pair and Triple

```kotlin { .api }
fun <A, B> PairSerializer(
    aSerializer: KSerializer<A>,
    bSerializer: KSerializer<B>
): KSerializer<Pair<A, B>>

fun <A, B, C> TripleSerializer(
    aSerializer: KSerializer<A>,
    bSerializer: KSerializer<B>,
    cSerializer: KSerializer<C>
): KSerializer<Triple<A, B, C>>
```

**Usage:**
```kotlin
val stringIntPairSerializer = PairSerializer(String.serializer(), Int.serializer())
val userDataTripleSerializer = TripleSerializer(
    String.serializer(),
    Int.serializer(),
    User.serializer()
)
```

## Map Entry Serializers

```kotlin { .api }
fun <K, V> MapEntrySerializer(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
): KSerializer<Map.Entry<K, V>>
```

**Usage:**
```kotlin
val stringIntEntrySerializer = MapEntrySerializer(String.serializer(), Int.serializer())

// Useful for serializing individual map entries
val entry: Map.Entry<String, Int> = mapOf("key" to 42).entries.first()
```

## Time and UUID Serializers

### Duration Serializer

```kotlin { .api }
fun Duration.Companion.serializer(): KSerializer<Duration>
```

### Instant Serializer

```kotlin { .api }
@ExperimentalTime
fun Instant.Companion.serializer(): KSerializer<Instant>
```

### UUID Serializer  

```kotlin { .api }
@ExperimentalUuidApi
fun Uuid.Companion.serializer(): KSerializer<Uuid>
```

**Usage:**
```kotlin
val durationSerializer = Duration.serializer()

@OptIn(ExperimentalTime::class)
val instantSerializer = Instant.serializer()

@OptIn(ExperimentalUuidApi::class)
val uuidSerializer = Uuid.serializer()
```

## Special Serializers

### Nothing Serializer

```kotlin { .api }
@ExperimentalSerializationApi
fun NothingSerializer(): KSerializer<Nothing>
```

## Usage Examples

### Basic Serializer Usage

```kotlin
// Simple types
val stringSerializer = String.serializer()
val intSerializer = Int.serializer()

// Collections
val stringList = listOf("apple", "banana", "cherry")
val stringListSerializer = ListSerializer(String.serializer())

val userMap = mapOf("john" to User("John", 30), "jane" to User("Jane", 25))
val userMapSerializer = MapSerializer(String.serializer(), User.serializer())
```

### Complex Nested Structures

```kotlin
// List of maps
val listOfMapsSerializer = ListSerializer(
    MapSerializer(String.serializer(), Int.serializer())
)

// Map of lists  
val mapOfListsSerializer = MapSerializer(
    String.serializer(),
    ListSerializer(User.serializer())
)

// Nested collections with custom types
@Serializable
data class Department(val name: String, val employees: List<User>)

val departmentListSerializer = ListSerializer(Department.serializer())
```

### Nullable Collections

```kotlin
// Nullable collection
val nullableListSerializer = ListSerializer(String.serializer()).nullable

// Collection of nullable elements
val listOfNullablesSerializer = ListSerializer(String.serializer().nullable)

// Both nullable collection and nullable elements
val nullableListOfNullablesSerializer = ListSerializer(String.serializer().nullable).nullable
```

### Arrays and Primitive Arrays

```kotlin
// Generic arrays
val stringArraySerializer = ArraySerializer(String.serializer())
val userArraySerializer = ArraySerializer(User::class, User.serializer())

// Primitive arrays (more efficient)
val intArraySerializer = IntArraySerializer()
val byteArraySerializer = ByteArraySerializer()

// Example usage
val numbers = intArrayOf(1, 2, 3, 4, 5)
val bytes = byteArrayOf(0x01, 0x02, 0x03)
```

### Working with Tuples

```kotlin
// Simple pair
val coordinateSerializer = PairSerializer(Double.serializer(), Double.serializer())
val coordinate: Pair<Double, Double> = 10.5 to 20.3

// Complex triple with mixed types
val recordSerializer = TripleSerializer(
    String.serializer(),           // ID
    LocalDateTime.serializer(),    // Timestamp (requires contextual registration)
    User.serializer()              // User data
)
```

### Serializer Composition Patterns

```kotlin
// Building complex serializers from simpler ones
class UserRepository {
    // Serializer for user lookup cache
    val userCacheSerializer = MapSerializer(
        String.serializer(),                    // User ID
        PairSerializer(                         // Cached data
            User.serializer(),                  // User object
            Long.serializer()                   // Cache timestamp
        ).nullable                              // May be null if not cached
    )
    
    // Serializer for operation results
    val operationResultSerializer = PairSerializer(
        Boolean.serializer(),                   // Success flag
        ListSerializer(                         // Error messages
            String.serializer()
        )
    )
}
```

### Performance Considerations

```kotlin
// Prefer primitive array serializers for performance
val efficientByteArraySerializer = ByteArraySerializer()          // Fast
val genericByteArraySerializer = ArraySerializer(Byte.serializer()) // Slower

// Cache serializers when used frequently
object SerializerCache {
    val stringListSerializer = ListSerializer(String.serializer())
    val userMapSerializer = MapSerializer(String.serializer(), User.serializer())
    
    // Reuse these instances instead of creating new ones each time
}
```

## Integration with Custom Types

```kotlin
@Serializable
data class CustomCollection<T>(
    val items: List<T>,
    val metadata: Map<String, String>
) {
    companion object {
        fun <T> serializer(elementSerializer: KSerializer<T>): KSerializer<CustomCollection<T>> {
            return CustomCollection.serializer(elementSerializer)
        }
    }
}

// Usage with built-in serializers
val customStringCollectionSerializer = CustomCollection.serializer(String.serializer())
val customUserCollectionSerializer = CustomCollection.serializer(User.serializer())
```

Built-in serializers provide the foundation for serializing most common Kotlin types. They can be composed to handle complex nested data structures while maintaining type safety and performance.