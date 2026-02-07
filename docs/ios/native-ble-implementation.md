# iOS Native BLE Implementation Guide

This guide explains how to implement the native BLE layer on iOS to connect with the BabLanguageSDK for multiplayer functionality.

## Overview

The SDK handles all game logic, state management, and packet serialization. Your iOS app provides the BLE transport layer using CoreBluetooth.

```
┌─────────────────────────────────────────────────────────────┐
│                      Your iOS App                            │
├─────────────────────────────────────────────────────────────┤
│                     BLEManager                               │
│  CBPeripheralManager (Host) ←→ CBCentralManager (Client)    │
├─────────────────────────────────────────────────────────────┤
│                     BabLanguageSDK                           │
│  sdk.onPeerConnected() / sdk.onDataReceived()               │
│  sdk.outgoingPackets → subscribe and transmit               │
└─────────────────────────────────────────────────────────────┘
```

## SDK ↔ Native BLE Contract

### SDK Provides

| Property/Method | Description |
|-----------------|-------------|
| `startHostAdvertising(): String` | Returns service name (e.g., "bab-game-player-12345") |
| `stopHostAdvertising()` | Stop advertising |
| `onPeerConnected(peerId, peerName)` | Call when BLE connection established |
| `onPeerDisconnected(peerId)` | Call when peer disconnects |
| `onDataReceived(fromPeerId, data)` | Call when BLE receives bytes |
| `outgoingPackets: Flow<OutgoingPacket>` | Subscribe to transmit packets |

### Native App Provides

| Responsibility | Implementation |
|----------------|----------------|
| BLE Advertising (GATT Server) | `CBPeripheralManager` |
| BLE Scanning (GATT Client) | SDK uses Kable internally |
| Raw byte transmission | Write to BLE characteristics |
| Connection management | Handle connect/disconnect events |

## Implementation

### 1. BLE Constants

```swift
import CoreBluetooth

enum BLEConstants {
    static let serviceUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABC")
    static let writeCharacteristicUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABD")
    static let notifyCharacteristicUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABE")
    
    static let maxMTU = 512
    static let defaultMTU = 20
}
```

### 2. BLE Manager (Host Mode)

```swift
import CoreBluetooth
import BabLanguageSDK

@MainActor
class BLEHostManager: NSObject, ObservableObject {
    private var peripheralManager: CBPeripheralManager?
    private var service: CBMutableService?
    private var writeCharacteristic: CBMutableCharacteristic?
    private var notifyCharacteristic: CBMutableCharacteristic?
    
    private var connectedCentrals: [CBCentral: String] = [:] // central -> peerId
    private var outgoingPacketTask: Task<Void, Never>?
    
    let sdk: BrainSDK
    
    @Published var isAdvertising = false
    @Published var connectedPeerCount = 0
    
    init(sdk: BrainSDK) {
        self.sdk = sdk
        super.init()
    }
    
    func startAdvertising() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: .main)
    }
    
    func stopAdvertising() {
        peripheralManager?.stopAdvertising()
        peripheralManager = nil
        sdk.stopHostAdvertising()
        isAdvertising = false
    }
    
    private func setupService() {
        // Write characteristic (clients write to host)
        writeCharacteristic = CBMutableCharacteristic(
            type: BLEConstants.writeCharacteristicUUID,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        
        // Notify characteristic (host broadcasts to clients)
        notifyCharacteristic = CBMutableCharacteristic(
            type: BLEConstants.notifyCharacteristicUUID,
            properties: [.notify, .read],
            value: nil,
            permissions: [.readable]
        )
        
        service = CBMutableService(type: BLEConstants.serviceUUID, primary: true)
        service?.characteristics = [writeCharacteristic!, notifyCharacteristic!]
        
        peripheralManager?.add(service!)
    }
    
    private func startAdvertisingService() {
        let serviceName = sdk.startHostAdvertising()
        
        peripheralManager?.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [BLEConstants.serviceUUID],
            CBAdvertisementDataLocalNameKey: serviceName
        ])
        
        isAdvertising = true
        subscribeToOutgoingPackets()
    }
    
    private func subscribeToOutgoingPackets() {
        outgoingPacketTask = Task {
            for await packet in sdk.outgoingPackets {
                await sendPacket(packet)
            }
        }
    }
    
    private func sendPacket(_ packet: OutgoingPacket) async {
        guard let notifyCharacteristic = notifyCharacteristic else { return }
        
        let data = Data(packet.data.toByteArray())
        
        if let targetPeerId = packet.targetPeerId {
            // Unicast to specific peer
            if let central = connectedCentrals.first(where: { $0.value == targetPeerId })?.key {
                peripheralManager?.updateValue(data, for: notifyCharacteristic, onSubscribedCentrals: [central])
            }
        } else {
            // Broadcast to all
            peripheralManager?.updateValue(data, for: notifyCharacteristic, onSubscribedCentrals: nil)
        }
    }
}

extension BLEHostManager: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            setupService()
        case .poweredOff:
            stopAdvertising()
        default:
            break
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if error == nil {
            startAdvertisingService()
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        let peerId = "peer-\(central.identifier.uuidString.prefix(8))"
        connectedCentrals[central] = peerId
        connectedPeerCount = connectedCentrals.count
        
        // Notify SDK
        sdk.onPeerConnected(peerId: peerId, peerName: "Player \(connectedPeerCount)")
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        if let peerId = connectedCentrals[central] {
            sdk.onPeerDisconnected(peerId: peerId)
            connectedCentrals.removeValue(forKey: central)
            connectedPeerCount = connectedCentrals.count
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let data = request.value,
               let peerId = connectedCentrals[request.central] {
                // Forward to SDK
                sdk.onDataReceived(fromPeerId: peerId, data: KotlinByteArray(data))
            }
            peripheral.respond(to: request, withResult: .success)
        }
    }
}
```

