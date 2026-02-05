# Phase 1: Walking Skeleton Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a working KMP SDK with mocked AI and loopback networking, proving the unified MVI architecture works end-to-end.

**Architecture:** Headless MVI where all state mutations flow through `NetworkSession.incomingPackets` → Reducer, even in Solo mode. Native UI (SwiftUI/Compose) observes `StateFlow<SessionState>` and dispatches `Intent` objects.

**Tech Stack:** Kotlin 2.3, MVIKotlin, Decompose, kotlinx.serialization, Koin DI, kotlin-test

---

## Prerequisites

Before starting, ensure:
- Android Studio Koala or newer
- JDK 17 (NOT 25)
- Project builds: `./gradlew build`

---

## Task 1: Domain Models - Core Data Classes

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/VectorClock.kt`
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/DialogLine.kt`
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/Participant.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/VectorClockTest.kt`

### Step 1: Write the failing test for VectorClock

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/VectorClockTest.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class VectorClockTest {
    
    @Test
    fun `increment increases peer timestamp by 1`() {
        val clock = VectorClock()
        val incremented = clock.increment("peer-a")
        
        assertEquals(1L, incremented.timestamps["peer-a"])
    }
    
    @Test
    fun `increment preserves other peer timestamps`() {
        val clock = VectorClock(mapOf("peer-a" to 5L, "peer-b" to 3L))
        val incremented = clock.increment("peer-a")
        
        assertEquals(6L, incremented.timestamps["peer-a"])
        assertEquals(3L, incremented.timestamps["peer-b"])
    }
    
    @Test
    fun `merge takes max of each peer timestamp`() {
        val clock1 = VectorClock(mapOf("peer-a" to 5L, "peer-b" to 3L))
        val clock2 = VectorClock(mapOf("peer-a" to 2L, "peer-b" to 7L, "peer-c" to 1L))
        
        val merged = clock1.merge(clock2)
        
        assertEquals(5L, merged.timestamps["peer-a"])
        assertEquals(7L, merged.timestamps["peer-b"])
        assertEquals(1L, merged.timestamps["peer-c"])
    }
    
    @Test
    fun `compare detects BEFORE relationship`() {
        val older = VectorClock(mapOf("peer-a" to 1L))
        val newer = VectorClock(mapOf("peer-a" to 2L))
        
        assertEquals(VectorClock.Comparison.BEFORE, older.compare(newer))
    }
    
    @Test
    fun `compare detects AFTER relationship`() {
        val newer = VectorClock(mapOf("peer-a" to 2L))
        val older = VectorClock(mapOf("peer-a" to 1L))
        
        assertEquals(VectorClock.Comparison.AFTER, newer.compare(older))
    }
    
    @Test
    fun `compare detects CONCURRENT relationship`() {
        val clock1 = VectorClock(mapOf("peer-a" to 2L, "peer-b" to 1L))
        val clock2 = VectorClock(mapOf("peer-a" to 1L, "peer-b" to 2L))
        
        assertEquals(VectorClock.Comparison.CONCURRENT, clock1.compare(clock2))
    }
    
    @Test
    fun `compare detects EQUAL relationship`() {
        val clock1 = VectorClock(mapOf("peer-a" to 2L))
        val clock2 = VectorClock(mapOf("peer-a" to 2L))
        
        assertEquals(VectorClock.Comparison.EQUAL, clock1.compare(clock2))
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew :composeApp:allTests --tests "*.VectorClockTest"`
Expected: FAIL with "Unresolved reference: VectorClock"

### Step 3: Create domain models directory structure

```bash
mkdir -p composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models
mkdir -p composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models
```

### Step 4: Implement VectorClock

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/VectorClock.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class VectorClock(val timestamps: Map<String, Long> = emptyMap()) {
    
    fun increment(peerId: String): VectorClock {
        val next = (timestamps[peerId] ?: 0L) + 1
        return VectorClock(timestamps + (peerId to next))
    }

    fun merge(other: VectorClock): VectorClock {
        val allKeys = timestamps.keys + other.timestamps.keys
        val merged = allKeys.associateWith { key ->
            maxOf(timestamps[key] ?: 0L, other.timestamps[key] ?: 0L)
        }
        return VectorClock(merged)
    }

    fun compare(other: VectorClock): Comparison {
        val keys = timestamps.keys + other.timestamps.keys
        var hasGreater = false
        var hasSmaller = false
        for (key in keys) {
            val local = timestamps[key] ?: 0L
            val remote = other.timestamps[key] ?: 0L
            if (local > remote) hasGreater = true
            if (local < remote) hasSmaller = true
        }
        return when {
            hasGreater && hasSmaller -> Comparison.CONCURRENT
            hasGreater -> Comparison.AFTER
            hasSmaller -> Comparison.BEFORE
            else -> Comparison.EQUAL
        }
    }

    enum class Comparison { BEFORE, AFTER, CONCURRENT, EQUAL }
}
```

### Step 5: Run test to verify it passes

Run: `./gradlew :composeApp:allTests --tests "*.VectorClockTest"`
Expected: PASS (7 tests)

### Step 6: Implement DialogLine and Participant

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/DialogLine.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class DialogLine(
    val id: String,
    val speakerId: String,
    val roleName: String,
    val textNative: String,
    val textTranslated: String,
    val timestamp: Long
)
```

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/Participant.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val role: Role? = null,
    val isHost: Boolean = false
)

