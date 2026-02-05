package com.bablabs.bringabrainlanguage.domain.stores

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogStoreTest {
    
    @Test
    fun initialStateHasEmptyDialogHistory() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        val state = store.state.first()
        
        assertTrue(state.dialogHistory.isEmpty())
        assertEquals(SessionMode.SOLO, state.mode)
    }
    
    @Test
    fun generateIntentProducesDialogLineInState() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
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
        
        delay(100)
        
        store.accept(DialogStore.Intent.Generate)
        
        delay(600)
        
        val state = store.state.value
        
        assertTrue(state.dialogHistory.isNotEmpty())
    }
    
    @Test
    fun stateUpdatesOnlyHappenViaNetworkPackets() = runTest {
        val session = LoopbackNetworkSession("local")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test", "Test", "Test scenario", emptyList()),
            userRole = Role("user", "User", "Test user")
        ))
        
        delay(100)
        
        store.accept(DialogStore.Intent.Generate)
        
        delay(600)
        
        val state = store.state.value
        
        assertTrue(state.vectorClock.timestamps.isNotEmpty())
    }
}
