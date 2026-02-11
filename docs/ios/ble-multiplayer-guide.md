# iOS BLE Multiplayer Integration Guide

This guide explains how to integrate BLE multiplayer functionality into your iOS app using the BabLanguageSDK.

## Overview

The SDK handles game logic, state management, and AI generation. Your iOS app is responsible for:

1. **BLE Peripheral Mode** (hosting) - CoreBluetooth `CBPeripheralManager`
2. **BLE Central Mode** (joining) - SDK uses Kable internally
3. **Speech Recognition** - iOS Speech Framework
4. **Text-to-Speech** - AVSpeechSynthesizer
5. **UI Rendering** - SwiftUI

## Prerequisites

### Required Capabilities

Add to your Xcode project:

1. **Info.plist keys:**
```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Connect with nearby players for multiplayer games</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>Host multiplayer games for nearby players</string>
<key>NSSpeechRecognitionUsageDescription</key>
<string>Check pronunciation of spoken words</string>
<key>NSMicrophoneUsageDescription</key>
<string>Listen to your pronunciation for feedback</string>
```

2. **Background Modes** (optional, for background play):
   - Uses Bluetooth LE accessories

### Minimum Requirements

| Requirement | Version |
|-------------|---------|
| iOS | 15.0+ |
| Xcode | 15.0+ |
| Swift | 5.9+ |
| Bluetooth | 4.0+ (BLE) |

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Your iOS App                            │
├─────────────────┬─────────────────┬────────────────────────┤
│   SwiftUI Views │  BLE Manager   │   Speech Manager        │
│   (Lobby, Game) │  (Host/Join)   │   (Recognition, TTS)    │
├─────────────────┴─────────────────┴────────────────────────┤
│                     SDKObserver                             │
│              (Observes SDK state, bridges to UI)            │
├────────────────────────────────────────────────────────────┤
│                     BabLanguageSDK                          │
│     BrainSDK (Game logic, AI, State, Packet handling)      │
└────────────────────────────────────────────────────────────┘
```

## Implementation

### 1. SDK Observer (State Management)

```swift
import SwiftUI
import BabLanguageSDK

@MainActor
class SDKObserver: ObservableObject {
    let sdk: BrainSDK
    
    @Published var sessionState: SessionState
    @Published var lobbyPlayers: [LobbyPlayer] = []
    @Published var isAdvertising: Bool = false
    @Published var currentTurnPlayerId: String?
    @Published var pendingLine: DialogLine?
    @Published var committedHistory: [DialogLine] = []
    
    private var observationTasks: [Task<Void, Never>] = []
    
    init(sdk: BrainSDK = BrainSDK()) {
        self.sdk = sdk
        self.sessionState = sdk.state.value
        startObserving()
    }
    
    private func startObserving() {
        // Observe main state
        observationTasks.append(Task {
            for await state in sdk.state {
                self.sessionState = state
                self.lobbyPlayers = state.lobbyPlayers
                self.isAdvertising = state.isAdvertising
                self.currentTurnPlayerId = state.currentTurnPlayerId
                self.pendingLine = state.pendingLine
                self.committedHistory = state.committedHistory
            }
        })
        
        // Observe outgoing packets (for BLE transmission)
        observationTasks.append(Task {
            for await packet in sdk.outgoingPackets {
                await BLEManager.shared.sendPacket(packet)
            }
        })
    }
}
```

### 2. BLE Manager (CoreBluetooth)

```swift
import CoreBluetooth
import BabLanguageSDK

class BLEManager: NSObject, ObservableObject {
    static let shared = BLEManager()
    
    // Service and Characteristic UUIDs
    static let serviceUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABC")
    static let writeCharUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABD")
    static let notifyCharUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABE")
    
    // Managers
    private var peripheralManager: CBPeripheralManager?
    private var centralManager: CBCentralManager?
    
    // State
    @Published var isHosting = false
    @Published var isScanning = false
    @Published var connectedPeers: [CBPeripheral] = []
    