### 3. Kotlin ByteArray ↔ Swift Data Conversion

```swift
extension KotlinByteArray {
    convenience init(_ data: Data) {
        self.init(size: Int32(data.count))
        data.enumerated().forEach { index, byte in
            self.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
    }
    
    func toData() -> Data {
        var bytes = [UInt8]()
        for i in 0..<size {
            bytes.append(UInt8(bitPattern: get(index: i)))
        }
        return Data(bytes)
    }
}

extension Data {
    init(_ kotlinByteArray: KotlinByteArray) {
        self = kotlinByteArray.toData()
    }
}
```

### 4. Integration with SwiftUI

```swift
import SwiftUI
import BabLanguageSDK

struct HostLobbyView: View {
    @StateObject private var bleManager: BLEHostManager
    @StateObject private var sdkObserver: SDKObserver
    
    init(sdk: BrainSDK) {
        let observer = SDKObserver(sdk: sdk)
        _sdkObserver = StateObject(wrappedValue: observer)
        _bleManager = StateObject(wrappedValue: BLEHostManager(sdk: sdk))
    }
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Hosting Game")
                .font(.largeTitle)
            
            if bleManager.isAdvertising {
                Text("Waiting for players...")
                    .foregroundColor(.secondary)
                
                Text("\(bleManager.connectedPeerCount) player(s) connected")
                    .font(.headline)
            }
            
            // Show lobby players from SDK state
            ForEach(sdkObserver.lobbyPlayers, id: \.peerId) { player in
                LobbyPlayerRow(player: player, onAssignRole: { roleId in
                    sdkObserver.sdk.assignRole(playerId: player.peerId, roleId: roleId)
                })
            }
            
            Button("Start Game") {
                sdkObserver.sdk.startMultiplayerGame()
            }
            .disabled(!allPlayersReady)
        }
        .onAppear {
            bleManager.startAdvertising()
        }
        .onDisappear {
            bleManager.stopAdvertising()
        }
    }
    
    private var allPlayersReady: Bool {
        sdkObserver.lobbyPlayers.allSatisfy { $0.isReady && $0.assignedRole != nil }
    }
}
```

## Packet Fragmentation

BLE has MTU limits (typically 20-512 bytes). The SDK includes `PacketFragmenter` for handling large packets.

### Fragment Header Format

```
Bytes 0-1: Packet ID (UInt16)
Byte 2:    Fragment index (UInt8)
Byte 3:    Total fragments (UInt8)
Bytes 4+:  Payload data
```

### Reassembly on Receive