@Serializable
data class Role(
    val id: String,
    val name: String,
    val description: String
)
```

### Step 7: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/
git commit -m "feat(domain): add VectorClock, DialogLine, Participant models

- VectorClock with increment, merge, compare operations
- DialogLine for dialog history entries
- Participant with optional Role assignment
- Full test coverage for VectorClock"
```

---

## Task 2: Domain Models - Packet Structure

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/Packet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/SessionState.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/PacketSerializationTest.kt`

### Step 1: Write the failing test for Packet serialization

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/PacketSerializationTest.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketSerializationTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun `DialogLineAdded packet serializes and deserializes correctly`() {
        val line = DialogLine(
            id = "line-1",
            speakerId = "peer-a",
            roleName = "The Barista",
            textNative = "Hola, que deseas?",
            textTranslated = "Hello, what would you like?",
            timestamp = 1234567890L
        )
        
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(mapOf("peer-a" to 1L)),
            payload = PacketPayload.DialogLineAdded(line)
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun `GenerateRequest packet serializes correctly`() {
        val packet = Packet(
            type = PacketType.GENERATE_REQUEST,
            senderId = "peer-b",
            vectorClock = VectorClock(mapOf("peer-b" to 5L)),
            payload = PacketPayload.GenerateRequest(prompt = "Generate next dialog line")
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
    
    @Test
    fun `Heartbeat packet has minimal payload`() {
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.Heartbeat
        )
        
        val serialized = json.encodeToString(packet)
        val deserialized = json.decodeFromString<Packet>(serialized)
        
        assertEquals(packet, deserialized)
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew :composeApp:allTests --tests "*.PacketSerializationTest"`
Expected: FAIL with "Unresolved reference: Packet"

### Step 3: Implement Packet and PacketPayload

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/Packet.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Packet(
    val type: PacketType,
    val senderId: String,
    val vectorClock: VectorClock,
    val payload: PacketPayload
)

@Serializable
enum class PacketType {
    HANDSHAKE,
    HEARTBEAT,
    GENERATE_REQUEST,
    DIALOG_LINE_ADDED,
    VOTE_REQUEST,
    VOTE_CAST,
    FULL_STATE_SNAPSHOT,
    NAVIGATION_SYNC
}

@Serializable
sealed class PacketPayload {
    
    // Game Actions
    @Serializable
    data class GenerateRequest(val prompt: String) : PacketPayload()
    
    @Serializable
    data class DialogLineAdded(val line: DialogLine) : PacketPayload()
    
    // Voting
    @Serializable
    data class VoteRequest(
        val proposerId: String,
        val action: VoteAction
    ) : PacketPayload()
    
    @Serializable
    data class VoteCast(
        val voterId: String,
        val vote: Boolean
    ) : PacketPayload()
    
    // State Sync
    @Serializable
    data class FullStateSnapshot(val state: SessionState) : PacketPayload()
    
    @Serializable
    data class NavigationSync(val screenName: String) : PacketPayload()
    
    // Control
    @Serializable
    data object Heartbeat : PacketPayload()
    
    @Serializable
    data class Handshake(val participant: Participant) : PacketPayload()
}

@Serializable
enum class VoteAction {
    CHANGE_SCENE,
    END_GAME,
    SKIP_LINE
}
```

### Step 4: Implement SessionState

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/SessionState.kt
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionState(
    // Connection
    val mode: SessionMode = SessionMode.SOLO,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val peers: List<Participant> = emptyList(),
    val localPeerId: String = "",
    
    // Game State
    val scenario: Scenario? = null,
    val roles: Map<String, Role> = emptyMap(),
    val dialogHistory: List<DialogLine> = emptyList(),
    val currentPhase: GamePhase = GamePhase.LOBBY,
    
    // Voting
    val pendingVote: PendingVote? = null,
    val voteResults: Map<String, Boolean> = emptyMap(),
    
    // Sync Metadata
    val vectorClock: VectorClock = VectorClock(),
    val lastSyncTimestamp: Long = 0L
)

@Serializable
enum class SessionMode { SOLO, HOST, CLIENT }

@Serializable
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

@Serializable
enum class GamePhase { LOBBY, ROLE_SELECTION, ACTIVE, VOTING, FINISHED }

@Serializable
data class Scenario(
    val id: String,
    val name: String,
    val description: String,
    val availableRoles: List<Role>
)

@Serializable
data class PendingVote(
    val proposerId: String,
    val action: VoteAction,
    val requiredVotes: Int
)
```

