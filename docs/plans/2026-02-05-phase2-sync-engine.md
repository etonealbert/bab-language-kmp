# Phase 2: The Sync Engine (BLE) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace LoopbackNetworkSession with real BLE networking using Kable, enabling 2+ phones to sync dialog in <200ms.

**Architecture:** Star Topology with iOS as Host (BLE Central) and up to 3 Peers (BLE Peripherals). Host validates all actions and broadcasts confirmed state.

**Tech Stack:** Kable 0.30.0, Kotlin Coroutines, kotlinx-serialization (Protobuf for compactness)

---

## Prerequisites

- Phase 1 complete (LoopbackNetworkSession working)
- Two physical devices for testing (iOS + Android or 2x iOS)
- Kable dependency already in build.gradle.kts

---

## Task 1: Define BLE GATT UUIDs and Service Constants

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleConstants.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleConstantsTest.kt`

### Step 1: Write the failing test

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BleConstantsTest {
    
    @Test
    fun serviceUuidIsValidFormat() {
        val uuid = BleConstants.SERVICE_UUID
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }
    
    @Test
    fun characteristicUuidsAreDifferent() {
        val uuids = listOf(
            BleConstants.CHAR_COMMAND_UUID,
            BleConstants.CHAR_STATE_UUID,
            BleConstants.CHAR_STREAM_UUID
        )
        assertEquals(3, uuids.toSet().size)
    }
    
    @Test
    fun mtuDefaultIsReasonable() {
        assertTrue(BleConstants.DEFAULT_MTU >= 23)
        assertTrue(BleConstants.DEFAULT_MTU <= 517)
    }
}
```

### Step 2: Run test to verify failure

Run: `./gradlew :composeApp:allTests --tests "*.BleConstantsTest"`
Expected: FAIL with "Unresolved reference: BleConstants"

### Step 3: Implement BleConstants

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

object BleConstants {
    // Custom Service UUID for Bring a Brain Sync
    const val SERVICE_UUID = "0000bab1-0000-1000-8000-00805f9b34fb"
    
    // Characteristics
    const val CHAR_COMMAND_UUID = "0000bab2-0000-1000-8000-00805f9b34fb"  // Client → Host (Write+Response)
    const val CHAR_STATE_UUID = "0000bab3-0000-1000-8000-00805f9b34fb"    // Host → Client (Indicate)
    const val CHAR_STREAM_UUID = "0000bab4-0000-1000-8000-00805f9b34fb"   // Host → Client (Notify, unreliable)
    
    // Connection Parameters
    const val DEFAULT_MTU = 185
    const val MAX_MTU = 517
    const val SUPERVISION_TIMEOUT_MS = 4000
    
    // Packet Framing
    const val MAX_PACKET_SIZE = 512
    const val HEADER_SIZE = 4  // 2 bytes seq + 2 bytes total
}
```

### Step 4: Run test to verify pass

Run: `./gradlew :composeApp:allTests --tests "*.BleConstantsTest"`
Expected: PASS

### Step 5: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/
git commit -m "feat(ble): add BLE GATT constants for Sync Engine"
```

---

## Task 2: Packet Serialization with Fragmentation Support

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/PacketFragmenter.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/PacketFragmenterTest.kt`

### Step 1: Write the failing test

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketFragmenterTest {
    
    private val fragmenter = PacketFragmenter(maxChunkSize = 20)
    
    @Test
    fun smallPacketFitsInSingleFragment() {
        val packet = Packet(
            type = PacketType.HEARTBEAT,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.Heartbeat
        )
        
        val fragments = fragmenter.fragment(packet)
        
        assertEquals(1, fragments.size)
    }
    
    @Test
    fun largePacketSplitsIntoMultipleFragments() {
        val longText = "a".repeat(100)
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", longText, longText, 0L)
            )
        )
        
        val fragments = fragmenter.fragment(packet)
        
        assertTrue(fragments.size > 1)
    }
    
    @Test
    fun fragmentsReassembleToOriginalPacket() {
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(mapOf("peer-a" to 5L)),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", "Hola mundo", "Hello world", 123L)
            )
        )
        
        val fragments = fragmenter.fragment(packet)
        val reassembled = fragmenter.reassemble(fragments)
        
        assertEquals(packet, reassembled)
    }
    
    @Test
    fun fragmentsHaveSequenceNumbers() {
        val longText = "a".repeat(100)
        val packet = Packet(
            type = PacketType.DIALOG_LINE_ADDED,
            senderId = "peer-a",
            vectorClock = VectorClock(),
            payload = PacketPayload.DialogLineAdded(
                DialogLine("id", "speaker", "role", longText, longText, 0L)
            )
        )
        
        val fragments = fragmenter.fragment(packet)
        
        fragments.forEachIndexed { index, fragment ->
            assertEquals(index, fragment.sequenceNumber)
            assertEquals(fragments.size, fragment.totalFragments)
        }
    }
}
```

