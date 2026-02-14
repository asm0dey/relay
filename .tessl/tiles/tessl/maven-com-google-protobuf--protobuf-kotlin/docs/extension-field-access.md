# Extension Field Access

Operator overloads for intuitive extension field access on extendable messages and builders, providing get, set, and contains operations with full type safety.

## Capabilities

### Setting Extension Values

Sets the current value of a protocol buffer extension in a message builder.

```kotlin { .api }
/**
 * Sets the current value of the proto extension in this builder
 * @param extension the extension field to set
 * @param value the value to set for the extension
 */
operator fun <
    M : GeneratedMessage.ExtendableMessage<M>,
    B : GeneratedMessage.ExtendableBuilder<M, B>,
    T : Any
> B.set(extension: ExtensionLite<M, T>, value: T)
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.set
// Assuming you have generated message classes with extensions

val builder = MyExtendableMessage.newBuilder()

// Set extension value using bracket notation
builder[myStringExtension] = "Hello, Extensions!"
builder[myIntExtension] = 42
builder[myBoolExtension] = true

val message = builder.build()
```

### Getting Extension Values

Gets the current value of a protocol buffer extension from a message or builder.

```kotlin { .api }
/**
 * Gets the current value of the proto extension
 * @param extension the extension field to get
 * @return the current value of the extension
 */
operator fun <
    M : GeneratedMessage.ExtendableMessage<M>,
    MorBT : GeneratedMessage.ExtendableMessageOrBuilder<M>,
    T : Any
> MorBT.get(extension: ExtensionLite<M, T>): T
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.get

// From a built message
val message: MyExtendableMessage = // ... some message with extensions
val stringValue = message[myStringExtension]
val intValue = message[myIntExtension]
val boolValue = message[myBoolExtension]

// From a builder
val builder = MyExtendableMessage.newBuilder()
builder[myStringExtension] = "test"
val currentValue = builder[myStringExtension] // "test"
```

### Checking Extension Presence

Returns true if the specified extension is set on a message or builder.

```kotlin { .api }
/**
 * Returns true if the specified extension is set on this builder
 * @param extension the extension field to check
 * @return true if the extension is set, false otherwise
 */
operator fun <
    M : GeneratedMessage.ExtendableMessage<M>,
    MorBT : GeneratedMessage.ExtendableMessageOrBuilder<M>
> MorBT.contains(extension: ExtensionLite<M, *>): Boolean
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.contains

val message: MyExtendableMessage = // ... some message
val builder = MyExtendableMessage.newBuilder()

// Check if extension is set
if (myStringExtension in message) {
    println("String extension is set: ${message[myStringExtension]}")
} else {
    println("String extension is not set")
}

// Check before accessing to avoid exceptions
if (myOptionalExtension in builder) {
    val value = builder[myOptionalExtension]
    // Use value safely
}
```

## Complete Usage Patterns

### Working with Extension Fields

```kotlin
import com.google.protobuf.kotlin.*

fun configureMessage(builder: MyExtendableMessage.Builder) {
    // Set various extension types
    builder[userNameExtension] = "john_doe"
    builder[userAgeExtension] = 25
    builder[isActiveExtension] = true
    builder[scoresExtension] = listOf(95, 87, 92)
    
    // Check what's been set
    if (userNameExtension in builder) {
        println("Username: ${builder[userNameExtension]}")
    }
    
    if (userAgeExtension in builder) {
        println("Age: ${builder[userAgeExtension]}")
    }
}

fun processMessage(message: MyExtendableMessage) {
    // Safely access extensions
    val username = if (userNameExtension in message) {
        message[userNameExtension]
    } else {
        "unknown"
    }
    
    val age = if (userAgeExtension in message) {
        message[userAgeExtension]
    } else {
        0
    }
    
    println("Processing user: $username (age: $age)")
}
```

### Extension Field Validation

```kotlin
import com.google.protobuf.kotlin.*

fun validateExtensions(message: MyExtendableMessage): List<String> {
    val errors = mutableListOf<String>()
    
    if (requiredNameExtension !in message) {
        errors.add("Required name extension is missing")
    }
    
    if (emailExtension in message) {
        val email = message[emailExtension]
        if (!email.contains("@")) {
            errors.add("Invalid email format: $email")
        }
    }
    
    if (ageExtension in message) {
        val age = message[ageExtension]
        if (age < 0 || age > 150) {
            errors.add("Invalid age: $age")
        }
    }
    
    return errors
}
```

### Builder Pattern with Extensions

```kotlin
import com.google.protobuf.kotlin.*

class MessageBuilder {
    private val builder = MyExtendableMessage.newBuilder()
    
    fun withName(name: String): MessageBuilder {
        builder[nameExtension] = name
        return this
    }
    
    fun withAge(age: Int): MessageBuilder {
        builder[ageExtension] = age
        return this
    }
    
    fun withEmail(email: String): MessageBuilder {
        builder[emailExtension] = email
        return this
    }
    
    fun build(): MyExtendableMessage {
        // Validation before building
        require(nameExtension in builder) { "Name is required" }
        require(ageExtension in builder) { "Age is required" }
        
        return builder.build()
    }
}

// Usage
val message = MessageBuilder()
    .withName("Alice")
    .withAge(30)
    .withEmail("alice@example.com")
    .build()
```

### Copying Extensions Between Messages

```kotlin
import com.google.protobuf.kotlin.*

fun copyExtensions(
    source: MyExtendableMessage,
    target: MyExtendableMessage.Builder
) {
    // Copy all known extensions
    val extensionsToCheck = listOf(
        nameExtension,
        ageExtension,
        emailExtension,
        isActiveExtension
    )
    
    extensionsToCheck.forEach { extension ->
        if (extension in source) {
            target[extension] = source[extension]
        }
    }
}

// Usage
val originalMessage: MyExtendableMessage = // ... some message
val newBuilder = MyExtendableMessage.newBuilder()

copyExtensions(originalMessage, newBuilder)
val copiedMessage = newBuilder.build()
```

## Types

```kotlin { .api }
// Imported from Java protobuf library
import com.google.protobuf.ExtensionLite
import com.google.protobuf.GeneratedMessage.ExtendableMessage
import com.google.protobuf.GeneratedMessage.ExtendableBuilder
import com.google.protobuf.GeneratedMessage.ExtendableMessageOrBuilder
```

## Notes

- These operator functions provide Kotlin-idiomatic access to protocol buffer extensions
- The `set` operator works only on builders, not on immutable messages
- The `get` and `contains` operators work on both messages and builders
- Extension fields must be defined in your `.proto` files and generated by the protocol buffer compiler
- Type safety is maintained through generic constraints - you cannot assign the wrong type to an extension field
- Always check with `contains` (using `in` operator) before accessing optional extensions to avoid exceptions
- These operators are commonly used in DSL builders and when working with protocol buffer messages that support extensions