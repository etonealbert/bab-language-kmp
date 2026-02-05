# Bring a Brain KMP SDK Architecture Design

**Date:** 2026-02-05  
**Status:** Finalized  
**Author:** Brainstorming Session  

---

## Executive Summary

This document defines the architecture for the "Bring a Brain" Kotlin Multiplatform SDK—a headless logic layer for a collaborative language learning game. The design emphasizes:

1. **Unified Loopback Pattern**: All state mutations flow through `NetworkSession`, even in Solo Mode
2. **Authoritative Host Model**: iOS device acts as BLE Central + AI Brain for offline play
3. **Headless MVI**: Pure Kotlin logic with native UI consumption via StateFlow

---

## 1. Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    NATIVE UI LAYER                          │
│         SwiftUI (iOS)  /  Jetpack Compose (Android)         │
│         Observes StateFlow • Dispatches Intents             │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               PRESENTATION LAYER (Decompose)                │
│    RootComponent → LobbyComponent → GameComponent           │
│    Navigation Stack Serialization • Lifecycle Binding       │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                 DOMAIN LAYER (MVI Stores)                   │
│    DialogStore • ConnectionStore • VoteStore                │
│    SessionState (Single Source of Truth)                    │
│    Executor dispatches to NetworkSession                    │
└───────────────┬───────────────────────────┬─────────────────┘
                │                           │
                ▼                           ▼
┌───────────────────────────┐  ┌──────────────────────────────┐
│   INFRASTRUCTURE: NET     │  │  INFRASTRUCTURE: AI          │
│  NetworkSession Interface │  │   AIProvider Interface       │
│  ├─ LoopbackSession       │  │   ├─ MockAIProvider          │
│  ├─ BleNetworkSession     │  │   ├─ NativeLLM (iOS 26)      │
│  └─ WsNetworkSession      │  │   └─ ServerLLM (Rust API)    │
└───────────────────────────┘  └──────────────────────────────┘
```

**Key Decisions:**
- Decompose owns navigation state; MVI Stores own game state
- NetworkSession and AIProvider are interfaces—mocked first, real later
- All state flows upward via StateFlow; all actions flow downward via Intent

---

## 2. The Unified Loopback Pattern

### Core Principle
**ALL state mutations flow through NetworkSession.incomingPackets → Reducer**, regardless of mode.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    UNIFIED MVI FLOW (ALL MODES)                     │
│                                                                     │
│   User Action                                                       │
│       │                                                             │
│       ▼                                                             │
│   ┌─────────────┐                                                   │
│   │   Intent    │  (e.g., Intent.Generate, Intent.Vote)             │
│   └──────┬──────┘                                                   │
│          │                                                          │
│          ▼                                                          │
│   ┌─────────────┐      ┌───────────────────────────────────────┐   │
│   │  Executor   │─────▶│        NetworkSession.send(Packet)    │   │
│   └─────────────┘      └───────────────────┬───────────────────┘   │
│                                            │                        │
│                        ┌───────────────────┼───────────────────┐   │
│                        │                   │                   │   │
│                        ▼                   ▼                   ▼   │
│              ┌──────────────┐    ┌──────────────┐    ┌──────────┐ │
│              │  Loopback    │    │     BLE      │    │WebSocket │ │
│              │  (Solo Mode) │    │  (Offline)   │    │ (Online) │ │
│              └──────┬───────┘    └──────┬───────┘    └────┬─────┘ │
│                     │                   │                  │       │
│                     │ Immediate         │ Host Process     │ Server│
│                     │ Echo              │ + Broadcast      │ Relay │
│                     │                   │                  │       │
│                     ▼                   ▼                  ▼       │
│              ┌─────────────────────────────────────────────────┐   │
│              │         NetworkSession.incomingPackets          │   │
│              └───────────────────────┬─────────────────────────┘   │
│                                      │                              │
│                                      ▼                              │
│                              ┌─────────────┐                        │
│                              │   Reducer   │  (Apply confirmed      │
│                              │             │   state from packet)   │
│                              └─────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

### NetworkSession Implementations

| Implementation | Behavior | Vector Clock |
|----------------|----------|--------------|
| `LoopbackNetworkSession` | Echoes packet back immediately (Solo) | Increments local peer ID |
| `BleNetworkSession` | Host validates, broadcasts; Client forwards | Host merges all clocks |
| `WsNetworkSession` | Server relay (future) | Server acts as Host |

### Why This Pattern?

1. **Single Reducer Path**: State ONLY changes via incomingPackets → Reducer
2. **Testable**: Inject LoopbackNetworkSession in tests without mocking BLE
3. **Vector Clock Consistency**: Solo mode still increments VectorClock["local"]
4. **No Branching in Executor**: executor.send(packet) works identically everywhere

---

## 3. Host/Client Asymmetry

### HOST Mode (iOS Only for Offline)
```
┌─────────────────────────────────────────────────────┐
│                   HOST DEVICE                       │
│                                                     │
│  ┌─────────────┐      ┌─────────────────┐          │
│  │ DialogStore │──────│   AIProvider    │          │
│  │  Executor   │      │ (Foundation LM) │          │
│  └──────┬──────┘      └─────────────────┘          │
│         │                                           │
│    State Mutation                                   │
│         │                                           │
│         ▼                                           │
│  ┌─────────────┐      ┌─────────────────┐          │
│  │  Reducer    │      │ NetworkSession  │          │
│  │ (Apply)     │─────▶│ (BLE Central)   │──────────┼──▶ Broadcast
│  └─────────────┘      └─────────────────┘          │
└─────────────────────────────────────────────────────┘
```

### CLIENT Mode (Android or iOS Joining)
```
┌─────────────────────────────────────────────────────┐
│                   CLIENT DEVICE                     │
│                                                     │
│  ┌─────────────┐      ┌─────────────────┐          │
│  │ DialogStore │◀─────│ NetworkSession  │◀─────────┼── Receive
│  │  (Read-Only)│      │ (BLE Peripheral)│          │
│  └──────┬──────┘      └─────────────────┘          │
│         │                                           │
│    Intents (Votes, Scene Change Requests)           │
│         │                                           │
│         ▼                                           │
│  NetworkSession.send(Intent) ───────────────────────┼──▶ To Host
└─────────────────────────────────────────────────────┘
```

**Rule**: Host owns mutations; Clients forward intents and apply confirmed state.

---

## 4. NetworkSession Interface

```kotlin
interface NetworkSession {
    val localPeerId: String
    val state: StateFlow<ConnectionState>
    