### Step 2: Run test to verify failure

Run: `./gradlew :composeApp:allTests --tests "*.PacketFragmenterTest"`
Expected: FAIL with "Unresolved reference: PacketFragmenter"

### Step 3: Implement PacketFragmenter

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.models.Packet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class Fragment(
    val sequenceNumber: Int,
    val totalFragments: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fragment) return false
        return sequenceNumber == other.sequenceNumber &&
               totalFragments == other.totalFragments &&
               data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + totalFragments
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class PacketFragmenter(
    private val maxChunkSize: Int = BleConstants.DEFAULT_MTU - BleConstants.HEADER_SIZE
) {
    private val json = Json { encodeDefaults = true }
    
    fun fragment(packet: Packet): List<Fragment> {
        val serialized = json.encodeToString(packet).encodeToByteArray()
        
        if (serialized.size <= maxChunkSize) {
            return listOf(Fragment(0, 1, serialized))
        }
        
        val chunks = serialized.toList().chunked(maxChunkSize)
        return chunks.mapIndexed { index, chunk ->
            Fragment(index, chunks.size, chunk.toByteArray())
        }
    }
    
    fun reassemble(fragments: List<Fragment>): Packet {
        val sorted = fragments.sortedBy { it.sequenceNumber }
        val combined = sorted.flatMap { it.data.toList() }.toByteArray()
        val jsonString = combined.decodeToString()
        return json.decodeFromString(jsonString)
    }
}
```

### Step 4: Run test to verify pass

Run: `./gradlew :composeApp:allTests --tests "*.PacketFragmenterTest"`
Expected: PASS

### Step 5: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/PacketFragmenter.kt
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/PacketFragmenterTest.kt
git commit -m "feat(ble): add PacketFragmenter for BLE MTU handling"
```

---

## Task 3: BLE Scanner Interface (expect/actual)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleScanner.kt`
- Create: `composeApp/src/androidMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleScanner.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleScanner.ios.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleScannerTest.kt`

### Step 1: Write the failing test

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import kotlin.test.Test
import kotlin.test.assertNotNull

class BleScannerTest {
    
    @Test
    fun scannerCanBeCreated() {
        val scanner = createBleScanner()
        assertNotNull(scanner)
    }
}
```

### Step 2: Implement expect/actual pattern

**Common (expect):**
```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import kotlinx.coroutines.flow.Flow

data class DiscoveredDevice(
    val id: String,
    val name: String?,
    val rssi: Int
)

interface BleScanner {
    fun scan(): Flow<DiscoveredDevice>
    fun stopScan()
}

expect fun createBleScanner(): BleScanner
```

**Android (actual):**
```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual fun createBleScanner(): BleScanner = AndroidBleScanner()

class AndroidBleScanner : BleScanner {
    private val scanner = Scanner {
        filters {
            match { services = listOf(uuidFrom(BleConstants.SERVICE_UUID)) }
        }
    }
    
    override fun scan(): Flow<DiscoveredDevice> {
        return scanner.advertisements.map { ad ->
            DiscoveredDevice(
                id = ad.identifier.toString(),
                name = ad.name,
                rssi = ad.rssi
            )
        }
    }
    
    override fun stopScan() {
        // Scanner automatically stops when flow collection stops
    }
}
```

**iOS (actual):**
```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual fun createBleScanner(): BleScanner = IosBleScanner()

class IosBleScanner : BleScanner {
    private val scanner = Scanner {
        filters {
            match { services = listOf(uuidFrom(BleConstants.SERVICE_UUID)) }
        }
    }
    
