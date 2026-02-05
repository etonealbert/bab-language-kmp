# Bring a Brain - Coding Standards

> Quick reference for AI assistants writing code in this project.

## Language: Kotlin Multiplatform

### Kotlin Version
- Kotlin 2.0.0
- Target JVM 11 for Android

### Code Style

```kotlin
// 1. Use data classes for immutable models
data class DialogLine(
    val id: String,
    val speakerId: String,
    val textNative: String,
    val textTranslated: String,
    val timestamp: VectorClock
)

// 2. Use sealed classes for sum types
sealed class Intent {
    data class StartSoloGame(val scenarioId: String, val roleId: String) : Intent()
    data class AddDialog(val line: DialogLine) : Intent()
    object LeaveGame : Intent()
}

// 3. Use interfaces for dependencies
interface AIProvider {
    suspend fun generate(prompt: String, context: List<DialogLine>): DialogLine
    fun isAvailable(): Boolean
}

// 4. Use expect/actual for platform code
// commonMain
expect class BleScanner() {
    fun scan(): Flow<DiscoveredDevice>
}

// androidMain
actual class BleScanner {
    actual fun scan(): Flow<DiscoveredDevice> = flow { /* Android impl */ }
}

// iosMain
actual class BleScanner {
    actual fun scan(): Flow<DiscoveredDevice> = flow { /* iOS impl */ }
}
```

### Naming Conventions

| Element | Style | Example |
|---------|-------|---------|
| Classes | PascalCase | `BleHostSession` |
| Interfaces | PascalCase | `NetworkSession` |
| Functions | camelCase | `startSoloGame()` |
| Properties | camelCase | `dialogHistory` |
| Constants | SCREAMING_SNAKE | `SERVICE_UUID` |
| Packages | lowercase | `com.bablabs.bringabrainlanguage` |
| Files | PascalCase.kt | `DialogStore.kt` |
| Test files | PascalCase**Test**.kt | `VectorClockTest.kt` |

### Import Order

```kotlin
// 1. Kotlin stdlib
import kotlin.coroutines.*

// 2. kotlinx libraries
import kotlinx.coroutines.*
import kotlinx.serialization.*

// 3. Third-party
import com.juul.kable.*

// 4. Project imports
import com.bablabs.bringabrainlanguage.domain.models.*
```

## Testing Standards

### Test File Location
- `commonTest/kotlin/com/bablabs/bringabrainlanguage/`
- Mirror the main source structure

### Test Naming
Use backtick-quoted descriptive names:

```kotlin
class VectorClockTest {
    @Test
    fun `increment increases own counter`() {
        // ...
    }
    
    @Test
    fun `merge takes maximum of each counter`() {
        // ...
    }
    
    @Test
    fun `happenedBefore returns true for causal ordering`() {
        // ...
    }
}
```

### Test Structure (AAA Pattern)

```kotlin
@Test
fun `reducer adds dialog line to history`() {
    // Arrange
    val initialState = SessionState(dialogHistory = emptyList())
    val newLine = DialogLine(id = "1", text = "Hello")
    val packet = Packet.DialogAdded(newLine)
    
    // Act
    val newState = reducer(initialState, packet)
    
    // Assert
    assertEquals(1, newState.dialogHistory.size)
    assertEquals(newLine, newState.dialogHistory.first())
}
```

### What to Test

| Component | Test Focus |
|-----------|------------|
| Models | Construction, equality, copy |
| VectorClock | Increment, merge, comparison |
| Reducer | State transitions for each Packet type |
| Executor | Intent → Packet mapping |
| Fragmenter | Fragment/reassemble round-trip |

## Architecture Patterns

### MVI (Model-View-Intent)

```
Intent → Executor → Packet → NetworkSession → Reducer → State
```

- **Intent**: User action (sealed class)
- **Executor**: Converts Intent to Packet(s)
- **Packet**: Network message (sealed class)
- **Reducer**: Pure function (State, Packet) → State
- **State**: Immutable data class

### Key Principle: Loopback