    // ALL state changes flow through here (including loopback)
    val incomingPackets: Flow<Packet>
    
    // ALL intents go out through here (even in Solo mode)
    suspend fun send(packet: Packet, recipientId: String? = null)
    
    suspend fun disconnect()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val peers: List<PeerInfo>) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
}
```

---

## 5. Packet Structure

```kotlin
@Serializable
data class Packet(
    val type: PacketType,
    val senderId: String,
    val vectorClock: VectorClock,
    val payload: PacketPayload
)

@Serializable
sealed class PacketPayload {
    // Game Actions
    data class GenerateRequest(val prompt: String) : PacketPayload()
    data class DialogLineAdded(val line: DialogLine) : PacketPayload()
    
    // Voting
    data class VoteRequest(val proposerId: String, val action: VoteAction) : PacketPayload()
    data class VoteCast(val voterId: String, val vote: Boolean) : PacketPayload()
    
    // State Sync
    data class FullStateSnapshot(val state: SessionState) : PacketPayload()
    data class NavigationSync(val config: ScreenConfig) : PacketPayload()
    
    // Control
    object Heartbeat : PacketPayload()
    data class Handshake(val profile: UserProfile) : PacketPayload()
}
```

---

## 6. BLE GATT Profile ("SyncProfile")

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYNC SERVICE (0xBAB1)                        │
│                                                                 │
│   CHAR_COMMAND (0xBAB2) - Write with Response                   │
│   ───────────────────────────────────────────────────────────── │
│   Purpose: Client → Host intents (Generate, Vote, NACK)         │
│   Reliable: Yes (ACK required)                                  │
│                                                                 │
│   CHAR_STATE (0xBAB3) - Indicate                                │
│   ───────────────────────────────────────────────────────────── │
│   Purpose: Host → Client confirmed state broadcasts             │
│   Contains: Full Packet (with VectorClock + Payload)            │
│                                                                 │
│   CHAR_STREAM (0xBAB4) - Notify (Unreliable/Fast)               │
│   ───────────────────────────────────────────────────────────── │
│   Purpose: LLM text streaming (token-by-token)                  │
│   Recovery: NACK mechanism via CHAR_COMMAND                     │
└─────────────────────────────────────────────────────────────────┘
```

| Characteristic | Direction | Reliability | Use Case |
|----------------|-----------|-------------|----------|
| CHAR_COMMAND | Client → Host | Reliable | Intents, NACKs, Handshakes |
| CHAR_STATE | Host → Clients | Reliable | Confirmed state changes |
| CHAR_STREAM | Host → Clients | Unreliable | LLM token streaming |

---

## 7. AI Provider Integration

```kotlin
interface AIProvider {
    suspend fun generate(context: DialogContext): DialogLine
    fun streamGenerate(context: DialogContext): Flow<String>
}
```

### Flow (Host Only):

```
Intent.Generate
    │
    ▼
Executor:
    1. val response = aiProvider.generate(prompt)
    2. val packet = Packet(
           type = DIALOG_LINE_ADDED,
           vectorClock = clock.increment(localPeerId),
           payload = DialogLineAdded(response)
       )
    3. networkSession.send(packet)
    │
    ▼
NetworkSession.incomingPackets (loopback/broadcast)
    │
    ▼
Reducer applies DialogLineAdded
```

---

## 8. Session State