    override fun scan(): Flow<DiscoveredDevice> {
        return scanner.advertisements.map { ad ->
            DiscoveredDevice(
                id = ad.identifier.toString(),
                name = ad.name,
                rssi = ad.rssi
            )
        }
    }
    
    override fun stopScan() {
        // Scanner automatically stops when flow collection stops
    }
}
```

### Step 3: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleScanner.kt
git add composeApp/src/androidMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/
git add composeApp/src/iosMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/
git commit -m "feat(ble): add BleScanner with expect/actual for iOS and Android"
```

---

## Task 4: BleHostSession (Central/Host Mode)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleHostSession.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleHostSessionTest.kt`

### Step 1: Write the failing test

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import kotlin.test.Test
import kotlin.test.assertTrue

class BleHostSessionTest {
    
    @Test
    fun hostSessionImplementsNetworkSession() {
        val mockPeripheralManager = MockPeripheralManager()
        val session: NetworkSession = BleHostSession(
            localPeerId = "host-1",
            peripheralManager = mockPeripheralManager
        )
        
        assertTrue(session.state.value is ConnectionState.Disconnected)
    }
    
    @Test
    fun startAdvertisingChangesStateToConnecting() {
        val mockPeripheralManager = MockPeripheralManager()
        val session = BleHostSession(
            localPeerId = "host-1",
            peripheralManager = mockPeripheralManager
        )
        
        session.startAdvertising()
        
        assertTrue(session.state.value is ConnectionState.Connecting)
    }
}

class MockPeripheralManager {
    var isAdvertising = false
    
    fun startAdvertising() {
        isAdvertising = true
    }
    
    fun stopAdvertising() {
        isAdvertising = false
    }
}
```

### Step 2: Implement BleHostSession skeleton

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

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

class BleHostSession(
    override val localPeerId: String,
    private val peripheralManager: MockPeripheralManager  // Will be replaced with real Kable
) : NetworkSession {
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    private var currentClock = VectorClock()
    private val fragmenter = PacketFragmenter()
    
    fun startAdvertising() {
        peripheralManager.startAdvertising()
        _state.value = ConnectionState.Connecting
    }
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        currentClock = currentClock.increment(localPeerId)
        val updatedPacket = packet.copy(vectorClock = currentClock)
        
        // Fragment and send via BLE
        val fragments = fragmenter.fragment(updatedPacket)
        // TODO: Send fragments to connected peripherals
        
        // Also loop back to self (Host receives its own broadcasts)
        packetChannel.send(updatedPacket)
    }
    
    override suspend fun disconnect() {
        peripheralManager.stopAdvertising()
        _state.value = ConnectionState.Disconnected
        packetChannel.close()
    }
}
```

### Step 3: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleHostSession.kt
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleHostSessionTest.kt
git commit -m "feat(ble): add BleHostSession skeleton for Host/Central mode"
```

---

## Task 5: BleClientSession (Peripheral/Client Mode)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleClientSession.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleClientSessionTest.kt`

### Step 1: Write the failing test

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

import com.bablabs.bringabrainlanguage.domain.interfaces.ConnectionState
import com.bablabs.bringabrainlanguage.domain.interfaces.NetworkSession
import kotlin.test.Test
import kotlin.test.assertTrue

class BleClientSessionTest {
    
    @Test
    fun clientSessionImplementsNetworkSession() {
        val session: NetworkSession = BleClientSession(
            localPeerId = "client-1",
            hostDeviceId = "host-device-uuid"
        )
        
        assertTrue(session.state.value is ConnectionState.Disconnected)
    }
    
    @Test
    fun connectChangesStateToConnecting() {
        val session = BleClientSession(
            localPeerId = "client-1",
            hostDeviceId = "host-device-uuid"
        )
        
        session.initiateConnection()
        
        assertTrue(session.state.value is ConnectionState.Connecting)
    }
}
```

### Step 2: Implement BleClientSession skeleton

```kotlin
package com.bablabs.bringabrainlanguage.infrastructure.network.ble

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

