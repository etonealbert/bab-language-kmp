package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockAIProviderTest {
    
    @Test
    fun generateReturnsDialogLineWithNativeAndTranslatedText() = runTest {
        val provider = MockAIProvider()
        
        val context = DialogContext(
            scenario = "Ordering Coffee",
            userRole = "Customer",
            aiRole = "Barista",
            previousLines = emptyList()
        )
        
        val result = provider.generate(context)
        
        assertNotNull(result)
        assertTrue(result.textNative.isNotBlank())
        assertTrue(result.textTranslated.isNotBlank())
        assertEquals("Barista", result.roleName)
    }
    
    @Test
    fun generateReturnsDifferentLinesForDifferentContexts() = runTest {
        val provider = MockAIProvider()
        
        val context1 = DialogContext(
            scenario = "Ordering Coffee",
            userRole = "Customer",
            aiRole = "Barista",
            previousLines = emptyList()
        )
        
        val context2 = DialogContext(
            scenario = "The Heist",
            userRole = "Detective",
            aiRole = "Thief",
            previousLines = emptyList()
        )
        
        val result1 = provider.generate(context1)
        val result2 = provider.generate(context2)
        
        assertEquals("Barista", result1.roleName)
        assertEquals("Thief", result2.roleName)
    }
    
    @Test
    fun generateIncludesSpeakerIdFromAIRole() = runTest {
        val provider = MockAIProvider()
        
        val context = DialogContext(
            scenario = "Test",
            userRole = "User",
            aiRole = "Robot",
            previousLines = emptyList()
        )
        
        val result = provider.generate(context)
        
        assertEquals("ai-robot", result.speakerId)
    }
}