    // Characteristics
    private var writeCharacteristic: CBMutableCharacteristic?
    private var notifyCharacteristic: CBMutableCharacteristic?
    
    // SDK reference
    weak var sdk: BrainSDK?
    
    // MARK: - Hosting (Peripheral Mode)
    
    func startHosting(serviceName: String) {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }
    
    func stopHosting() {
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()
        peripheralManager = nil
        isHosting = false
    }
    
    private func setupPeripheralService() {
        // Create characteristics
        writeCharacteristic = CBMutableCharacteristic(
            type: Self.writeCharUUID,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        
        notifyCharacteristic = CBMutableCharacteristic(
            type: Self.notifyCharUUID,
            properties: [.notify],
            value: nil,
            permissions: [.readable]
        )
        
        // Create service
        let service = CBMutableService(type: Self.serviceUUID, primary: true)
        service.characteristics = [writeCharacteristic!, notifyCharacteristic!]
        
        peripheralManager?.add(service)
    }
    
    private func startAdvertising() {
        let advertisementData: [String: Any] = [
            CBAdvertisementDataLocalNameKey: "BabLanguage Game",
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID]
        ]
        peripheralManager?.startAdvertising(advertisementData)
        isHosting = true
    }
    
    // MARK: - Sending Data
    
    func sendPacket(_ packet: OutgoingPacket) async {
        guard let characteristic = notifyCharacteristic else { return }
        
        let data = packet.data
        
        // Fragment if needed (BLE MTU is typically 20-512 bytes)
        let mtu = 182  // Safe default
        let chunks = stride(from: 0, to: data.count, by: mtu).map {
            Data(data[$0..<min($0 + mtu, data.count)])
        }
        
        for chunk in chunks {
            peripheralManager?.updateValue(
                chunk,
                for: characteristic,
                onSubscribedCentrals: nil
            )
            // Small delay between chunks
            try? await Task.sleep(nanoseconds: 10_000_000)  // 10ms
        }
    }
}

// MARK: - CBPeripheralManagerDelegate

extension BLEManager: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            setupPeripheralService()
        case .poweredOff:
            isHosting = false
        default:
            break
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, 
                          didAdd service: CBService, 
                          error: Error?) {
        if error == nil {
            startAdvertising()
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          central: CBCentral,
                          didSubscribeTo characteristic: CBCharacteristic) {
        // Client connected
        let peerId = central.identifier.uuidString
        sdk?.onPeerConnected(peerId: peerId, peerName: "Player")
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          central: CBCentral,
                          didUnsubscribeFrom characteristic: CBCharacteristic) {
        // Client disconnected
        let peerId = central.identifier.uuidString
        sdk?.onPeerDisconnected(peerId: peerId)
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager,
                          didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let data = request.value {
                let peerId = request.central.identifier.uuidString
                sdk?.onDataReceived(fromPeerId: peerId, data: [UInt8](data))
            }
            peripheral.respond(to: request, withResult: .success)
        }
    }
}
```

### 3. Speech Recognition Manager

```swift
import Speech
import AVFoundation
import BabLanguageSDK

class SpeechManager: NSObject, ObservableObject {
    private let speechRecognizer = SFSpeechRecognizer()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    
    @Published var isListening = false
    @Published var recognizedText = ""
    
