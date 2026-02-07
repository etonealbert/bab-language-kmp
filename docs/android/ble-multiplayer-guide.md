# Android BLE Multiplayer Integration Guide

This guide explains how to integrate BLE multiplayer functionality into your Android app using the BabLanguageSDK.

## Overview

The SDK handles game logic, state management, and AI generation. Your Android app is responsible for:

1. **BLE Peripheral Mode** (hosting) - Android BLE GATT Server
2. **BLE Central Mode** (joining) - SDK uses Kable internally
3. **Speech Recognition** - Android SpeechRecognizer
4. **Text-to-Speech** - Android TextToSpeech
5. **UI Rendering** - Jetpack Compose

## Prerequisites

### Required Permissions

Add to `AndroidManifest.xml`:

```xml
<!-- Bluetooth permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Location (required for BLE scanning on older Android) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Audio/Speech -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Features -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### Minimum Requirements

| Requirement | Version |
|-------------|---------|
| Android | API 24+ (Android 7.0) |
| Target SDK | 34+ |
| Kotlin | 1.9+ |
| Bluetooth | 4.0+ (BLE) |

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    Your Android App                         │
├─────────────────┬─────────────────┬────────────────────────┤
│ Compose UI      │  BLE Manager   │   Speech Manager        │
│ (Lobby, Game)   │  (Host/Join)   │   (Recognition, TTS)    │
├─────────────────┴─────────────────┴────────────────────────┤
│                     ViewModel                               │
│              (Observes SDK state, bridges to UI)            │
├────────────────────────────────────────────────────────────┤
│                     BabLanguageSDK                          │
│     BrainSDK (Game logic, AI, State, Packet handling)      │
└────────────────────────────────────────────────────────────┘
```

## Implementation