ALL state changes go through NetworkSession, even in solo mode:

```kotlin
// Solo: LoopbackNetworkSession immediately returns packets
// Multiplayer: BleSession sends over network

// Both use same Reducer path!
networkSession.incomingPackets.collect { packet ->
    state = reducer(state, packet)
}
```

### Dependency Injection

Use constructor injection:

```kotlin
class DialogStore(
    private val networkSession: NetworkSession,
    private val aiProvider: AIProvider,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
)
```

## Serialization

Use kotlinx.serialization:

```kotlin
@Serializable
data class DialogLine(
    val id: String,
    val speakerId: String,
    val textNative: String,
    val textTranslated: String,
    val timestampMs: Long
)

@Serializable
sealed class Packet {
    @Serializable
    @SerialName("dialog_added")
    data class DialogAdded(val line: DialogLine) : Packet()
    
    @Serializable
    @SerialName("state_sync")
    data class StateSync(val state: SessionState) : Packet()
}
```

## Coroutines

### Flow Usage

```kotlin
// Cold flow for scanning
fun scanForHosts(): Flow<DiscoveredDevice> = flow {
    // Emit discovered devices
}

// StateFlow for state
private val _state = MutableStateFlow(SessionState())
val state: StateFlow<SessionState> = _state.asStateFlow()

// Collecting
scope.launch {
    networkSession.incomingPackets.collect { packet ->
        _state.update { reducer(it, packet) }
    }
}
```

### Dispatcher Usage

| Context | Dispatcher |
|---------|------------|
| Default/CPU | `Dispatchers.Default` |
| IO (network, file) | `Dispatchers.IO` (Android) |
| Main thread | `Dispatchers.Main` |
| Tests | `StandardTestDispatcher()` |

## Error Handling

```kotlin
// Use sealed class for errors
sealed class GameError {
    data class NetworkError(val message: String) : GameError()
    data class AIError(val message: String) : GameError()
    object NotConnected : GameError()
}

// Include in state
data class SessionState(
    // ...
    val error: GameError? = null
)

// Handle in Reducer
fun reducer(state: SessionState, packet: Packet): SessionState = when (packet) {
    is Packet.Error -> state.copy(error = packet.error)
    // ...
}
```

## Documentation

### KDoc for Public API

```kotlin
/**
 * Starts a solo game with an AI partner.
 *
 * @param scenarioId The scenario to play (e.g., "coffee-shop")
 * @param userRoleId The role the user will play (e.g., "customer")
 * @throws IllegalStateException if already in a game
 */
fun startSoloGame(scenarioId: String, userRoleId: String)
```

## Forbidden Patterns

```kotlin
// ❌ NEVER: Mutable state outside StateFlow
var currentState = SessionState() // BAD

// ✅ ALWAYS: Immutable with StateFlow
private val _state = MutableStateFlow(SessionState())

// ❌ NEVER: Direct state mutation
state.dialogHistory.add(newLine) // BAD

// ✅ ALWAYS: Copy on change
state.copy(dialogHistory = state.dialogHistory + newLine)

// ❌ NEVER: Unchecked casts
val session = networkSession as BleHostSession // BAD

// ✅ ALWAYS: Safe casts or sealed class
when (networkSession) {
    is BleHostSession -> // handle host
    is BleClientSession -> // handle client
}

// ❌ NEVER: Hardcoded platform code in commonMain
if (Platform.isIOS) { /* iOS code */ } // BAD

// ✅ ALWAYS: expect/actual
expect fun getPlatformName(): String
```

## File Templates

### New Model

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class NewModel(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### New Interface

```kotlin
package com.bablabs.bringabrainlanguage.domain.interfaces

interface NewInterface {
    suspend fun doSomething(): Result
    fun isAvailable(): Boolean
}
```

### New Test

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NewModelTest {
    @Test
    fun `should do something`() {
        // Arrange
        val model = NewModel(id = "1", name = "Test")
        
        // Act
        val result = model.someMethod()
        
        // Assert
        assertEquals(expected, result)
    }
}
```