```swift
class PacketReassembler {
    private var fragments: [UInt16: [UInt8: Data]] = [:]
    
    func addFragment(_ data: Data) -> Data? {
        guard data.count >= 4 else { return nil }
        
        let packetId = UInt16(data[0]) << 8 | UInt16(data[1])
        let fragmentIndex = data[2]
        let totalFragments = data[3]
        let payload = data.dropFirst(4)
        
        if fragments[packetId] == nil {
            fragments[packetId] = [:]
        }
        fragments[packetId]![fragmentIndex] = Data(payload)
        
        // Check if complete
        if fragments[packetId]!.count == Int(totalFragments) {
            var completeData = Data()
            for i in 0..<totalFragments {
                if let fragment = fragments[packetId]![i] {
                    completeData.append(fragment)
                }
            }
            fragments.removeValue(forKey: packetId)
            return completeData
        }
        
        return nil
    }
}
```

## Error Handling

### Connection Loss

```swift
extension BLEHostManager {
    func handleConnectionLoss(for central: CBCentral) {
        if let peerId = connectedCentrals[central] {
            sdk.onPeerDisconnected(peerId: peerId)
            connectedCentrals.removeValue(forKey: central)
            
            // Attempt reconnection for 30 seconds
            Task {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                // If still not reconnected, SDK will handle cleanup
            }
        }
    }
}
```

### Bluetooth State Changes

```swift
func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
    switch peripheral.state {
    case .poweredOn:
        setupService()
    case .poweredOff:
        // Notify all connected peers
        for peerId in connectedCentrals.values {
            sdk.onPeerDisconnected(peerId: peerId)
        }
        connectedCentrals.removeAll()
        isAdvertising = false
    case .unauthorized:
        // Show permission request
        break
    case .unsupported:
        // Show "BLE not supported" message
        break
    default:
        break
    }
}
```

## Best Practices

### 1. Background Mode Support

Add to `Info.plist`:
```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-peripheral</string>
    <string>bluetooth-central</string>
</array>
```

### 2. Power Optimization

```swift
// Use low-latency connection parameters for gaming
peripheralManager?.setDesiredConnectionLatency(.low, for: central)

// Batch updates when possible
var pendingUpdates: [Data] = []
Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
    self.flushPendingUpdates()
}
```

### 3. Connection Quality Monitoring

```swift
func updateConnectionQuality(for central: CBCentral, rssi: NSNumber) {
    let quality: ConnectionQuality
    switch rssi.intValue {
    case -50...0:
        quality = .excellent
    case -70..<(-50):
        quality = .good
    case -85..<(-70):
        quality = .fair
    default:
        quality = .poor
    }
    
    // Update UI or SDK state
}
```

### 4. Graceful Disconnection

```swift
func disconnectGracefully() {
    // Send goodbye packet first
    let goodbyePacket = createGoodbyePacket()
    sendPacket(goodbyePacket)
    
    // Wait for transmission
    Task {
        try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
        stopAdvertising()
    }
}
```

## Testing

### Simulator Limitations

CoreBluetooth peripheral mode does not work in the iOS Simulator. Test on physical devices.

### Mock BLE for Unit Tests

```swift
protocol BLEManagerProtocol {
    func startAdvertising()
    func stopAdvertising()
    func sendData(_ data: Data, to peerId: String?)
}

class MockBLEManager: BLEManagerProtocol {
    var sentPackets: [(Data, String?)] = []
    
    func startAdvertising() {}
    func stopAdvertising() {}
    
    func sendData(_ data: Data, to peerId: String?) {
        sentPackets.append((data, peerId))
    }
}
```

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Advertising not starting | Bluetooth off or unauthorized | Check `peripheralManagerDidUpdateState` |
| Clients can't discover | Wrong service UUID | Verify UUID matches SDK expectations |
| Data not received | MTU exceeded | Use PacketFragmenter |
| Frequent disconnects | Interference or distance | Reduce transmission frequency |
| Slow updates | High latency mode | Set `.low` latency |

## Related Documentation

- [BLE Multiplayer Integration Guide](./ble-multiplayer-guide.md)
- [Gamification System Guide](./gamification-guide.md)
- [SKIE Integration Guide](./integration-guide.md)
