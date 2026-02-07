# BLE Multiplayer Design

## Overview

This document describes the design for Bluetooth Low Energy (BLE) multiplayer functionality in the BabLanguageSDK. The SDK supports 2-4 players connecting locally via BLE to participate in turn-based language learning role-play scenarios.

## Architecture

### Host/Client Model

One device acts as the **Host** (BLE Peripheral), others join as **Clients** (BLE Central).

```
Host Device                          Client Device(s)
┌─────────────────────┐              ┌─────────────────────┐
│     BrainSDK        │              │     BrainSDK        │
│  - AI generation    │              │  - State mirror     │
│  - State authority  │◄────────────►│  - UI updates       │
│  - Turn management  │   BLE Sync   │  - Input handling   │
├─────────────────────┤              ├─────────────────────┤
│   Native BLE Layer  │              │   Native BLE Layer  │
│  (CBPeripheralMgr)  │◄────────────►│     (Kable)         │
└─────────────────────┘              └─────────────────────┘
```

### SDK vs Native App Responsibilities

| Responsibility | SDK | Native App |
|---------------|-----|------------|
| Game state management | ✅ | |
| AI dialog generation | ✅ | |
| Turn/role assignment | ✅ | |
| Packet serialization | ✅ | |
| XP/achievement tracking | ✅ | |
| BLE advertising (host) | | ✅ |
| BLE scanning (client) | ✅ (via Kable) | |
| BLE connection management | | ✅ |
| Raw byte transfer | | ✅ |
| Speech recognition | | ✅ |
| Voice synthesis (TTS) | | ✅ |
| UI rendering | | ✅ |

## User Flow

### 1. Lobby Phase

```
Host Device:
1. User selects "Host Game"
2. Chooses scenario and their role
3. SDK: hostGame(scenarioId, roleId)
4. SDK: startHostAdvertising() → returns "bab-game-{sessionId}"
5. Native app starts BLE advertising with service UUID
6. Lobby UI shows waiting for players

Client Device:
1. User selects "Join Game"
2. SDK: scanForHosts() → Flow<DiscoveredDevice>
3. UI shows list of nearby hosts
4. User taps host to join
5. Native app connects via BLE
6. SDK: joinGame(hostDeviceId, roleId)
7. Lobby UI shows connected players
```

### 2. Role Selection Phase

```
1. All players see available roles for scenario
2. Each player picks a role (or host assigns)
3. SDK validates: no duplicate roles, all players have roles
4. Host taps "Start Game"
5. SDK: state.currentPhase → ACTIVE
```

### 3. Active Game Phase (Turn-Based)

```
For each turn:
1. SDK determines whose turn (currentTurnPlayerId)
2. Host's AI generates next line for that player
3. Line synced to all devices with visibility=PRIVATE
4. Active player sees: target text + translation
5. Other players see: "[Player A is reading...]"
6. Active player reads aloud (native speech recognition)
7. Native app evaluates pronunciation accuracy
8. Player taps "Done"
9. Native app calls: sdk.completeLine(lineId, pronunciationResult)
10. SDK: line.visibility → COMMITTED
11. All players see line in theater view
12. Turn advances to next player
```

### 4. Session End

```
1. All dialog lines completed OR host ends early
2. SDK: state.currentPhase → FINISHED
3. SDK calculates: SessionSummary with XP, stats, achievements
4. Each player sees summary screen
5. XP added to user profiles
6. Vocabulary words added to review queue
```

## Data Models

### DialogLine (Enhanced)

```kotlin
data class DialogLine(
    val id: String,
    val roleId: String,
    val roleName: String,
    val textNative: String,            // Target language text
    val textTranslated: String,        // Translation in user's native language
    val timestamp: Long,
    val assignedToPlayerId: String,    // Who should read this
    val visibility: LineVisibility,    // PRIVATE or COMMITTED
    val pronunciationResult: PronunciationResult?  // Filled when completed
)

enum class LineVisibility {
    PRIVATE,    // Only assigned player sees
    COMMITTED   // All players see in theater
}
```

### PronunciationResult

```kotlin
data class PronunciationResult(
    val errorCount: Int,               // Number of errors
    val accuracy: Float,               // 0.0 to 1.0
    val wordErrors: List<WordError>,   // Specific mispronunciations
    val skipped: Boolean,              // User skipped (mic off)
    val duration: Long                 // Time taken to read (ms)
)

data class WordError(
    val word: String,                  // The word with error
    val position: Int,                 // Position in sentence
    val expected: String?,             // Expected pronunciation
    val heard: String?                 // What was recognized
)
```

### Session State (Enhanced)

```kotlin
data class SessionState(
    // Existing fields...
    
    // Multiplayer additions
    val lobbyPlayers: List<LobbyPlayer>,
    val isAdvertising: Boolean,
    val connectedPeers: List<ConnectedPeer>,
    
    // Turn management
    val currentTurnPlayerId: String?,
    val pendingLine: DialogLine?,           // Current line being read (PRIVATE)
    val committedHistory: List<DialogLine>, // Theater view
    
    // Stats
    val playerStats: Map<String, PlayerStats>,
    val sessionLeaderboard: SessionLeaderboard?
)

data class LobbyPlayer(
    val peerId: String,
    val displayName: String,
    val assignedRole: Role?,
    val isReady: Boolean,
    val connectionQuality: ConnectionQuality
)

enum class ConnectionQuality { EXCELLENT, GOOD, FAIR, POOR }
```

### Gamification Models

