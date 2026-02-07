package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DialogStoreMultiplayerTest {
    
    private fun createStore(testDispatcher: kotlinx.coroutines.test.TestDispatcher): DialogStore {
        return DialogStore(
            networkSession = LoopbackNetworkSession("local"),
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
    }
    
    @Test
    fun startAdvertisingIntentSetsAdvertisingTrue() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        store.accept(DialogStore.Intent.StartAdvertising)
        advanceUntilIdle()
        
        val state = store.state.value
        assertTrue(state.isAdvertising)
        assertEquals(GamePhase.LOBBY, state.currentPhase)
    }
    
    @Test
    fun stopAdvertisingIntentSetsAdvertisingFalse() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        store.accept(DialogStore.Intent.StartAdvertising)
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.StopAdvertising)
        advanceUntilIdle()
        
        val state = store.state.value
        assertEquals(false, state.isAdvertising)
    }
    
    @Test
    fun peerConnectedIntentAddsToLobbyPlayers() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        store.accept(DialogStore.Intent.PeerConnected(
            peerId = "peer-123",
            peerName = "Alice"
        ))
        advanceUntilIdle()
        
        val state = store.state.value
        assertEquals(1, state.lobbyPlayers.size)
        assertEquals("peer-123", state.lobbyPlayers.first().peerId)
        assertEquals("Alice", state.lobbyPlayers.first().displayName)
        assertEquals(false, state.lobbyPlayers.first().isReady)
        assertNull(state.lobbyPlayers.first().assignedRole)
        
        assertEquals(1, state.connectedPeers.size)
        assertEquals("peer-123", state.connectedPeers.first().peerId)
    }
    
    @Test
    fun peerDisconnectedIntentRemovesFromLobbyPlayers() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        store.accept(DialogStore.Intent.PeerConnected("peer-1", "Alice"))
        store.accept(DialogStore.Intent.PeerConnected("peer-2", "Bob"))
        advanceUntilIdle()
        
        assertEquals(2, store.state.value.lobbyPlayers.size)
        
        store.accept(DialogStore.Intent.PeerDisconnected("peer-1"))
        advanceUntilIdle()
        
        val state = store.state.value
        assertEquals(1, state.lobbyPlayers.size)
        assertEquals("peer-2", state.lobbyPlayers.first().peerId)
    }
    
    @Test
    fun assignRoleIntentUpdatesPlayerRole() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        val scenario = Scenario(
            id = "coffee-shop",
            name = "Coffee Shop",
            description = "Order coffee",
            availableRoles = listOf(
                Role("barista", "Barista", "Makes coffee"),
                Role("customer", "Customer", "Orders coffee")
            )
        )
        
        store.accept(DialogStore.Intent.HostGame(scenario, scenario.availableRoles.first()))
        store.accept(DialogStore.Intent.PeerConnected("peer-1", "Alice"))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.AssignRole("peer-1", "customer"))
        advanceUntilIdle()
        
        val state = store.state.value
        val player = state.lobbyPlayers.find { it.peerId == "peer-1" }
        assertNotNull(player)
        assertNotNull(player.assignedRole)
        assertEquals("customer", player.assignedRole?.id)
        assertEquals("Customer", player.assignedRole?.name)
        
        assertTrue(state.roles.containsKey("peer-1"))
    }
    
    @Test
    fun setPlayerReadyIntentUpdatesReadyState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        store.accept(DialogStore.Intent.PeerConnected("peer-1", "Alice"))
        advanceUntilIdle()
        
        assertEquals(false, store.state.value.lobbyPlayers.first().isReady)
        
        store.accept(DialogStore.Intent.SetPlayerReady("peer-1", true))
        advanceUntilIdle()
        
        assertTrue(store.state.value.lobbyPlayers.first().isReady)
    }
    
    @Test
    fun completeLineIntentUpdatesDialogLineAndStats() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        val scenario = Scenario("test", "Test", "Test", listOf(Role("a", "A", "a")))
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles.first()))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        
        val lineId = store.state.value.dialogHistory.first().id
        val result = PronunciationResult.perfect(2000L)
        
        store.accept(DialogStore.Intent.CompleteLine(lineId, result))
        advanceUntilIdle()
        
        val state = store.state.value
        val line = state.dialogHistory.find { it.id == lineId }
        
        assertNotNull(line)
        assertEquals(LineVisibility.COMMITTED, line.visibility)
        assertNotNull(line.pronunciationResult)
        assertEquals(1.0f, line.pronunciationResult?.accuracy)
    }
    
    @Test
    fun skipLineIntentMarksLineAsSkipped() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        val scenario = Scenario("test", "Test", "Test", listOf(Role("a", "A", "a")))
        store.accept(DialogStore.Intent.StartSoloGame(scenario, scenario.availableRoles.first()))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        
        val lineId = store.state.value.dialogHistory.first().id
        
        store.accept(DialogStore.Intent.SkipLine(lineId))
        advanceUntilIdle()
        
        val state = store.state.value
        val line = state.dialogHistory.find { it.id == lineId }
        
        assertNotNull(line)
        assertEquals(LineVisibility.COMMITTED, line.visibility)
        assertTrue(line.pronunciationResult?.skipped == true)
    }
    
    @Test
    fun startMultiplayerGameRequiresAllPlayersReady() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = createStore(testDispatcher)
        
        val scenario = Scenario(
            id = "test",
            name = "Test",
            description = "Test",
            availableRoles = listOf(Role("a", "A", "a"), Role("b", "B", "b"))
        )
        
        store.accept(DialogStore.Intent.HostGame(scenario, scenario.availableRoles.first()))
        store.accept(DialogStore.Intent.PeerConnected("peer-1", "Alice"))
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.StartMultiplayerGame)
        advanceUntilIdle()
        
        assertEquals(GamePhase.ACTIVE, store.state.value.currentPhase)
    }
}