class BleClientSession(
    override val localPeerId: String,
    private val hostDeviceId: String
) : NetworkSession {
    
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    private val packetChannel = Channel<Packet>(Channel.UNLIMITED)
    override val incomingPackets: Flow<Packet> = packetChannel.receiveAsFlow()
    
    private var currentClock = VectorClock()
    private val fragmenter = PacketFragmenter()
    
    fun initiateConnection() {
        _state.value = ConnectionState.Connecting
        // TODO: Use Kable to connect to hostDeviceId
    }
    
    override suspend fun send(packet: Packet, recipientId: String?) {
        currentClock = currentClock.increment(localPeerId)
        val updatedPacket = packet.copy(vectorClock = currentClock)
        
        // Fragment and send via CHAR_COMMAND to Host
        val fragments = fragmenter.fragment(updatedPacket)
        // TODO: Write fragments to Host's CHAR_COMMAND characteristic
    }
    
    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
        packetChannel.close()
        // TODO: Disconnect Kable peripheral
    }
    
    internal suspend fun onPacketReceived(packet: Packet) {
        packetChannel.send(packet)
    }
}
```

### Step 3: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleClientSession.kt
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/infrastructure/network/ble/BleClientSessionTest.kt
git commit -m "feat(ble): add BleClientSession skeleton for Client/Peripheral mode"
```

---

## Task 6: Update DialogStore to Support Host/Client Modes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStore.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStoreTest.kt`

### Step 1: Add new intents for Host/Join

```kotlin
sealed class Intent {
    data class StartSoloGame(val scenario: Scenario, val userRole: Role) : Intent()
    
    // NEW: Multiplayer intents
    data class HostGame(val scenario: Scenario, val userRole: Role) : Intent()
    data class JoinGame(val hostDeviceId: String, val userRole: Role) : Intent()
    
    data object Generate : Intent()
    data object LeaveGame : Intent()
}
```

### Step 2: Write tests for new intents

```kotlin
@Test
fun hostGameIntentSetsHostMode() = runTest {
    val session = LoopbackNetworkSession("local")
    val store = DialogStore(
        networkSession = session,
        aiProvider = MockAIProvider()
    )
    
    store.accept(DialogStore.Intent.HostGame(
        scenario = Scenario("test", "Test", "Test", emptyList()),
        userRole = Role("host", "Host", "The host")
    ))
    
    delay(100)
    
    val state = store.state.value
    assertEquals(SessionMode.HOST, state.mode)
}
```

### Step 3: Implement executor logic

(Updates to DialogStore.execute() method)

### Step 4: Commit

```bash
git add composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStore.kt
git add composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/domain/stores/DialogStoreTest.kt
git commit -m "feat(store): add HostGame and JoinGame intents for multiplayer"
```

---

## Task 7: Update BrainSDK with Multiplayer Methods

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/BrainSDK.kt`
- Test: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/BrainSDKTest.kt`

### Methods to Add:
- `hostGame(scenarioId: String, userRoleId: String)`
- `scanForHosts(): Flow<DiscoveredDevice>`
- `joinGame(hostDeviceId: String, userRoleId: String)`

### Step 1: Write failing tests

```kotlin
@Test
fun scanForHostsReturnsFlow() {
    val sdk = BrainSDK()
    val hosts = sdk.scanForHosts()
    assertNotNull(hosts)
}
```

### Step 2: Implement

### Step 3: Commit

---

## Task 8: Integration Test - Two Device Simulation

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/bablabs/bringabrainlanguage/integration/MultiplayerSimulationTest.kt`

### Test Scenario:
1. Create Host session with LoopbackNetworkSession (simulating host)
2. Create Client session that receives packets from a mock
3. Verify both receive same state after Generate

This validates the architecture without requiring real BLE hardware.

---

## Summary

After Phase 2, the SDK structure will be:

```
infrastructure/network/
├── LoopbackNetworkSession.kt     # Solo mode (Phase 1)
└── ble/
    ├── BleConstants.kt           # GATT UUIDs
    ├── PacketFragmenter.kt       # MTU handling
    ├── BleScanner.kt             # Device discovery
    ├── BleHostSession.kt         # Host/Central mode
    └── BleClientSession.kt       # Client/Peripheral mode
```

**Verification Checklist:**
- [ ] All unit tests pass
- [ ] Packet fragmentation/reassembly works
- [ ] Host and Client sessions implement NetworkSession
- [ ] DialogStore supports HOST and CLIENT modes
- [ ] BrainSDK exposes multiplayer methods

**Next Phase:** iOS 26 Foundation Model integration
