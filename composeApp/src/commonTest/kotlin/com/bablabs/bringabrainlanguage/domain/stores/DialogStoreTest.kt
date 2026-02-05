package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DialogStoreTest {
    
    @Test
    fun initialStateHasEmptyDialogHistory() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        val state = store.state.first()
        
        assertTrue(state.dialogHistory.isEmpty())
        assertEquals(SessionMode.SOLO, state.mode)
    }
    
    @Test
    fun generateIntentProducesDialogLineInState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
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
        
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        
        advanceUntilIdle()
        
        val state = store.state.value
        
        assertTrue(state.dialogHistory.isNotEmpty())
    }
    
    @Test
    fun stateUpdatesOnlyHappenViaNetworkPackets() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test", "Test", "Test scenario", emptyList()),
            userRole = Role("user", "User", "Test user")
        ))
        
        advanceUntilIdle()
        
        store.accept(DialogStore.Intent.Generate)
        
        advanceUntilIdle()
        
        val state = store.state.value
        
        assertTrue(state.vectorClock.timestamps.isNotEmpty())
    }
    
    @Test
    fun hostGameIntentSetsHostMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        store.accept(DialogStore.Intent.HostGame(
            scenario = Scenario("test", "Test", "Test scenario", emptyList()),
            userRole = Role("host", "Host", "The host")
        ))
        
        advanceUntilIdle()
        
        val state = store.state.value
        assertEquals(SessionMode.HOST, state.mode)
        assertEquals(GamePhase.ACTIVE, state.currentPhase)
    }
    
    @Test
    fun joinGameIntentSetsClientMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider(),
            coroutineContext = testDispatcher
        )
        
        store.accept(DialogStore.Intent.JoinGame(
            hostDeviceId = "host-device-uuid",
            userRole = Role("client", "Client", "The joining player")
        ))
        
        advanceUntilIdle()
        
        val state = store.state.value
        assertEquals(SessionMode.CLIENT, state.mode)
        assertEquals(GamePhase.WAITING, state.currentPhase)
    }
}
