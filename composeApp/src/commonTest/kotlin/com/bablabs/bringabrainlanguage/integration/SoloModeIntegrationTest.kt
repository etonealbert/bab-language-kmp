package com.bablabs.bringabrainlanguage.integration

import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoloModeIntegrationTest {
    
    @Test
    fun completeSoloGameFlowWorksEndToEnd() = runTest {
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        val coffeeScenario = Scenario(
            id = "coffee-shop",
            name = "Ordering Coffee",
            description = "Practice ordering at a Spanish cafe",
            availableRoles = listOf(
                Role("barista", "Barista", "The coffee shop worker"),
                Role("customer", "Customer", "The person ordering")
            )
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = coffeeScenario,
            userRole = coffeeScenario.availableRoles[1]
        ))
        
        delay(100)
        
        val stateAfterStart = store.state.value
        
        assertEquals(SessionMode.SOLO, stateAfterStart.mode)
        assertEquals(GamePhase.ACTIVE, stateAfterStart.currentPhase)
        assertEquals("Ordering Coffee", stateAfterStart.scenario?.name)
        
        store.accept(DialogStore.Intent.Generate)
        
        delay(600)
        
        val stateAfterGenerate = store.state.value
        
        assertTrue(stateAfterGenerate.dialogHistory.isNotEmpty())
        val firstLine = stateAfterGenerate.dialogHistory.first()
        assertTrue(firstLine.textNative.isNotBlank())
        assertTrue(firstLine.textTranslated.isNotBlank())
        
        assertTrue(stateAfterGenerate.vectorClock.timestamps.values.any { it > 0 })
    }
    
    @Test
    fun multipleGenerateCallsProduceSequentialDialog() = runTest {
        val session = LoopbackNetworkSession("player-1")
        val store = DialogStore(
            networkSession = session,
            aiProvider = MockAIProvider()
        )
        
        store.accept(DialogStore.Intent.StartSoloGame(
            scenario = Scenario("test", "Test", "Test", emptyList()),
            userRole = Role("user", "User", "Test")
        ))
        
        delay(100)
        
        repeat(3) {
            store.accept(DialogStore.Intent.Generate)
            delay(600)
        }
        
        val finalState = store.state.value
        
        assertEquals(3, finalState.dialogHistory.size)
        
        val uniqueIds = finalState.dialogHistory.map { it.id }.toSet()
        assertEquals(3, uniqueIds.size)
    }
}