### Step 5: Run test to verify it passes

Run: `./gradlew :composeApp:allTests --tests "*.PacketSerializationTest"`
Expected: PASS (3 tests)

### Step 6: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/Packet.kt
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/models/SessionState.kt
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/models/PacketSerializationTest.kt
git commit -m "feat(domain): add Packet structure and SessionState

- Packet with VectorClock and sealed PacketPayload
- SessionState as single source of truth
- Full serialization support for BLE/WS transport
- Test coverage for serialization roundtrips"
```

---

## Task 3: NetworkSession Interface and Loopback Implementation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/NetworkSession.kt`
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/LoopbackNetworkSession.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/LoopbackNetworkSessionTest.kt`

### Step 1: Write the failing test for LoopbackNetworkSession

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/LoopbackNetworkSessionTest.kt
package com.bablabs.bringabrainlanguage.infrastructure.network

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackNetworkSessionTest {
    
    @Test
    fun `loopback session starts in connected state`() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        assertEquals(ConnectionState.Connected::class, session.state.value::class)
    }
    
    @Test
    fun `sent packet is echoed back with incremented vector clock`() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        val outgoingPacket = Packet(
            type = PacketType.GENERATE_REQUEST,
            senderId = "local",
            vectorClock = VectorClock(),
            payload = PacketPayload.GenerateRequest("test prompt")
        )
        
        // Send the packet
        session.send(outgoingPacket)
        
        // Receive the echoed packet
        val receivedPacket = session.incomingPackets.first()
        
        // Verify vector clock was incremented
        assertEquals(1L, receivedPacket.vectorClock.timestamps["local"])
        assertEquals(outgoingPacket.type, receivedPacket.type)
        assertEquals(outgoingPacket.payload, receivedPacket.payload)
    }
    
    @Test
    fun `vector clock increments cumulatively`() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "local",
            vectorClock = VectorClock(),
            payload = PacketPayload.Heartbeat
        )
        
        // Send multiple packets
        session.send(packet)
        session.send(packet)
        session.send(packet)
        
        // Receive all
        val received1 = session.incomingPackets.first()
        val received2 = session.incomingPackets.first()
        val received3 = session.incomingPackets.first()
        
        assertEquals(1L, received1.vectorClock.timestamps["local"])
        assertEquals(2L, received2.vectorClock.timestamps["local"])
        assertEquals(3L, received3.vectorClock.timestamps["local"])
    }
    
    @Test
    fun `disconnect changes state to disconnected`() = runTest {
        val session = LoopbackNetworkSession(localPeerId = "local")
        
        session.disconnect()
        
        assertEquals(ConnectionState.Disconnected, session.state.value)
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew :composeApp:allTests --tests "*.LoopbackNetworkSessionTest"`
Expected: FAIL with "Unresolved reference: NetworkSession"

### Step 3: Create directory structure

```bash
mkdir -p composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces
mkdir -p composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network
mkdir -p composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network
```

### Step 4: Implement NetworkSession interface

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/NetworkSession.kt
package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.Participant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NetworkSession {
    val localPeerId: String
    val state: StateFlow<ConnectionState>
    
    /**
     * ALL state changes flow through here (including loopback).
     * The Reducer ONLY applies state from this flow.
     */
    val incomingPackets: Flow<Packet>
    
    /**
     * ALL intents go out through here (even in Solo mode).
     * @param packet The packet to send
     * @param recipientId Optional target (null for broadcast)
     */
    suspend fun send(packet: Packet, recipientId: String? = null)
    
    /**
     * Gracefully terminate the session.
     */
    suspend fun disconnect()
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val peers: List<Participant> = emptyList()) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
}
```

### Step 5: Implement LoopbackNetworkSession

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/LoopbackNetworkSession.kt
package com.bablabs.bringabrainlanguage.infrastructure.network

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.Packet
import com.bablabs.bringabrainlanguage.domain.models.VectorClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * LoopbackNetworkSession echoes all sent packets back immediately.
 * Used for Solo Mode where the "network" is just the local device.
 * 
 * This ensures the same MVI flow (Intent → Packet → Reducer) works
 * identically for Solo and Multiplayer modes.
 */
class LoopbackNetworkSession(
    override val localPeerId: String
) : NetworkSession {
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connected())
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    // Internal vector clock that increments with each packet
    private var currentClock = VectorClock()
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        // Increment the vector clock for this peer
        currentClock = currentClock.increment(localPeerId)
        
        // Echo the packet back with updated vector clock
        val echoedPacket = packet.copy(
            vectorClock = currentClock
        )
        
        packetChannel.send(echoedPacket)
    }
    
    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
        packetChannel.close()
    }
}
```

