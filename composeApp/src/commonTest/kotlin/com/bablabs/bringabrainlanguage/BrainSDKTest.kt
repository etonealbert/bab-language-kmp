package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.models.GamePhase
import com.bablabs.bringabrainlanguage.domain.models.SessionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BrainSDKTest {
    
    @Test
    fun sdkInitializesWithValidState() {
        val sdk = BrainSDK()
        assertNotNull(sdk.state.value)
    }
    
    @Test
    fun availableScenariosReturnsNonEmptyList() {
        val sdk = BrainSDK()
        val scenarios = sdk.getAvailableScenarios()
        assertTrue(scenarios.isNotEmpty())
    }
    
    @Test
    fun scanForHostsReturnsFlow() {
        val sdk = BrainSDK()
        val hosts = sdk.scanForHosts()
        assertNotNull(hosts)
    }
    
    @Test
    fun hostGameSetsHostMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.hostGame(scenarioId = "coffee-shop", userRoleId = "barista")
        
        advanceUntilIdle()
        
        val state = sdk.state.value
        assertEquals(SessionMode.HOST, state.mode)
        assertEquals(GamePhase.ACTIVE, state.currentPhase)
    }
    
    @Test
    fun joinGameSetsClientMode() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sdk = BrainSDK(coroutineContext = testDispatcher)
        
        sdk.joinGame(hostDeviceId = "host-123", userRoleId = "customer")
        
        advanceUntilIdle()
        
        val state = sdk.state.value
        assertEquals(SessionMode.CLIENT, state.mode)
        assertEquals(GamePhase.WAITING, state.currentPhase)
    }
}