    // Request authorization
    func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
    }
    
    // Start listening for speech
    func startListening() throws {
        // Cancel any ongoing task
        recognitionTask?.cancel()
        recognitionTask = nil
        
        // Configure audio session
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.record, mode: .measurement)
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        
        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else { return }
        recognitionRequest.shouldReportPartialResults = true
        
        // Start recognition
        recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            if let result = result {
                self?.recognizedText = result.bestTranscription.formattedString
            }
            if error != nil || result?.isFinal == true {
                self?.stopListening()
            }
        }
        
        // Configure audio input
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            self.recognitionRequest?.append(buffer)
        }
        
        audioEngine.prepare()
        try audioEngine.start()
        isListening = true
    }
    
    func stopListening() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        isListening = false
    }
    
    // Compare spoken text to expected text
    func evaluatePronunciation(expected: String, spoken: String) -> PronunciationResult {
        let expectedWords = expected.lowercased().split(separator: " ").map(String.init)
        let spokenWords = spoken.lowercased().split(separator: " ").map(String.init)
        
        var errors: [WordError] = []
        var correctCount = 0
        
        for (index, expectedWord) in expectedWords.enumerated() {
            if index < spokenWords.count {
                let spokenWord = spokenWords[index]
                if expectedWord == spokenWord {
                    correctCount += 1
                } else {
                    errors.append(WordError(
                        word: expectedWord,
                        position: Int32(index),
                        expected: expectedWord,
                        heard: spokenWord
                    ))
                }
            } else {
                // Word not spoken
                errors.append(WordError(
                    word: expectedWord,
                    position: Int32(index),
                    expected: expectedWord,
                    heard: nil
                ))
            }
        }
        
        let accuracy = Float(correctCount) / Float(max(expectedWords.count, 1))
        
        return PronunciationResult(
            errorCount: Int32(errors.count),
            accuracy: accuracy,
            wordErrors: errors,
            skipped: false,
            duration: 0  // Set by caller
        )
    }
}
```

### 4. Lobby View

```swift
import SwiftUI
import BabLanguageSDK

struct LobbyView: View {
    @EnvironmentObject var observer: SDKObserver
    @StateObject private var bleManager = BLEManager.shared
    
    let scenario: Scenario
    let isHost: Bool
    