### Step 6: Run test to verify it passes

Run: `./gradlew :composeApp:allTests --tests "*.LoopbackNetworkSessionTest"`
Expected: PASS (4 tests)

### Step 7: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/
git commit -m "feat(network): add NetworkSession interface and LoopbackNetworkSession

- NetworkSession defines unified packet flow interface
- LoopbackNetworkSession echoes packets for Solo mode
- Vector clock incremented on each packet send
- Enables identical MVI flow for Solo and Multiplayer"
```

---

## Task 4: AIProvider Interface and MockAIProvider

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/AIProvider.kt`
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/MockAIProvider.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/MockAIProviderTest.kt`

### Step 1: Write the failing test for MockAIProvider

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/MockAIProviderTest.kt
package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockAIProviderTest {
    
    @Test
    fun `generate returns a DialogLine with native and translated text`() = runTest {
        val provider = MockAIProvider()
        
        val context = DialogContext(
            scenario = "Ordering Coffee",
            userRole = "Customer",
            aiRole = "Barista",
            previousLines = emptyList()
        )
        
        val result = provider.generate(context)
        
        assertNotNull(result)
        assertTrue(result.textNative.isNotBlank())
        assertTrue(result.textTranslated.isNotBlank())
        assertEquals("Barista", result.roleName)
    }
    
    @Test
    fun `generate returns different lines for different contexts`() = runTest {
        val provider = MockAIProvider()
        
        val context1 = DialogContext(
            scenario = "Ordering Coffee",
            userRole = "Customer",
            aiRole = "Barista",
            previousLines = emptyList()
        )
        
        val context2 = DialogContext(
            scenario = "The Heist",
            userRole = "Detective",
            aiRole = "Thief",
            previousLines = emptyList()
        )
        
        val result1 = provider.generate(context1)
        val result2 = provider.generate(context2)
        
        // Different scenarios should produce different role names at minimum
        assertEquals("Barista", result1.roleName)
        assertEquals("Thief", result2.roleName)
    }
    
    @Test
    fun `generate includes speakerId from AI role`() = runTest {
        val provider = MockAIProvider()
        
        val context = DialogContext(
            scenario = "Test",
            userRole = "User",
            aiRole = "Robot",
            previousLines = emptyList()
        )
        
        val result = provider.generate(context)
        
        assertEquals("ai-robot", result.speakerId)
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew :composeApp:allTests --tests "*.MockAIProviderTest"`
Expected: FAIL with "Unresolved reference: AIProvider"

### Step 3: Create directory structure

```bash
mkdir -p composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai
mkdir -p composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai
```

### Step 4: Implement AIProvider interface

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/AIProvider.kt
package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import kotlinx.coroutines.flow.Flow

/**
 * Interface for AI-powered dialog generation.
 * Implementations may use:
 * - MockAIProvider (for testing/Phase 1)
 * - NativeLLM (iOS 26 Foundation Models)
 * - ServerLLM (Rust backend API)
 */
interface AIProvider {
    /**
     * Generate a single dialog line based on context.
     */
    suspend fun generate(context: DialogContext): DialogLine
    
    /**
     * Stream dialog generation token-by-token.
     * Used for real-time text display.
     */
    fun streamGenerate(context: DialogContext): Flow<String>
}

/**
 * Context provided to the AI for generation.
 */
