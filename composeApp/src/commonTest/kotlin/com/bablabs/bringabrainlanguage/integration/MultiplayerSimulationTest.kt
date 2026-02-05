package com.bablabs.bringabrainlanguage.integration

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerSimulationTest {
    
    @Test
    fun hostGameInitializesWithHostMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val hostSession = LoopbackNetworkSession("host-1")
        val hostStore = DialogStore(
            networkSession = hostSession,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        val scenario = Scenario(
            id = "coffee-shop",
            name = "Ordering Coffee",
            description = "Practice ordering at a Spanish cafe",
            availableRoles = listOf(
                Role("barista", "Barista", "The coffee shop worker"),
                Role("customer", "Customer", "The person ordering")
            )
        )
        
        hostStore.accept(DialogStore.Intent.HostGame(
            scenario = scenario,
            userRole = scenario.availableRoles[0]
        ))
        
        advanceUntilIdle()
        
        val hostState = hostStore.state.value
        assertEquals(SessionMode.HOST, hostState.mode)
        assertEquals(GamePhase.ACTIVE, hostState.currentPhase)
        assertEquals("Ordering Coffee", hostState.scenario?.name)
    }
    
    @Test
    fun clientGameInitializesWithClientMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val clientSession = LoopbackNetworkSession("client-1")
        val clientStore = DialogStore(
            networkSession = clientSession,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        clientStore.accept(DialogStore.Intent.JoinGame(
            hostDeviceId = "host-device-uuid",
            userRole = Role("customer", "Customer", "The joining player")
        ))
        
        advanceUntilIdle()
        
        val clientState = clientStore.state.value
        assertEquals(SessionMode.CLIENT, clientState.mode)
        assertEquals(GamePhase.WAITING, clientState.currentPhase)
    }
    
    @Test
    fun hostCanGenerateDialogInMultiplayerMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val hostSession = LoopbackNetworkSession("host-1")
        val hostStore = DialogStore(
            networkSession = hostSession,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        val scenario = Scenario(
            id = "coffee-shop",
            name = "Ordering Coffee",
            description = "Practice ordering at a Spanish cafe",
            availableRoles = listOf(
                Role("barista", "Barista", "The coffee shop worker"),
                Role("customer", "Customer", "The person ordering")
            )
        )
        
        hostStore.accept(DialogStore.Intent.HostGame(
            scenario = scenario,
            userRole = scenario.availableRoles[0]
        ))
        
        advanceUntilIdle()
        
        hostStore.accept(DialogStore.Intent.Generate)
        
        advanceUntilIdle()
        
        val hostState = hostStore.state.value
        assertTrue(hostState.dialogHistory.isNotEmpty())
        assertTrue(hostState.vectorClock.timestamps.values.any { it > 0 })
    }
    
    @Test
    fun vectorClockIncrementsOnEachAction() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        store.accept(DialogStore.Intent.HostGame(
            scenario = Scenario("test", "Test", "Test", emptyList()),
            userRole = Role("host", "Host", "Test")
        ))
        
        advanceUntilIdle()
        
        val clockAfterHost = store.state.value.vectorClock.timestamps["player-1"] ?: 0L
        
        store.accept(DialogStore.Intent.Generate)
        advanceUntilIdle()
        
        val clockAfterGenerate = store.state.value.vectorClock.timestamps["player-1"] ?: 0L
        
        assertTrue(clockAfterGenerate > clockAfterHost)
    }
    
    @Test
    fun modeTransitionsWorkCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        assertEquals(SessionMode.SOLO, store.state.value.mode)
        
        store.accept(DialogStore.Intent.HostGame(
            scenario = Scenario("test", "Test", "Test", emptyList()),
            userRole = Role("host", "Host", "Test")
        ))
        advanceUntilIdle()
        assertEquals(SessionMode.HOST, store.state.value.mode)
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test2", "Test2", "Test2", emptyList()),
            userRole = Role("user", "User", "Test")
        ))
        advanceUntilIdle()
        assertEquals(SessionMode.SOLO, store.state.value.mode)
        
        store.accept(DialogStore.Intent.JoinGame(
            hostDeviceId = "host-123",
            userRole = Role("client", "Client", "Test")
        ))
        advanceUntilIdle()
        assertEquals(SessionMode.CLIENT, store.state.value.mode)
    }
}