### 1. SDK ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bablabs.bringabrainlanguage.BrainSDK
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameViewModel(
    private val sdk: BrainSDK = BrainSDK()
) : ViewModel() {
    
    val sessionState: StateFlow<SessionState> = sdk.state
    
    val lobbyPlayers: StateFlow<List<LobbyPlayer>> = sdk.state
        .map { it.lobbyPlayers }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val currentTurnPlayerId: StateFlow<String?> = sdk.state
        .map { it.currentTurnPlayerId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val pendingLine: StateFlow<DialogLine?> = sdk.state
        .map { it.pendingLine }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val committedHistory: StateFlow<List<DialogLine>> = sdk.state
        .map { it.committedHistory }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val isMyTurn: StateFlow<Boolean> = sdk.state
        .map { it.currentTurnPlayerId == it.localPeerId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // Forward outgoing packets to BLE manager
    init {
        viewModelScope.launch {
            sdk.outgoingPackets.collect { packet ->
                BLEManager.getInstance().sendPacket(packet)
            }
        }
    }
    
    // Game lifecycle
    fun startHostAdvertising(): String = sdk.startHostAdvertising()
    fun stopHostAdvertising() = sdk.stopHostAdvertising()
    fun hostGame(scenarioId: String, roleId: String) = sdk.hostGame(scenarioId, roleId)
    fun joinGame(hostId: String, roleId: String) = sdk.joinGame(hostId, roleId)
    fun startMultiplayerGame() = sdk.startMultiplayerGame()
    
    // Turn management
    fun completeLine(lineId: String, result: PronunciationResult) {
        sdk.completeLine(lineId, result)
    }
    
    fun skipLine(lineId: String) {
        sdk.skipLine(lineId)
    }
    
    // Peer callbacks (called by BLE manager)
    fun onPeerConnected(peerId: String, peerName: String) {
        sdk.onPeerConnected(peerId, peerName)
    }
    
    fun onPeerDisconnected(peerId: String) {
        sdk.onPeerDisconnected(peerId)
    }
    
    fun onDataReceived(fromPeerId: String, data: ByteArray) {
        sdk.onDataReceived(fromPeerId, data)
    }
}
```

### 2. BLE Manager (GATT Server)

```kotlin
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BLEManager private constructor(private val context: Context) {
    
    companion object {
        // Service and Characteristic UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789ABC")
        val WRITE_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789ABD")
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789ABE")
        
        @Volatile
        private var INSTANCE: BLEManager? = null
        
        fun initialize(context: Context) {
            INSTANCE = BLEManager(context.applicationContext)
        }
        
        fun getInstance(): BLEManager = INSTANCE 
            ?: throw IllegalStateException("BLEManager not initialized")
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    
    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting
    
    private val _connectedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDevice>> = _connectedDevices
    
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    
    // Callback reference
    var viewModel: GameViewModel? = null
    
    // MARK: - Hosting (GATT Server)
    
    fun startHosting(serviceName: String) {
        setupGattServer()
        startAdvertising(serviceName)
    }
    
    fun stopHosting() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        _isHosting.value = false
    }
    
    private fun setupGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        // Create service
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // Write characteristic (client → host)
        val writeCharacteristic = BluetoothGattCharacteristic(
            WRITE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or 
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // Notify characteristic (host → client)
        notifyCharacteristic = BluetoothGattCharacteristic(
            NOTIFY_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_WRITE
            ))
        }
        
        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)
        
        gattServer?.addService(service)
    }
    
    private fun startAdvertising(serviceName: String) {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .build()
        
        advertiser?.startAdvertising(settings, data, advertiseCallback)
        _isHosting.value = true
    }
    
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
    }
    
    // MARK: - Send Data
    
    fun sendPacket(packet: OutgoingPacket) {
        val characteristic = notifyCharacteristic ?: return
        val data = packet.data
        
        // Fragment if needed
        val mtu = 182
        val chunks = data.toList().chunked(mtu).map { it.toByteArray() }
        
        val devices = if (packet.targetPeerId != null) {
            _connectedDevices.value.filter { it.address == packet.targetPeerId }
        } else {
            _connectedDevices.value
        }
        
        for (device in devices) {
            for (chunk in chunks) {
                characteristic.value = chunk
                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                Thread.sleep(10) // Small delay between chunks
            }
        }
    }
    
    // MARK: - Callbacks
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevices.value = _connectedDevices.value + device
                    viewModel?.onPeerConnected(device.address, device.name ?: "Player")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevices.value = _connectedDevices.value - device
                    viewModel?.onPeerDisconnected(device.address)
                }
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == WRITE_CHAR_UUID) {
                viewModel?.onDataReceived(device.address, value)
            }
            
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // Handle notification subscription
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _isHosting.value = true
        }
        
        override fun onStartFailure(errorCode: Int) {
            _isHosting.value = false
        }
    }
}
```

### 3. Speech Recognition Manager

```kotlin
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpeechManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
    }
    
    fun startListening(languageCode: String = "es-ES") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        speechRecognizer?.startListening(intent)
        _isListening.value = true
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }
    
    fun evaluatePronunciation(expected: String, spoken: String): PronunciationResult {
        val expectedWords = expected.lowercase().split(" ")
        val spokenWords = spoken.lowercase().split(" ")
        
        val errors = mutableListOf<WordError>()
        var correctCount = 0
        
        expectedWords.forEachIndexed { index, expectedWord ->
            if (index < spokenWords.size) {
                val spokenWord = spokenWords[index]
                if (expectedWord == spokenWord) {
                    correctCount++
                } else {
                    errors.add(WordError(
                        word = expectedWord,
                        position = index,
                        expected = expectedWord,
                        heard = spokenWord
                    ))
                }
            } else {
                errors.add(WordError(
                    word = expectedWord,
                    position = index,
                    expected = expectedWord,
                    heard = null
                ))
            }
        }
        
        val accuracy = correctCount.toFloat() / expectedWords.size.coerceAtLeast(1)
        
        return PronunciationResult(
            errorCount = errors.size,
            accuracy = accuracy,
            wordErrors = errors,
            skipped = false,
            duration = 0 // Set by caller
        )
    }
    
    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            _recognizedText.value = matches?.firstOrNull() ?: ""
            _isListening.value = false
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            _recognizedText.value = matches?.firstOrNull() ?: ""
        }
        
        override fun onError(error: Int) {
            _error.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                else -> "Recognition error: $error"
            }
            _isListening.value = false
        }
        
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
```

### 4. Compose UI - Lobby Screen

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LobbyScreen(
    viewModel: GameViewModel,
    scenario: Scenario,
    isHost: Boolean,
    onGameStart: () -> Unit
) {
    val lobbyPlayers by viewModel.lobbyPlayers.collectAsState()
    
    DisposableEffect(isHost) {
        if (isHost) {
            val serviceName = viewModel.startHostAdvertising()
            BLEManager.getInstance().viewModel = viewModel
            BLEManager.getInstance().startHosting(serviceName)
        }
        
        onDispose {
            if (isHost) {
                BLEManager.getInstance().stopHosting()
                viewModel.stopHostAdvertising()
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scenario info
        Text(
            text = scenario.name,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = scenario.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Players list
        Text(
            text = "Players",
            style = MaterialTheme.typography.titleMedium
        )
        
        if (lobbyPlayers.isEmpty()) {
            Text(
                text = "Waiting for players...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(lobbyPlayers) { player ->
                    PlayerRow(player = player)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Start button (host only)
        if (isHost) {
            val allReady = lobbyPlayers.isNotEmpty() && 
                lobbyPlayers.all { it.isReady && it.assignedRole != null }
            
            Button(
                onClick = {
                    viewModel.startMultiplayerGame()
                    onGameStart()
                },
                enabled = allReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Game")
            }
        }
    }
}

@Composable
fun PlayerRow(player: LobbyPlayer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ready indicator
        Surface(
            modifier = Modifier.size(12.dp),
            shape = MaterialTheme.shapes.small,
            color = if (player.isReady) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.surfaceVariant
        ) {}
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Player name
        Text(
            text = player.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        
        // Role
        Text(
            text = player.assignedRole?.name ?: "No role",
            style = MaterialTheme.typography.bodyMedium,
            color = if (player.assignedRole != null) 
                MaterialTheme.colorScheme.onSurfaceVariant
            else 
                MaterialTheme.colorScheme.error
        )
    }
}
```