data class DialogContext(
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val previousLines: List<DialogLine>,
    val targetLanguage: String = "Spanish",
    val nativeLanguage: String = "English"
)
```

### Step 5: Implement MockAIProvider

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/MockAIProvider.kt
package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Mock implementation of AIProvider for Phase 1 development.
 * Returns pre-defined dialog lines based on scenario keywords.
 */
class MockAIProvider : AIProvider {
    
    private val mockDialogs = mapOf(
        "coffee" to listOf(
            MockLine("Hola, bienvenido. Que deseas tomar?", "Hello, welcome. What would you like to drink?"),
            MockLine("Tenemos cafe, te, y chocolate caliente.", "We have coffee, tea, and hot chocolate."),
            MockLine("Muy bien. Seran tres euros.", "Very well. That will be three euros.")
        ),
        "heist" to listOf(
            MockLine("Alto ahi! Policia!", "Stop right there! Police!"),
            MockLine("No te muevas. Tienes derecho a guardar silencio.", "Don't move. You have the right to remain silent."),
            MockLine("Donde esta el dinero?", "Where is the money?")
        ),
        "default" to listOf(
            MockLine("Hola, como estas?", "Hello, how are you?"),
            MockLine("Muy bien, gracias. Y tu?", "Very well, thanks. And you?"),
            MockLine("Hasta luego!", "See you later!")
        )
    )
    
    private data class MockLine(val native: String, val translated: String)
    
    override suspend fun generate(context: DialogContext): DialogLine {
        // Simulate network/AI delay
        delay(Random.nextLong(200, 500))
        
        val scenarioKey = context.scenario.lowercase()
        val lines = mockDialogs.entries
            .firstOrNull { scenarioKey.contains(it.key) }
            ?.value ?: mockDialogs["default"]!!
        
        val selectedLine = lines[context.previousLines.size % lines.size]
        
        return DialogLine(
            id = "line-${System.currentTimeMillis()}",
            speakerId = "ai-${context.aiRole.lowercase().replace(" ", "-")}",
            roleName = context.aiRole,
            textNative = selectedLine.native,
            textTranslated = selectedLine.translated,
            timestamp = System.currentTimeMillis()
        )
    }
    
    override fun streamGenerate(context: DialogContext): Flow<String> = flow {
        // For mock, just emit the full line word by word
        delay(100)
        
        val lines = mockDialogs["default"]!!
        val selectedLine = lines[0]
        
        selectedLine.native.split(" ").forEach { word ->
            emit("$word ")
            delay(50)
        }
    }
    
    // Note: System.currentTimeMillis() won't work in common code
    // We need a platform-agnostic solution
    private fun System.currentTimeMillis(): Long = 
        kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
```

### Step 6: Add kotlinx-datetime dependency

Update `composeApp/build.gradle.kts` to include:
```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}
```

### Step 7: Run test to verify it passes

Run: `./gradlew :composeApp:allTests --tests "*.MockAIProviderTest"`
Expected: PASS (3 tests)

### Step 8: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/interfaces/AIProvider.kt
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/ai/
git commit -m "feat(ai): add AIProvider interface and MockAIProvider

- AIProvider defines generate and streamGenerate methods
- DialogContext provides scenario/role information
- MockAIProvider returns predefined dialogs for testing
- Simulates realistic delays for UX development"
```

---

## Task 5: DialogStore (MVI Store)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStore.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStoreTest.kt`

