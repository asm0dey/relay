# DSL Infrastructure

Core infrastructure for generated protobuf DSL including collection wrappers, proxy types, and annotations for type-safe message building.

## Capabilities

### DSL Marker Annotation

Provides DSL scoping for protocol buffer message generation APIs to prevent accidental access to outer DSL scopes.

```kotlin { .api }
/**
 * Indicates an API that is part of a DSL to generate protocol buffer messages
 * Prevents accidental access to receivers from outer scopes in DSL contexts
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@OnlyForUseByGeneratedProtoCode
annotation class ProtoDslMarker
```

**Usage Example:**

```kotlin
// Applied to generated DSL builder classes
@ProtoDslMarker
class MessageDslBuilder {
    // DSL methods here
}
```

### Generated Code Restriction Annotation

Restricts API usage to generated protocol buffer code, preventing misuse of internal APIs.

```kotlin { .api }
/**
 * Opt-in annotation to make it difficult to accidentally use APIs only intended 
 * for use by proto generated code
 * @param message Error message shown when API is used incorrectly
 * @param level RequiresOptIn level - ERROR prevents compilation
 */
@RequiresOptIn(
    message = "This API is only intended for use by generated protobuf code, the code generator, and their own tests. If this does not describe your code, you should not be using this API.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.ANNOTATION_CLASS)
annotation class OnlyForUseByGeneratedProtoCode
```

### DSL List Wrapper

Immutable List wrapper with type disambiguation for DSL contexts.

```kotlin { .api }
/**
 * A simple wrapper around a List with an extra generic parameter that can be used 
 * to disambiguate extension methods for different DSL contexts
 * @param E the element type
 * @param P the proxy type for disambiguation
 */
class DslList<E, P : DslProxy> @OnlyForUseByGeneratedProtoCode constructor(
    private val delegate: List<E>
) : List<E> by delegate {
    
    override fun iterator(): Iterator<E>
    override fun listIterator(): ListIterator<E>
    override fun listIterator(index: Int): ListIterator<E>
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
```

**Usage Example:**

```kotlin
// Used internally by generated code
val dslList: DslList<String, MyMessageProxy> = // ... created by generated code
// Standard List operations work
val size = dslList.size
val firstItem = dslList[0]
val isEmpty = dslList.isEmpty()
```

### DSL Map Wrapper

Immutable Map wrapper with type disambiguation for DSL contexts.

```kotlin { .api }
/**
 * A simple wrapper around a Map with an extra generic parameter that can be used 
 * to disambiguate extension methods for different DSL contexts
 * @param K the key type
 * @param V the value type  
 * @param P the proxy type for disambiguation
 */
class DslMap<K, V, P : DslProxy> @OnlyForUseByGeneratedProtoCode constructor(
    private val delegate: Map<K, V>
) : Map<K, V> by delegate {
    
    override val entries: Set<Map.Entry<K, V>>
    override val keys: Set<K>
    override val values: Collection<V>
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
```

**Usage Example:**

```kotlin
// Used internally by generated code
val dslMap: DslMap<String, Int, MyMessageProxy> = // ... created by generated code
// Standard Map operations work
val value = dslMap["key"]
val containsKey = dslMap.containsKey("key")
val allKeys = dslMap.keys
```

### DSL Proxy Type

Abstract marker class used for type disambiguation in DSL contexts.

```kotlin { .api }
/**
 * A type meaningful only for its existence, never intended to be instantiated
 * Used to provide different extension methods for DslList<Int, FooProxy> vs DslList<Int, BarProxy>
 */
abstract class DslProxy @OnlyForUseByGeneratedProtoCode protected constructor() {
    init {
        throw UnsupportedOperationException("A DslProxy should never be instantiated")
    }
}
```

**Usage Example:**

```kotlin
// Generated proxy types extend DslProxy
abstract class MyMessageProxy : DslProxy()

// Used as type parameter for disambiguation
val list1: DslList<Int, MyMessageProxy> = // ...
val list2: DslList<Int, OtherMessageProxy> = // ...
// These can have different extension methods despite same element type
```