### 5. Compose UI - Active Game Screen

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ActiveGameScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    val committedHistory by viewModel.committedHistory.collectAsState()
    val pendingLine by viewModel.pendingLine.collectAsState()
    val isMyTurn by viewModel.isMyTurn.collectAsState()
    val lobbyPlayers by viewModel.lobbyPlayers.collectAsState()
    val currentTurnPlayerId by viewModel.currentTurnPlayerId.collectAsState()
    
    val speechManager = remember { SpeechManager(context) }
    val isListening by speechManager.isListening.collectAsState()
    val recognizedText by speechManager.recognizedText.collectAsState()
    
    var readStartTime by remember { mutableStateOf<Long?>(null) }
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new lines added
    LaunchedEffect(committedHistory.size) {
        if (committedHistory.isNotEmpty()) {
            listState.animateScrollToItem(committedHistory.size - 1)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            speechManager.release()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Theater view (committed lines)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(committedHistory) { line ->
                TheaterLineCard(line = line)
            }
        }
        
        Divider()
        
        // Current turn area
        if (isMyTurn && pendingLine != null) {
            CurrentTurnCard(
                line = pendingLine!!,
                isRecording = isListening,
                recognizedText = recognizedText,
                onStartReading = {
                    readStartTime = System.currentTimeMillis()
                    speechManager.startListening()
                },
                onFinishReading = {
                    speechManager.stopListening()
                    val duration = readStartTime?.let { System.currentTimeMillis() - it } ?: 0
                    val result = speechManager.evaluatePronunciation(
                        expected = pendingLine!!.textNative,
                        spoken = recognizedText
                    ).copy(duration = duration)
                    viewModel.completeLine(pendingLine!!.id, result)
                },
                onSkip = {
                    viewModel.skipLine(pendingLine!!.id)
                }
            )
        } else {
            // Waiting for other player
            val currentPlayer = lobbyPlayers.find { it.peerId == currentTurnPlayerId }
            WaitingCard(playerName = currentPlayer?.displayName ?: "Player")
        }
    }
}

@Composable
fun TheaterLineCard(line: DialogLine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = line.roleName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = line.textNative,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Text(
                text = line.textTranslated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Pronunciation accuracy
            line.pronunciationResult?.let { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = if (result.accuracy > 0.8f) 
                            Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (result.accuracy > 0.8f) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${(result.accuracy * 100).toInt()}% accuracy",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentTurnCard(
    line: DialogLine,
    isRecording: Boolean,
    recognizedText: String,
    onStartReading: () -> Unit,
    onFinishReading: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your turn to read:",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Target text
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = line.textNative,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Translation
            Text(
                text = line.textTranslated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            // Recognized text (while recording)
            if (isRecording && recognizedText.isNotEmpty()) {
                Text(
                    text = "Heard: $recognizedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip")
                }
                
                if (isRecording) {
                    Button(onClick = onFinishReading) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done")
                    }
                } else {
                    Button(onClick = onStartReading) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read Aloud")
                    }
                }
            }
        }
    }
}

@Composable
fun WaitingCard(playerName: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$playerName is reading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| BLE advertising fails | Check Bluetooth and location permissions |
| Speech recognition unavailable | Ensure device has Google Speech Services |
| Connection drops | Reduce distance, check for interference |
| Slow packet delivery | Check MTU size, reduce payload |

### Required Runtime Permissions

```kotlin
// Request permissions before BLE operations
val permissions = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.RECORD_AUDIO
)

// Use ActivityResultContracts.RequestMultiplePermissions()
```

## Next Steps

- See [BLE Multiplayer Design](../plans/2026-02-06-ble-multiplayer-design.md) for architecture details
- See [SDK Architecture](../ios/sdk-architecture.md) for internal structure