### Step 1: Write the failing test for DialogStore

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStoreTest.kt
package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogStoreTest {
    
    @Test
    fun `initial state has empty dialog history`() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        val state = store.state.first()
        
        assertTrue(state.dialogHistory.isEmpty())
        assertEquals(SessionMode.SOLO, state.mode)
    }
    
    @Test
    fun `Generate intent produces DialogLineAdded in state`() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario(
                id = "coffee",
                name = "Ordering Coffee",
                description = "Practice ordering at a cafe",
                availableRoles = listOf(Role("barista", "Barista", "The coffee shop worker"))
            ),
            userRole = Role("customer", "Customer", "The person ordering")
        ))
        
        store.accept(DialogStore.Intent.Generate)
        
        // Wait for the packet to loop back and state to update
        val state = store.state.first { it.dialogHistory.isNotEmpty() }
        
        assertEquals(1, state.dialogHistory.size)
        assertEquals("Barista", state.dialogHistory.first().roleName)
    }
    
    @Test
    fun `state updates only happen via network packets`() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        // Verify that the vector clock is updated (proving packet went through network)
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test", "Test", "Test scenario", emptyList()),
            userRole = Role("user", "User", "Test user")
        ))
        
        store.accept(DialogStore.Intent.Generate)
        
        val state = store.state.first { it.dialogHistory.isNotEmpty() }
        
        // Vector clock should be incremented, proving loopback path was used
        assertTrue(state.vectorClock.timestamps.isNotEmpty())
    }
}
```

### Step 2: Run test to verify it fails

Run: `./gradlew :composeApp:allTests --tests "*.DialogStoreTest"`
Expected: FAIL with "Unresolved reference: DialogStore"

### Step 3: Create directory structure

```bash
mkdir -p composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores
mkdir -p composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores
```

### Step 4: Implement DialogStore

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStore.kt
package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DialogStore manages the core game state following MVI pattern.
 * 
 * Key principle: ALL state mutations happen via packets received from NetworkSession.
 * Even in Solo mode, intents are sent through LoopbackNetworkSession and
 * state is only updated when the packet comes back.
 */
class DialogStore(
    private val networkSession: NetworkSession,
    private val aiProvider: AIProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _state = MutableStateFlow(SessionState(localPeerId = networkSession.localPeerId))
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    init {
        // Subscribe to incoming packets - this is the ONLY place state changes
        scope.launch {
            networkSession.incomingPackets.collect { packet ->
                reduce(packet)
            }
        }
    }
    
    /**
     * Accept user intents. These are processed by the Executor
     * which may call AI or send packets.
     */
    fun accept(intent: Intent) {
        scope.launch {
            execute(intent)
        }
    }
    
    // ==================== EXECUTOR ====================
    // Processes intents and produces packets
    
    private suspend fun execute(intent: Intent) {
        when (intent) {
            is Intent.StartSoloGame -> {
                val packet = Packet(
                    type = PacketType.FULL_STATE_SNAPSHOT,
                    senderId = networkSession.localPeerId,
                    vectorClock = _state.value.vectorClock,
                    payload = PacketPayload.FullStateSnapshot(
                        _state.value.copy(
                            mode = SessionMode.SOLO,
                            connectionStatus = ConnectionStatus.CONNECTED,
                            scenario = intent.scenario,
                            roles = mapOf(
                                networkSession.localPeerId to intent.userRole,
                                "ai-robot" to Role("robot", "Robot", "AI partner")
                            ),
                            currentPhase = GamePhase.ACTIVE
                        )
                    )
                )
                networkSession.send(packet)
            }
            
            is Intent.Generate -> {
                val currentState = _state.value
                val scenario = currentState.scenario ?: return
                
                // Build context for AI
                val context = DialogContext(
                    scenario = scenario.name,
                    userRole = currentState.roles[networkSession.localPeerId]?.name ?: "User",
                    aiRole = currentState.roles["ai-robot"]?.name ?: "Robot",
                    previousLines = currentState.dialogHistory
                )
                
                // Call AI provider
                val dialogLine = aiProvider.generate(context)
                
                // Wrap response in packet and send through network
                val packet = Packet(
                    type = PacketType.DIALOG_LINE_ADDED,
                    senderId = networkSession.localPeerId,
                    vectorClock = currentState.vectorClock,
                    payload = PacketPayload.DialogLineAdded(dialogLine)
                )
                networkSession.send(packet)
            }
            
            is Intent.LeaveGame -> {
                networkSession.disconnect()
            }
        }
    }
    
    // ==================== REDUCER ====================
    // The ONLY place state changes. Applies incoming packets.
    
    private fun reduce(packet: Packet) {
        val currentState = _state.value
        
        // Always merge vector clocks first
        val mergedClock = currentState.vectorClock.merge(packet.vectorClock)
        
        val newState = when (val payload = packet.payload) {
            is PacketPayload.DialogLineAdded -> currentState.copy(
                dialogHistory = currentState.dialogHistory + payload.line,
                vectorClock = mergedClock
            )
            
            is PacketPayload.FullStateSnapshot -> payload.state.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.VoteRequest -> currentState.copy(
                pendingVote = PendingVote(
                    proposerId = payload.proposerId,
                    action = payload.action,
                    requiredVotes = currentState.peers.size
                ),
                currentPhase = GamePhase.VOTING,
                vectorClock = mergedClock
            )
            
            is PacketPayload.VoteCast -> currentState.copy(
                voteResults = currentState.voteResults + (payload.voterId to payload.vote),
                vectorClock = mergedClock
            )
            
            is PacketPayload.NavigationSync -> currentState.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.Handshake -> currentState.copy(
                peers = currentState.peers + payload.participant,
                vectorClock = mergedClock
            )
            
            is PacketPayload.Heartbeat -> currentState.copy(
                vectorClock = mergedClock
            )
            
            is PacketPayload.GenerateRequest -> {
                // Host would process this; clients just acknowledge
                currentState.copy(vectorClock = mergedClock)
            }
        }
        
        _state.value = newState
    }
    
    // ==================== INTENTS ====================
    
    sealed class Intent {
        data class StartSoloGame(
            val scenario: Scenario,
            val userRole: Role
        ) : Intent()
        
        data object Generate : Intent()
        
        data object LeaveGame : Intent()
    }
}
```

### Step 5: Run test to verify it passes

Run: `./gradlew :composeApp:allTests --tests "*.DialogStoreTest"`
Expected: PASS (3 tests)

### Step 6: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores/
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores/
git commit -m "feat(store): add DialogStore with unified MVI flow

- DialogStore manages SessionState via MVI pattern
- Executor processes intents, calls AI, sends packets
- Reducer ONLY applies state from NetworkSession packets
- Proves unified loopback architecture works end-to-end"
```

---

## Task 6: Integration Test - Full Solo Mode Flow

**Files:**
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/integration/SoloModeIntegrationTest.kt`

### Step 1: Write the integration test

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/integration/SoloModeIntegrationTest.kt
package com.bablabs.bringabrainlanguage.integration

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests verifying the complete Solo Mode flow:
 * Intent → Executor → AIProvider → Packet → NetworkSession (Loopback) → Reducer → State
 */
class SoloModeIntegrationTest {
    