```kotlin
@Serializable
data class SessionState(
    // Connection
    val mode: SessionMode,  // SOLO, HOST, CLIENT
    val connectionStatus: ConnectionStatus,
    val peers: List<Participant>,
    
    // Game State
    val scenario: Scenario?,
    val roles: Map<String, Role>,  // peerId → Role
    val dialogHistory: List<DialogLine>,
    val currentPhase: GamePhase,
    
    // Voting
    val pendingVote: VoteRequest?,
    val voteResults: Map<String, Boolean>,
    
    // Sync Metadata
    val vectorClock: VectorClock,
    val lastSyncTimestamp: Long
)

enum class SessionMode { SOLO, HOST, CLIENT }
enum class GamePhase { LOBBY, ROLE_SELECTION, ACTIVE, VOTING, FINISHED }
```

---

## 9. Intent Catalog

| Intent | Who Can Fire | Executor Action |
|--------|--------------|-----------------|
| StartSoloGame | Any | Create LoopbackNetworkSession, mode=SOLO |
| HostGame | iOS Only | Create BleNetworkSession as Central |
| JoinGame(hostId) | Any | Create BleNetworkSession as Peripheral |
| Generate | Host/Solo | Call AIProvider, emit DialogLineAdded packet |
| ProposeSceneChange | Any | Send VoteRequest packet |
| CastVote(yes/no) | Any | Send VoteCast packet |
| LeaveGame | Any | Disconnect, reset state |

---

## 10. Vector Clock Implementation

```kotlin
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

    enum class Comparison { BEFORE, AFTER, CONCURRENT, EQUAL }

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
}
```

---

## 11. Reducer Pattern

```kotlin
fun reduce(state: SessionState, packet: Packet): SessionState {
    // Always merge vector clocks first
    val mergedClock = state.vectorClock.merge(packet.vectorClock)
    
    return when (packet.payload) {
        is DialogLineAdded -> state.copy(
            dialogHistory = state.dialogHistory + packet.payload.line,
            vectorClock = mergedClock
        )
        is VoteRequest -> state.copy(
            pendingVote = packet.payload,
            currentPhase = GamePhase.VOTING,
            vectorClock = mergedClock
        )
        is VoteCast -> state.copy(
            voteResults = state.voteResults + (packet.senderId to packet.payload.vote),
            vectorClock = mergedClock
        )
        is FullStateSnapshot -> packet.payload.state.copy(
            vectorClock = mergedClock
        )
        // ... etc
        else -> state.copy(vectorClock = mergedClock)
    }
}
```

---

## 12. Folder Structure

```
commonMain/
├── domain/
│   ├── models/
│   │   ├── SessionState.kt
│   │   ├── DialogLine.kt
│   │   ├── Participant.kt
│   │   ├── VectorClock.kt
│   │   └── Packet.kt
│   ├── interfaces/
│   │   ├── NetworkSession.kt
│   │   └── AIProvider.kt
│   └── stores/
│       ├── DialogStore.kt
│       ├── ConnectionStore.kt
│       └── VoteStore.kt
├── infrastructure/
│   ├── network/
│   │   ├── LoopbackNetworkSession.kt
│   │   ├── BleNetworkSession.kt      (Kable)
│   │   └── WsNetworkSession.kt       (Ktor - future)
│   └── ai/
│       ├── MockAIProvider.kt
│       └── NativeLLM.kt              (expect/actual for iOS)
└── presentation/
    └── components/
        ├── RootComponent.kt
        ├── LobbyComponent.kt
        └── GameComponent.kt
```

---

## 13. Implementation Phases

### Phase 1: Walking Skeleton (Weeks 1-4)
- MockAIProvider + LoopbackNetworkSession
- DialogStore with full Intent/Reducer flow
- SwiftUI/Compose observing StateFlow
- **Success**: Click "Generate" → see mocked dialog appear

### Phase 2: The Sync Engine (Weeks 5-8)
- BleNetworkSession with Kable
- Host/Client mode switching
- VectorClock integration
- **Success**: 2 phones sync dialog in <200ms

### Phase 3: The Brain (Weeks 9-12)
- iOS 26 Foundation Model integration via Swift Bridge
- NativeLLM actual implementation
- Prompt engineering for language tutoring
- **Success**: AI generates contextual dialog lines

### Phase 4: Server & Monetization (Weeks 13+)
- WsNetworkSession for online play
- Rust backend integration
- Subscription validation
- **Success**: Cross-internet multiplayer works

---

## 14. Key Constraints

| Constraint | Implication |
|------------|-------------|
| iOS-Only Host (Offline) | Android cannot create offline sessions |
| Authoritative Host | No CRDT merge; Host decides all conflicts |
| Loopback Pattern | All modes use same Reducer path |
| Foundation Models iOS 26+ | Minimum iOS deployment target is 26 |

---

## Next Steps

1. **Create implementation plan** (task breakdown per phase)
2. **Set up git worktree** for isolated development
3. **Implement Phase 1** with full TDD approach