### Extension List

Specialized List wrapper for protocol buffer extension fields.

```kotlin { .api }
/**
 * Represents an unmodifiable view of a repeated proto extension field
 * Like DslList but supports querying the extension field it represents
 * @param E the element type
 * @param M the message type that owns this extension
 */
class ExtensionList<E, M : MessageLite> @OnlyForUseByGeneratedProtoCode constructor(
    val extension: ExtensionLite<M, List<E>>,
    private val delegate: List<E>
) : List<E> by delegate {
    
    override fun iterator(): Iterator<E>
    override fun listIterator(): ListIterator<E>
    override fun listIterator(index: Int): ListIterator<E>
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
```

**Usage Example:**

```kotlin
// Used internally by generated code for extension lists
val extensionList: ExtensionList<String, MyMessage> = // ... created by generated code
// Access the extension definition
val extensionDef = extensionList.extension
val extensionName = extensionDef.descriptor.name

// Standard List operations
val items = extensionList.toList()
val firstItem = extensionList.firstOrNull()
```

## Internal Collection Utilities

### Unmodifiable Iterator Wrappers

Internal classes that ensure collections remain unmodifiable even from Java code.

```kotlin { .api }
// Internal utility classes for Java interop safety
internal class UnmodifiableIterator<E>(delegate: Iterator<E>) : Iterator<E> by delegate

internal class UnmodifiableListIterator<E>(delegate: ListIterator<E>) : ListIterator<E> by delegate

internal open class UnmodifiableCollection<E>(private val delegate: Collection<E>) : Collection<E> by delegate {
    override fun iterator(): Iterator<E>
}

internal class UnmodifiableSet<E>(delegate: Collection<E>) : UnmodifiableCollection<E>(delegate), Set<E>

internal class UnmodifiableMapEntry<K, V>(delegate: Map.Entry<K, V>) : Map.Entry<K, V> by delegate

internal class UnmodifiableMapEntries<K, V>(private val delegate: Set<Map.Entry<K, V>>) : 
    UnmodifiableCollection<Map.Entry<K, V>>(delegate), Set<Map.Entry<K, V>> {
    override fun iterator(): Iterator<Map.Entry<K, V>>
}
```

## Usage Patterns

### Generated DSL Example

```kotlin
// Example of how these components work together in generated code

@ProtoDslMarker
class PersonDslBuilder @OnlyForUseByGeneratedProtoCode constructor() {
    private val builder = Person.newBuilder()
    
    var name: String
        get() = builder.name
        set(value) { builder.name = value }
    
    // DSL collections
    val hobbies: DslList<String, PersonDslProxy>
        get() = DslList(builder.hobbiesList)
    
    val scores: DslMap<String, Int, PersonDslProxy>
        get() = DslMap(builder.scoresMap)
    
    @OnlyForUseByGeneratedProtoCode
    fun build(): Person = builder.build()
}

abstract class PersonDslProxy : DslProxy()

// DSL usage
fun person(init: PersonDslBuilder.() -> Unit): Person {
    return PersonDslBuilder().apply(init).build()
}

val myPerson = person {
    name = "John"
    // hobbies and scores are accessible as read-only collections
    println("Current hobbies: ${hobbies.size}")
}
```

## Types

```kotlin { .api }
// Imported from Java protobuf library
import com.google.protobuf.ExtensionLite
import com.google.protobuf.MessageLite
```

## Notes

- All DSL infrastructure components are marked with `@OnlyForUseByGeneratedProtoCode` to prevent misuse
- The `DslProxy` class provides type-level disambiguation for extension methods on generic collection types
- `DslList` and `DslMap` ensure immutability and provide safe iteration even from Java code
- `ExtensionList` combines the functionality of `DslList` with access to the underlying extension definition
- The `@ProtoDslMarker` annotation prevents accidental receiver access in nested DSL scopes
- All collection wrappers delegate to underlying Java collections while ensuring safety guarantees
- These components work together to provide a type-safe, scoped DSL experience for protocol buffer message construction