    @Test
    fun `complete solo game flow works end-to-end`() = runTest {
        // Setup
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        val coffeeScenario = Scenario(
            id = "coffee-shop",
            name = "Ordering Coffee",
            description = "Practice ordering at a Spanish cafe",
            availableRoles = listOf(
                Role("barista", "Barista", "The coffee shop worker"),
                Role("customer", "Customer", "The person ordering")
            )
        )
        
        // 1. Start Solo Game
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = coffeeScenario,
            userRole = coffeeScenario.availableRoles[1] // Customer
        ))
        
        // Wait for state update
        val stateAfterStart = store.state.first { it.scenario != null }
        
        assertEquals(SessionMode.SOLO, stateAfterStart.mode)
        assertEquals(GamePhase.ACTIVE, stateAfterStart.currentPhase)
        assertEquals("Ordering Coffee", stateAfterStart.scenario?.name)
        
        // 2. Generate first dialog line
        store.accept(DialogStore.Intent.Generate)
        
        // Wait for AI response to come through loopback
        val stateAfterGenerate = store.state.first { it.dialogHistory.size == 1 }
        
        assertEquals(1, stateAfterGenerate.dialogHistory.size)
        val firstLine = stateAfterGenerate.dialogHistory.first()
        assertTrue(firstLine.textNative.isNotBlank())
        assertTrue(firstLine.textTranslated.isNotBlank())
        
        // 3. Verify vector clock was incremented (proves loopback path)
        assertTrue(stateAfterGenerate.vectorClock.timestamps.values.any { it > 0 })
        
        // 4. Generate second dialog line
        store.accept(DialogStore.Intent.Generate)
        
        val stateAfterSecondGenerate = store.state.first { it.dialogHistory.size == 2 }
        
        assertEquals(2, stateAfterSecondGenerate.dialogHistory.size)
        
        // Vector clock should have incremented again
        val clockValue = stateAfterSecondGenerate.vectorClock.timestamps.values.maxOrNull() ?: 0
        assertTrue(clockValue >= 2, "Vector clock should increment with each packet")
    }
    
    @Test
    fun `multiple generate calls produce sequential dialog`() = runTest {
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test", "Test", "Test", emptyList()),
            userRole = Role("user", "User", "Test")
        ))
        
        // Wait for setup
        store.state.first { it.scenario != null }
        
        // Generate 3 lines
        repeat(3) {
            store.accept(DialogStore.Intent.Generate)
            delay(100) // Give time for async processing
        }
        
        // Wait for all lines
        val finalState = store.state.first { it.dialogHistory.size >= 3 }
        
        assertEquals(3, finalState.dialogHistory.size)
        
        // Each line should have a unique ID
        val uniqueIds = finalState.dialogHistory.map { it.id }.toSet()
        assertEquals(3, uniqueIds.size, "Each dialog line should have unique ID")
    }
}
```

### Step 2: Run integration test

Run: `./gradlew :composeApp:allTests --tests "*.SoloModeIntegrationTest"`
Expected: PASS (2 tests)

### Step 3: Commit

```bash
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/integration/
git commit -m "test(integration): add Solo Mode end-to-end integration tests

- Verifies complete flow: Intent → AI → Packet → Loopback → State
- Confirms vector clock increments through loopback path
- Tests sequential dialog generation"
```

---

## Task 7: Update BrainSDK Entry Point

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/BrainSDK.kt`

### Step 1: Update BrainSDK to expose the store

