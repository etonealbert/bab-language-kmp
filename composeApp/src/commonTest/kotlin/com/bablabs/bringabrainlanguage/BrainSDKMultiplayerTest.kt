package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class BrainSDKMultiplayerTest {
    
    @Test
    fun startHostAdvertisingReturnsServiceName() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        val serviceName = sdk.startHostAdvertising()
        advanceUntilIdle()
        
        assertTrue(serviceName.startsWith("bab-game-"))
        assertTrue(sdk.state.value.isAdvertising)
    }
    
    @Test
    fun stopHostAdvertisingSetsAdvertisingFalse() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.startHostAdvertising()
        advanceUntilIdle()
        
        sdk.stopHostAdvertising()
        advanceUntilIdle()
        
        assertEquals(false, sdk.state.value.isAdvertising)
    }
    
    @Test
    fun onPeerConnectedAddsPlayerToLobby() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.onPeerConnected("peer-123", "Alice")
        advanceUntilIdle()
        
        val state = sdk.state.value
        assertEquals(1, state.lobbyPlayers.size)
        assertEquals("peer-123", state.lobbyPlayers.first().peerId)
        assertEquals("Alice", state.lobbyPlayers.first().displayName)
    }
    
    @Test
    fun onPeerDisconnectedRemovesPlayerFromLobby() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.onPeerConnected("peer-1", "Alice")
        sdk.onPeerConnected("peer-2", "Bob")
        advanceUntilIdle()
        
        sdk.onPeerDisconnected("peer-1")
        advanceUntilIdle()
        
        val state = sdk.state.value
        assertEquals(1, state.lobbyPlayers.size)
        assertEquals("peer-2", state.lobbyPlayers.first().peerId)
    }
    
    @Test
    fun assignRoleUpdatesPlayerRole() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.hostGame("coffee-shop", "barista")
        sdk.onPeerConnected("peer-1", "Alice")
        advanceUntilIdle()
        
        sdk.assignRole("peer-1", "customer")
        advanceUntilIdle()
        
        val player = sdk.state.value.lobbyPlayers.find { it.peerId == "peer-1" }
        assertNotNull(player?.assignedRole)
        assertEquals("customer", player?.assignedRole?.id)
    }
    
    @Test
    fun setPlayerReadyUpdatesReadyState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.onPeerConnected("peer-1", "Alice")
        advanceUntilIdle()
        
        sdk.setPlayerReady("peer-1", true)
        advanceUntilIdle()
        
        assertTrue(sdk.state.value.lobbyPlayers.first().isReady)
    }
    
    @Test
    fun completeLineUpdatesDialogHistory() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.startSoloGame("coffee-shop", "customer")
        advanceUntilIdle()
        
        sdk.generate()
        advanceUntilIdle()
        
        val lineId = sdk.state.value.dialogHistory.first().id
        val result = PronunciationResult(
            errorCount = 0,
            accuracy = 1.0f,
            wordErrors = emptyList(),
            skipped = false,
            duration = 2500L
        )
        
        sdk.completeLine(lineId, result)
        advanceUntilIdle()
        
        val line = sdk.state.value.dialogHistory.find { it.id == lineId }
        assertNotNull(line)
        assertEquals(LineVisibility.COMMITTED, line.visibility)
        assertNotNull(line.pronunciationResult)
    }
    
    @Test
    fun skipLineMarksLineAsSkipped() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.startSoloGame("coffee-shop", "customer")
        advanceUntilIdle()
        
        sdk.generate()
        advanceUntilIdle()
        
        val lineId = sdk.state.value.dialogHistory.first().id
        
        sdk.skipLine(lineId)
        advanceUntilIdle()
        
        val line = sdk.state.value.dialogHistory.find { it.id == lineId }
        assertNotNull(line)
        assertTrue(line.pronunciationResult?.skipped == true)
    }
    
    @Test
    fun outgoingPacketsFlowIsAccessible() {
        val sdk = BrainSDK()
        
        assertNotNull(sdk.outgoingPackets)
    }
}