```kotlin
data class PlayerStats(
    val playerId: String,
    val linesCompleted: Int,
    val totalErrors: Int,
    val perfectLines: Int,              // 100% accuracy
    val averageAccuracy: Float,
    val currentStreak: Int,             // Consecutive perfect lines
    val xpEarned: Int
)

data class SessionLeaderboard(
    val rankings: List<PlayerRanking>,
    val updatedAt: Long
)

data class PlayerRanking(
    val rank: Int,
    val playerId: String,
    val displayName: String,
    val score: Int,                     // Combined XP + accuracy
    val highlight: String?              // "Most Improved", "Perfect Score", etc.
)

data class SessionSummary(
    val scenarioName: String,
    val duration: Duration,
    val totalLines: Int,
    val playerRankings: List<PlayerRanking>,
    val xpEarned: Map<String, Int>,
    val achievementsUnlocked: List<Achievement>,
    val vocabularyLearned: List<VocabularyEntry>,
    val overallAccuracy: Float
)
```

## SDK Public API

### New Methods for BLE Multiplayer

```kotlin
class BrainSDK {
    // === Existing ===
    fun scanForHosts(): Flow<DiscoveredDevice>
    fun hostGame(scenarioId: String, userRoleId: String)
    fun joinGame(hostDeviceId: String, userRoleId: String)
    fun generate()
    
    // === NEW: Advertising Control ===
    
    /** Start BLE advertising. Returns service name for native layer. */
    fun startHostAdvertising(): String
    
    /** Stop advertising and close lobby. */
    fun stopHostAdvertising()
    
    // === NEW: Connection Callbacks (called by native app) ===
    
    /** Called when native BLE establishes connection with peer. */
    fun onPeerConnected(peerId: String, peerName: String)
    
    /** Called when peer disconnects. */
    fun onPeerDisconnected(peerId: String)
    
    /** Called when native BLE receives data from peer. */
    fun onDataReceived(fromPeerId: String, data: ByteArray)
    
    // === NEW: Outgoing Data ===
    
    /** Flow of packets to send. Native app subscribes and transmits via BLE. */
    val outgoingPackets: Flow<OutgoingPacket>
    
    // === NEW: Turn Management ===
    
    /** Complete current line with pronunciation result. */
    fun completeLine(lineId: String, result: PronunciationResult)
    
    /** Skip current line (microphone unavailable). */
    fun skipLine(lineId: String)
    
    // === NEW: Lobby Management ===
    
    /** Assign role to player in lobby. */
    fun assignRole(playerId: String, roleId: String)
    
    /** Mark player as ready. */
    fun setPlayerReady(playerId: String, isReady: Boolean)
    
    /** Start the game (host only, all players must be ready). */
    fun startMultiplayerGame()
}

data class OutgoingPacket(
    val targetPeerId: String?,   // null = broadcast to all
    val data: ByteArray
)
```

## XP Calculation

```kotlin
object XPCalculator {
    fun calculateLineXP(
        accuracy: Float,
        isStreak: Boolean,
        isMultiplayer: Boolean,
        isNewScenario: Boolean
    ): XPBreakdown {
        val baseXP = 10
        val accuracyBonus = (accuracy * 20).toInt()  // 0-20 XP
        val streakBonus = if (isStreak) 5 else 0
        val multiplayerBonus = if (isMultiplayer) (baseXP * 0.5).toInt() else 0
        val newScenarioBonus = if (isNewScenario) 25 else 0
        
        return XPBreakdown(
            baseXP = baseXP,
            accuracyBonus = accuracyBonus,
            streakBonus = streakBonus,
            multiplayerBonus = multiplayerBonus,
            firstTimeScenarioBonus = newScenarioBonus,
            total = baseXP + accuracyBonus + streakBonus + multiplayerBonus + newScenarioBonus
        )
    }
}
```

## Achievements

| ID | Name | Description | Trigger |
|----|------|-------------|---------|
| `first_multiplayer` | First Words Together | Complete first multiplayer session | Session end with 2+ players |
| `perfect_pair` | Perfect Pair | Both players get 100% on same exchange | Two consecutive 100% lines |
| `chatterbox` | Chatterbox | Complete 50 lines in one session | Line count ≥ 50 |
| `language_partner` | Language Partner | Play with same friend 5 times | Session count with same peer |
| `flawless_scene` | Flawless Scene | Complete scenario with 0 errors | Session accuracy = 100% |
| `streak_master` | Streak Master | 10 perfect lines in a row | Streak count = 10 |
| `speed_reader` | Speed Reader | Average read time < 3 seconds | Avg duration < 3000ms |

## BLE Protocol

### Service UUID
```
Service: 0x1234-ABCD-... (custom BabLanguage UUID)
Characteristic: Write (client → host)
Characteristic: Notify (host → client)
```

### Packet Format
SDK provides `PacketFragmenter` to handle BLE MTU limits (typically 20-512 bytes).

```
Fragment Header (4 bytes):
  [0-1]: Packet ID (uint16)
  [2]:   Fragment index (uint8)
  [3]:   Total fragments (uint8)
Fragment Body: JSON bytes
```

## Error Handling

| Error | SDK Behavior | Native App Behavior |
|-------|-------------|---------------------|
| BLE connection lost | `state.connectionStatus → RECONNECTING` | Attempt reconnect |
| Reconnect timeout (30s) | `state.connectionStatus → DISCONNECTED` | Show "Connection Lost" |
| Host leaves | Clients see `sessionEnded` event | Navigate to home |
| Client leaves | Remove from lobby, continue game | Show "[Name] left" |
| Speech recognition fails | Allow retry or skip | Show retry dialog |

## Testing

Unit tests should cover:
- Packet fragmentation/reassembly
- Turn order logic
- XP calculation
- Achievement triggers
- State synchronization

Integration tests:
- Full game flow with mock BLE transport
- Reconnection scenarios
- Edge cases (player leaves mid-turn)