```kotlin
// composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/BrainSDK.kt
package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.models.SessionState
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.flow.StateFlow

/**
 * BrainSDK is the main entry point for native apps (SwiftUI/Compose).
 * 
 * Native apps should:
 * 1. Create an instance of BrainSDK
 * 2. Observe the `state` StateFlow for UI updates
 * 3. Call methods to dispatch intents
 */
class BrainSDK {
    private val networkSession = LoopbackNetworkSession(
        localPeerId = generatePeerId()
    )
    
    private val aiProvider = MockAIProvider()
    
    private val dialogStore = DialogStore(
        networkSession = networkSession,
        aiProvider = aiProvider
    )
    
    /**
     * The single source of truth for UI state.
     * Observe this from SwiftUI/Compose.
     */
    val state: StateFlow<SessionState> = dialogStore.state
    
    /**
     * Start a Solo game session.
     */
    fun startSoloGame(scenarioId: String, userRoleId: String) {
        // For Phase 1, use hardcoded scenarios
        val scenario = getScenarioById(scenarioId)
        val role = scenario.availableRoles.find { it.id == userRoleId }
            ?: scenario.availableRoles.first()
        
        dialogStore.accept(DialogStore.Intent.StartSoloGame(
            scenario = scenario,
            userRole = role
        ))
    }
    
    /**
     * Request AI to generate the next dialog line.
     */
    fun generate() {
        dialogStore.accept(DialogStore.Intent.Generate)
    }
    
    /**
     * Leave the current game session.
     */
    fun leaveGame() {
        dialogStore.accept(DialogStore.Intent.LeaveGame)
    }
    
    /**
     * Get available scenarios for the lobby.
     */
    fun getAvailableScenarios(): List<com.bablabs.bringabrainlanguage.domain.models.Scenario> {
        return listOf(
            getScenarioById("coffee-shop"),
            getScenarioById("the-heist"),
            getScenarioById("first-date")
        )
    }
    
    private fun getScenarioById(id: String): com.bablabs.bringabrainlanguage.domain.models.Scenario {
        return when (id) {
            "coffee-shop" -> com.bablabs.bringabrainlanguage.domain.models.Scenario(
                id = "coffee-shop",
                name = "Ordering Coffee",
                description = "Practice ordering at a Spanish cafe",
                availableRoles = listOf(
                    com.bablabs.bringabrainlanguage.domain.models.Role("barista", "Barista", "The coffee shop worker"),
                    com.bablabs.bringabrainlanguage.domain.models.Role("customer", "Customer", "The person ordering")
                )
            )
            "the-heist" -> com.bablabs.bringabrainlanguage.domain.models.Scenario(
                id = "the-heist",
                name = "The Heist",
                description = "A dramatic crime scene unfolds",
                availableRoles = listOf(
                    com.bablabs.bringabrainlanguage.domain.models.Role("detective", "Detective", "The investigator"),
                    com.bablabs.bringabrainlanguage.domain.models.Role("thief", "Thief", "The suspect")
                )
            )
            else -> com.bablabs.bringabrainlanguage.domain.models.Scenario(
                id = "first-date",
                name = "First Date",
                description = "A romantic dinner conversation",
                availableRoles = listOf(
                    com.bablabs.bringabrainlanguage.domain.models.Role("date1", "Alex", "First person"),
                    com.bablabs.bringabrainlanguage.domain.models.Role("date2", "Sam", "Second person")
                )
            )
        }
    }
    
    private fun generatePeerId(): String {
        return "player-${kotlin.random.Random.nextInt(10000, 99999)}"
    }
}
```

### Step 2: Run all tests to verify nothing broke

Run: `./gradlew :composeApp:allTests`
Expected: All tests PASS

### Step 3: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/BrainSDK.kt
git commit -m "feat(sdk): update BrainSDK entry point for native app consumption

- Exposes state as StateFlow for SwiftUI/Compose observation
- Provides startSoloGame, generate, leaveGame methods
- Includes hardcoded scenarios for Phase 1 testing
- Ready for native UI integration"
```

---

## Task 8: Clean Up and Remove Unused Files

**Files:**
- Delete: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/Greeting.kt`
- Update: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/ComposeAppCommonTest.kt`

### Step 1: Remove unused Greeting.kt

```bash
rm composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/Greeting.kt
```

### Step 2: Update common test to use new SDK

```kotlin
// composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/ComposeAppCommonTest.kt
package com.bablabs.bringabrainlanguage

import kotlin.test.Test
import kotlin.test.assertNotNull

class ComposeAppCommonTest {
    @Test
    fun testBrainSDKInitialization() {
        val sdk = BrainSDK()
        assertNotNull(sdk.state.value)
    }
}
```

### Step 3: Run all tests

Run: `./gradlew :composeApp:allTests`
Expected: All tests PASS

### Step 4: Commit

```bash
git add -A
git commit -m "chore: remove unused Greeting.kt and update tests

- Clean up boilerplate from project template
- Update ComposeAppCommonTest to verify SDK initialization"
```

---

## Summary

After completing all tasks, the project structure should look like:

```
composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/
├── BrainSDK.kt                           # SDK entry point
├── Platform.kt                           # Platform expect declarations
├── domain/
│   ├── interfaces/
│   │   ├── AIProvider.kt                 # AI generation interface
│   │   └── NetworkSession.kt             # Network abstraction
│   ├── models/
│   │   ├── DialogLine.kt                 # Dialog data model
│   │   ├── Packet.kt                     # Network packet structure
│   │   ├── Participant.kt                # Player data model
│   │   ├── SessionState.kt               # Single source of truth
│   │   └── VectorClock.kt                # Distributed sync primitive
│   └── stores/
│       └── DialogStore.kt                # MVI store implementation
└── infrastructure/
    ├── ai/
    │   └── MockAIProvider.kt             # Mock AI for Phase 1
    └── network/
        └── LoopbackNetworkSession.kt     # Solo mode network
```

**Verification Checklist:**
- [ ] All unit tests pass: `./gradlew :composeApp:allTests`
- [ ] Project builds: `./gradlew build`
- [ ] Integration test proves loopback flow works

**Next Phase:** Implement real BLE networking with Kable library.
