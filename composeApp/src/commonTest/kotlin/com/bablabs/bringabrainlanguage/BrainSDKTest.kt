package com.bablabs.bringabrainlanguage

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
}
