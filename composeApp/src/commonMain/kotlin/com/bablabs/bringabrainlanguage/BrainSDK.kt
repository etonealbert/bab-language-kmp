package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.models.Role
import com.bablabs.bringabrainlanguage.domain.models.Scenario
import com.bablabs.bringabrainlanguage.domain.models.SessionState
import com.bablabs.bringabrainlanguage.domain.stores.DialogStore
import com.bablabs.bringabrainlanguage.infrastructure.ai.MockAIProvider
import com.bablabs.bringabrainlanguage.infrastructure.network.LoopbackNetworkSession
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class BrainSDK {
    private val networkSession = LoopbackNetworkSession(
        localPeerId = generatePeerId()
    )
    
    private val aiProvider = MockAIProvider()
    
    private val dialogStore = DialogStore(
        networkSession = networkSession,
        aiProvider = aiProvider
    )
    
    val state: StateFlow<SessionState> = dialogStore.state
    
    fun startSoloGame(scenarioId: String, userRoleId: String) {
        val scenario = getScenarioById(scenarioId)
        val role = scenario.availableRoles.find { it.id == userRoleId }
            ?: scenario.availableRoles.first()
        
        dialogStore.accept(DialogStore.Intent.StartSoloGame(
            scenario = scenario,
            userRole = role
        ))
    }
    
    fun generate() {
        dialogStore.accept(DialogStore.Intent.Generate)
    }
    
    fun leaveGame() {
        dialogStore.accept(DialogStore.Intent.LeaveGame)
    }
    
    fun getAvailableScenarios(): List<Scenario> {
        return listOf(
            getScenarioById("coffee-shop"),
            getScenarioById("the-heist"),
            getScenarioById("first-date")
        )
    }
    
    private fun getScenarioById(id: String): Scenario {
        return when (id) {
            "coffee-shop" -> Scenario(
                id = "coffee-shop",
                name = "Ordering Coffee",
                description = "Practice ordering at a Spanish cafe",
                availableRoles = listOf(
                    Role("barista", "Barista", "The coffee shop worker"),
                    Role("customer", "Customer", "The person ordering")
                )
            )
            "the-heist" -> Scenario(
                id = "the-heist",
                name = "The Heist",
                description = "A dramatic crime scene unfolds",
                availableRoles = listOf(
                    Role("detective", "Detective", "The investigator"),
                    Role("thief", "Thief", "The suspect")
                )
            )
            else -> Scenario(
                id = "first-date",
                name = "First Date",
                description = "A romantic dinner conversation",
                availableRoles = listOf(
                    Role("date1", "Alex", "First person"),
                    Role("date2", "Sam", "Second person")
                )
            )
        }
    }
    
    private fun generatePeerId(): String {
        return "player-${Random.nextInt(10000, 99999)}"
    }
}
