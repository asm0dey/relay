# ByteString Extensions

Kotlin idiomatic extensions for ByteString operations including conversion from strings and byte arrays, concatenation, indexing, and utility functions.

## Capabilities

### String to ByteString Conversion

Converts a Kotlin String to a ByteString using UTF-8 encoding.

```kotlin { .api }
/**
 * Encodes this String into a sequence of UTF-8 bytes and returns the result as a ByteString
 * @return ByteString containing UTF-8 encoded bytes of this string
 */
fun String.toByteStringUtf8(): ByteString
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.toByteStringUtf8

val message = "Hello, World!"
val byteString = message.toByteStringUtf8()
// Equivalent to: ByteString.copyFromUtf8("Hello, World!")
```

### ByteString Concatenation

Concatenates two ByteStrings using the plus operator.

```kotlin { .api }
/**
 * Concatenates the given ByteString to this one
 * @param other the ByteString to concatenate
 * @return new ByteString containing both ByteStrings concatenated
 */
operator fun ByteString.plus(other: ByteString): ByteString
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.plus
import com.google.protobuf.kotlin.toByteStringUtf8

val first = "Hello".toByteStringUtf8()
val second = ", World!".toByteStringUtf8()
val combined = first + second
// Result contains "Hello, World!"
```

### ByteString Indexing

Gets the byte at a specific index using array-style indexing.

```kotlin { .api }
/**
 * Gets the byte at the specified index
 * @param index the index of the byte to retrieve
 * @return the byte at the specified index
 * @throws IndexOutOfBoundsException if index is out of bounds
 */
operator fun ByteString.get(index: Int): Byte
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.get
import com.google.protobuf.kotlin.toByteStringUtf8

val byteString = "abc".toByteStringUtf8()
val firstByte = byteString[0]  // 'a'.code (97)
val lastByte = byteString[2]   // 'c'.code (99)
```

### ByteString Non-Empty Check

Checks if a ByteString contains any data.

```kotlin { .api }
/**
 * Checks if this ByteString is not empty
 * @return true if this ByteString contains at least one byte, false if empty
 */
fun ByteString.isNotEmpty(): Boolean
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.isNotEmpty
import com.google.protobuf.kotlin.toByteStringUtf8
import com.google.protobuf.ByteString

val emptyString = ByteString.EMPTY
val nonEmptyString = "hello".toByteStringUtf8()

println(emptyString.isNotEmpty())    // false
println(nonEmptyString.isNotEmpty()) // true
```

### ByteArray to ByteString Conversion

Converts a ByteArray to an immutable ByteString.

```kotlin { .api }
/**
 * Returns a copy of this ByteArray as an immutable ByteString
 * @return ByteString containing a copy of this ByteArray's data
 */
fun ByteArray.toByteString(): ByteString
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.toByteString

val byteArray = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f) // "Hello"
val byteString = byteArray.toByteString()
// Equivalent to: ByteString.copyFrom(byteArray)
```

### ByteBuffer to ByteString Conversion

Copies the remaining bytes from a ByteBuffer to a ByteString.

```kotlin { .api }
/**
 * Copies the remaining bytes from this ByteBuffer to a ByteString
 * @return ByteString containing the remaining bytes from this ByteBuffer
 */
fun ByteBuffer.toByteString(): ByteString
```

**Usage Example:**

```kotlin
import com.google.protobuf.kotlin.toByteString
import java.nio.ByteBuffer

val buffer = ByteBuffer.allocate(10)
buffer.put("Hello".toByteArray())
buffer.flip() // Prepare for reading

val byteString = buffer.toByteString()
// ByteString contains "Hello"
```

## Common Usage Patterns

### Chaining Operations

```kotlin
import com.google.protobuf.kotlin.*

val result = "Hello"
    .toByteStringUtf8()
    .plus(", ".toByteStringUtf8())
    .plus("World!".toByteStringUtf8())

println(result.isNotEmpty()) // true
println(result[0])           // 'H'.code (72)
```

### Working with Binary Data

```kotlin
import com.google.protobuf.kotlin.*

// Create ByteString from binary data
val binaryData = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
val byteString = binaryData.toByteString()

// Inspect the data
if (byteString.isNotEmpty()) {
    val firstByte = byteString[0]
    println("First byte: 0x${firstByte.toUByte().toString(16)}")
}
```