    var body: some View {
        VStack(spacing: 20) {
            // Scenario Info
            Text(scenario.name)
                .font(.title)
            Text(scenario.description_)
                .foregroundColor(.secondary)
            
            Divider()
            
            // Players List
            Text("Players")
                .font(.headline)
            
            ForEach(observer.lobbyPlayers, id: \.peerId) { player in
                HStack {
                    Circle()
                        .fill(player.isReady ? Color.green : Color.gray)
                        .frame(width: 12, height: 12)
                    
                    Text(player.displayName)
                    
                    Spacer()
                    
                    if let role = player.assignedRole {
                        Text(role.name)
                            .foregroundColor(.secondary)
                    } else {
                        Text("No role")
                            .foregroundColor(.red)
                    }
                }
                .padding(.horizontal)
            }
            
            if observer.lobbyPlayers.isEmpty {
                Text("Waiting for players...")
                    .foregroundColor(.secondary)
                    .padding()
            }
            
            Spacer()
            
            // Role Selection
            if isHost {
                VStack {
                    Text("Available Roles")
                        .font(.headline)
                    
                    ForEach(scenario.availableRoles, id: \.id) { role in
                        Button(role.name) {
                            // Assign role logic
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            
            // Start Button (Host only)
            if isHost {
                Button("Start Game") {
                    observer.sdk.startMultiplayerGame()
                }
                .buttonStyle(.borderedProminent)
                .disabled(!allPlayersReady)
            }
        }
        .padding()
        .onAppear {
            if isHost {
                let serviceName = observer.sdk.startHostAdvertising()
                bleManager.sdk = observer.sdk
                bleManager.startHosting(serviceName: serviceName)
            }
        }
        .onDisappear {
            if isHost {
                bleManager.stopHosting()
                observer.sdk.stopHostAdvertising()
            }
        }
    }
    
    private var allPlayersReady: Bool {
        !observer.lobbyPlayers.isEmpty && 
        observer.lobbyPlayers.allSatisfy { $0.isReady && $0.assignedRole != nil }
    }
}
```

### 5. Active Game View

```swift
import SwiftUI
import BabLanguageSDK

struct ActiveGameView: View {
    @EnvironmentObject var observer: SDKObserver
    @StateObject private var speechManager = SpeechManager()
    
    @State private var isRecording = false
    @State private var readStartTime: Date?
    
    var body: some View {
        VStack {
            // Theater View (committed lines)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(observer.committedHistory, id: \.id) { line in
                            TheaterLineView(line: line)
                                .id(line.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: observer.committedHistory.count) { _ in
                    if let lastId = observer.committedHistory.last?.id {
                        withAnimation {
                            proxy.scrollTo(lastId, anchor: .bottom)
                        }
                    }
                }
            }
            
            Divider()
            
            // Current Turn Area
            if isMyTurn, let line = observer.pendingLine {
                CurrentTurnView(
                    line: line,
                    isRecording: isRecording,
                    onStartReading: startReading,
                    onFinishReading: finishReading,
                    onSkip: skipLine
                )
            } else if let currentPlayerId = observer.currentTurnPlayerId,
                      let player = observer.lobbyPlayers.first(where: { $0.peerId == currentPlayerId }) {
                WaitingView(playerName: player.displayName)
            }
        }
    }
    
    private var isMyTurn: Bool {
        observer.currentTurnPlayerId == observer.sdk.state.value.localPeerId
    }
    
    private func startReading() {
        readStartTime = Date()
        isRecording = true
        try? speechManager.startListening()
    }
    
    private func finishReading() {
        speechManager.stopListening()
        isRecording = false
        
        guard let line = observer.pendingLine else { return }
        
        let duration = readStartTime.map { Date().timeIntervalSince($0) * 1000 } ?? 0
        
        let result = speechManager.evaluatePronunciation(
            expected: line.textNative,
            spoken: speechManager.recognizedText
        )
        
        // Create result with duration
        let finalResult = PronunciationResult(
            errorCount: result.errorCount,
            accuracy: result.accuracy,
            wordErrors: result.wordErrors,
            skipped: false,
            duration: Int64(duration)
        )
        
        observer.sdk.completeLine(lineId: line.id, result: finalResult)
        speechManager.recognizedText = ""
    }
    
    private func skipLine() {
        guard let line = observer.pendingLine else { return }
        observer.sdk.skipLine(lineId: line.id)
    }
}

struct CurrentTurnView: View {
    let line: DialogLine
    let isRecording: Bool
    let onStartReading: () -> Void
    let onFinishReading: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Your turn to read:")
                .font(.headline)
            
            // Target language text (what to read)
            Text(line.textNative)
                .font(.title2)
                .fontWeight(.medium)
                .padding()
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
            
            // Translation (helper)
            Text(line.textTranslated)
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            // Controls
            HStack(spacing: 20) {
                Button(action: onSkip) {
                    Label("Skip", systemImage: "forward.fill")
                }
                .buttonStyle(.bordered)
                
                if isRecording {
                    Button(action: onFinishReading) {
                        Label("Done", systemImage: "checkmark.circle.fill")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
                } else {
                    Button(action: onStartReading) {
                        Label("Read Aloud", systemImage: "mic.fill")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding()
    }
}

struct WaitingView: View {
    let playerName: String
    
    var body: some View {
        VStack {
            ProgressView()
                .padding()
            Text("\(playerName) is reading...")
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

struct TheaterLineView: View {
    let line: DialogLine
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(line.roleName)
                .font(.caption)
                .foregroundColor(.secondary)
            
            Text(line.textNative)
                .font(.body)
            
            Text(line.textTranslated)
                .font(.caption)
                .foregroundColor(.secondary)
            
            // Show pronunciation accuracy if available
            if let result = line.pronunciationResult {
                HStack {
                    Image(systemName: result.accuracy > 0.8 ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                        .foregroundColor(result.accuracy > 0.8 ? .green : .orange)
                    Text("\(Int(result.accuracy * 100))% accuracy")
                        .font(.caption2)
                }
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}
```

### 6. Session Summary View

```swift
import SwiftUI
import BabLanguageSDK

struct SessionSummaryView: View {
    let summary: SessionSummary
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack {
                        Text("Session Complete!")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text(summary.scenarioName)
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                    
                    // Stats
                    HStack(spacing: 20) {
                        StatBox(title: "Lines", value: "\(summary.totalLines)")
                        StatBox(title: "Accuracy", value: "\(Int(summary.overallAccuracy * 100))%")
                        StatBox(title: "Duration", value: formatDuration(summary.duration))
                    }
                    
                    // Leaderboard
                    VStack(alignment: .leading) {
                        Text("Rankings")
                            .font(.headline)
                        
                        ForEach(summary.playerRankings, id: \.playerId) { ranking in
                            HStack {
                                Text("#\(ranking.rank)")
                                    .fontWeight(.bold)
                                    .frame(width: 30)
                                
                                Text(ranking.displayName)
                                
                                Spacer()
                                
                                Text("+\(summary.xpEarned[ranking.playerId] ?? 0) XP")
                                    .foregroundColor(.green)
                                
                                if let highlight = ranking.highlight {
                                    Text(highlight)
                                        .font(.caption)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 2)
                                        .background(Color.yellow.opacity(0.3))
                                        .cornerRadius(4)
                                }
                            }
                            .padding(.vertical, 8)
                        }
                    }
                    .padding()
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                    
                    // Achievements
                    if !summary.achievementsUnlocked.isEmpty {
                        VStack(alignment: .leading) {
                            Text("Achievements Unlocked!")
                                .font(.headline)
                            
                            ForEach(summary.achievementsUnlocked, id: \.id) { achievement in
                                HStack {
                                    Image(systemName: "trophy.fill")
                                        .foregroundColor(.yellow)
                                    Text(achievement.name)
                                    Spacer()
                                    Text(achievement.description_)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .padding()
                        .background(Color(.secondarySystemBackground))
                        .cornerRadius(12)
                    }
                    
                    // Vocabulary Learned
                    if !summary.vocabularyLearned.isEmpty {
                        VStack(alignment: .leading) {
                            Text("New Vocabulary")
                                .font(.headline)
                            
                            ForEach(summary.vocabularyLearned, id: \.id) { word in
                                HStack {
                                    Text(word.word)
                                        .fontWeight(.medium)
                                    Text("→")
                                    Text(word.translation)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .padding()
                        .background(Color(.secondarySystemBackground))
                        .cornerRadius(12)
                    }
                    
                    // Done Button
                    Button("Done") {
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.top)
                }
                .padding()
            }
            .navigationBarHidden(true)
        }
    }
    
    private func formatDuration(_ duration: Duration) -> String {
        let seconds = duration.inWholeSeconds
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return "\(minutes):\(String(format: "%02d", remainingSeconds))"
    }
}

struct StatBox: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack {
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}
```

### 7. Lobby State Sync (Pre-Session)

The SDK supports a **Lobby Phase** before the game starts, where the Host selects a Scenario and Difficulty and all connected Clients see these changes in real-time.

#### How It Works

```
Host selects scenario → setLobbyScenario("coffee-shop")
  → Updates local SessionState.lobbyState
  → Broadcasts LobbyUpdatePacket to all Clients via BLE

Client receives packet → onDataReceived()
  → SDK Reducer applies LobbyUpdate → SessionState.lobbyState updated
  → SDKObserver publishes change → SwiftUI re-renders
```

#### SDKObserver Integration

The `lobbyState` is embedded in `SessionState`, so the existing `for await state in sdk.state` loop picks it up automatically:

```swift
@MainActor
class SDKObserver: ObservableObject {
    let sdk: BrainSDK
    
    @Published var sessionState: SessionState
    @Published var lobbyState: LobbyState  // New
    
    // ... existing properties ...
    
    private func startObserving() {
        observationTasks.append(Task {
            for await state in sdk.state {
                self.sessionState = state
                self.lobbyState = state.lobbyState  // Automatically synced
                self.lobbyPlayers = state.lobbyPlayers
                // ... existing assignments ...
            }
        })
    }
}
```

#### Host: Lobby Configuration View

```swift
struct LobbyConfigView: View {
    @EnvironmentObject var observer: SDKObserver
    
    let scenarios = ["coffee-shop", "the-heist", "first-date"]
    let difficulties = ["easy", "normal", "hard"]
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Game Setup")
                .font(.title)
            
            // Scenario picker
            Picker("Scenario", selection: scenarioBinding) {
                ForEach(scenarios, id: \.self) { Text($0) }
            }
            
            // Difficulty picker
            Picker("Difficulty", selection: difficultyBinding) {
                ForEach(difficulties, id: \.self) { Text($0) }
            }
            
            // Connected players
            Section("Players (\(observer.lobbyState.connectedPlayers.count))") {
                ForEach(observer.lobbyState.connectedPlayers, id: \.playerId) { player in
                    Text(player.displayName)
                }
            }
            
            // Start game
            Button("Start Game") {
                observer.sdk.startLobbyGame()
            }
            .disabled(observer.lobbyState.selectedScenarioId.isEmpty())
        }
    }
    
    private var scenarioBinding: Binding<String> {
        Binding(
            get: { observer.lobbyState.selectedScenarioId },
            set: { observer.sdk.setLobbyScenario(scenarioId: $0) }
        )
    }
    
    private var difficultyBinding: Binding<String> {
        Binding(
            get: { observer.lobbyState.difficultyLevel },
            set: { observer.sdk.setLobbyDifficulty(difficultyLevel: $0) }
        )
    }
}
```

#### Client: Waiting View

Clients don't need to call any special methods — the SDK automatically updates `lobbyState` when a `LobbyUpdatePacket` arrives:

```swift
struct ClientLobbyView: View {
    @EnvironmentObject var observer: SDKObserver
    
    var body: some View {
        VStack(spacing: 16) {
            if observer.lobbyState.selectedScenarioId.isEmpty() {
                Text("Waiting for host to select a scenario...")
                    .foregroundColor(.secondary)
            } else {
                Text("Scenario: \(observer.lobbyState.selectedScenarioId)")
                    .font(.headline)
                Text("Difficulty: \(observer.lobbyState.difficultyLevel)")
                    .foregroundColor(.secondary)
            }
            
            ProgressView()
                .padding()
            Text("Waiting for host to start...")
        }
    }
}
```

#### Detecting Game Start

Check `currentPhase` to transition from lobby to active game:

```swift
// In your navigation logic
.onChange(of: observer.sessionState.currentPhase) { phase in
    if phase == .active {
        // Navigate to Theater / Active Game View
        navigateToGame()
    }
}
```

#### New SDK Methods

| Method | Side | Description |
|--------|------|-------------|
| `setLobbyScenario(scenarioId:)` | Host | Set scenario, broadcasts to clients |
| `setLobbyDifficulty(difficultyLevel:)` | Host | Set difficulty, broadcasts to clients |
| `onClientJoined(profile:)` | Host | Register a client player profile |
| `startLobbyGame()` | Host | Validate & start game, broadcasts to clients |

#### New Packet Types

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `LOBBY_UPDATE` | Host → Clients | Full `LobbyState` snapshot on any change |
| `CLIENT_JOIN` | Client → Host | Client announces presence with `PlayerProfile` |
| `START_GAME` | Host → Clients | Signal transition to active game |

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| BLE advertising not starting | Check Bluetooth permission in Settings |
| Speech recognition fails | Ensure microphone permission granted |
| Connection drops frequently | Reduce distance between devices |
| Packets lost | SDK handles fragmentation; check MTU size |

### Debug Logging

```swift
// Enable SDK debug logging
BrainSDK.enableDebugLogging(true)

// BLE state logging
print("Peripheral state: \(peripheralManager?.state.rawValue ?? -1)")
print("Connected centrals: \(connectedCentrals.count)")
```

## Next Steps

- See [BLE Multiplayer Design](../plans/2026-02-06-ble-multiplayer-design.md) for architecture details
- See [SDK Architecture](./sdk-architecture.md) for internal structure
- See [API Reference](./api-reference.md) for complete method documentation
