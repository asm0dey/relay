# Any Message Operations

Type-safe operations for protocol buffer Any messages, allowing runtime type checking and unpacking with compile-time type safety through reified generics.

## Capabilities

### Type Checking with isA

Checks if a protocol buffer Any message contains a message of a specific type.

```kotlin { .api }
/**
 * Returns true if this Any contains a message of type T
 * @param T the message type to check for (reified type parameter)
 * @return true if this Any contains a message of type T, false otherwise
 */
inline fun <reified T : Message> ProtoAny.isA(): Boolean
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.isA
import com.google.protobuf.Any as ProtoAny
// Assuming you have generated message types like UserMessage, ProductMessage

val anyMessage: ProtoAny = // ... received from somewhere

// Type-safe checking
if (anyMessage.isA<UserMessage>()) {
    println("This Any contains a UserMessage")
} else if (anyMessage.isA<ProductMessage>()) {
    println("This Any contains a ProductMessage")
} else {
    println("Unknown message type")
}
```

### Message Unpacking with unpack

Extracts and returns the message of a specific type from a protocol buffer Any.

```kotlin { .api }
/**
 * Returns the message of type T encoded in this Any
 * @param T the message type to unpack (reified type parameter)
 * @return the unpacked message of type T
 * @throws InvalidProtocolBufferException if this Any does not contain a T message
 */
inline fun <reified T : Message> ProtoAny.unpack(): T
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.unpack
import com.google.protobuf.kotlin.isA
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.InvalidProtocolBufferException

val anyMessage: ProtoAny = // ... received from somewhere

try {
    // Safe unpacking with type checking
    if (anyMessage.isA<UserMessage>()) {
        val userMessage = anyMessage.unpack<UserMessage>()
        println("User name: ${userMessage.name}")
        println("User email: ${userMessage.email}")
    }
} catch (e: InvalidProtocolBufferException) {
    println("Failed to unpack message: ${e.message}")
}
```

## Complete Usage Pattern

### Safe Any Message Processing

```kotlin
import com.google.protobuf.kotlin.*
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.InvalidProtocolBufferException

fun processAnyMessage(anyMessage: ProtoAny) {
    when {
        anyMessage.isA<UserMessage>() -> {
            val user = anyMessage.unpack<UserMessage>()
            handleUser(user)
        }
        anyMessage.isA<ProductMessage>() -> {
            val product = anyMessage.unpack<ProductMessage>()
            handleProduct(product)
        }
        anyMessage.isA<OrderMessage>() -> {
            val order = anyMessage.unpack<OrderMessage>()
            handleOrder(order)
        }
        else -> {
            println("Unknown message type: ${anyMessage.typeUrl}")
        }
    }
}

fun handleUser(user: UserMessage) {
    println("Processing user: ${user.name}")
}

fun handleProduct(product: ProductMessage) {
    println("Processing product: ${product.name}")
}

fun handleOrder(order: OrderMessage) {
    println("Processing order: ${order.id}")
}
```

### Error Handling

```kotlin
import com.google.protobuf.kotlin.*
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.InvalidProtocolBufferException

fun safeUnpack(anyMessage: ProtoAny): UserMessage? {
    return try {
        if (anyMessage.isA<UserMessage>()) {
            anyMessage.unpack<UserMessage>()
        } else {
            null
        }
    } catch (e: InvalidProtocolBufferException) {
        println("Error unpacking UserMessage: ${e.message}")
        null
    }
}

// Usage
val anyMessage: ProtoAny = // ... some Any message
val userMessage = safeUnpack(anyMessage)
userMessage?.let { user ->
    println("Successfully unpacked user: ${user.name}")
}
```

### Working with Collections of Any Messages

```kotlin
import com.google.protobuf.kotlin.*
import com.google.protobuf.Any as ProtoAny

fun processAnyMessages(messages: List<ProtoAny>) {
    val users = messages
        .filter { it.isA<UserMessage>() }
        .map { it.unpack<UserMessage>() }
    
    val products = messages
        .filter { it.isA<ProductMessage>() }
        .map { it.unpack<ProductMessage>() }
    
    println("Found ${users.size} users and ${products.size} products")
    
    users.forEach { user -> 
        println("User: ${user.name}")
    }
    
    products.forEach { product -> 
        println("Product: ${product.name}")
    }
}
```

## Types

```kotlin { .api }
// Imported from Java protobuf library
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.Message
import com.google.protobuf.InvalidProtocolBufferException
```

## Notes

- Both `isA` and `unpack` use reified type parameters, allowing for type-safe operations at runtime
- These functions work with any protocol buffer message type that extends `Message`
- Always check with `isA` before calling `unpack` to avoid `InvalidProtocolBufferException`
- The `ProtoAny` type is aliased from `com.google.protobuf.Any` to avoid confusion with Kotlin's built-in `Any` type
- These operations are commonly used in systems that need to handle multiple message types in a type-safe manner