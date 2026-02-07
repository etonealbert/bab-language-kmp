package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PronunciationModelsTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun pronunciationResultSkippedFactoryCreatesCorrectDefaults() {
        val result = PronunciationResult.skipped()
        
        assertTrue(result.skipped)
        assertEquals(0, result.errorCount)
        assertEquals(0f, result.accuracy)
        assertTrue(result.wordErrors.isEmpty())
        assertEquals(0L, result.duration)
    }
    
    @Test
    fun pronunciationResultPerfectFactoryCreatesPerfectScore() {
        val duration = 2500L
        val result = PronunciationResult.perfect(duration)
        
        assertEquals(false, result.skipped)
        assertEquals(0, result.errorCount)
        assertEquals(1.0f, result.accuracy)
        assertTrue(result.wordErrors.isEmpty())
        assertEquals(duration, result.duration)
    }
    
    @Test
    fun pronunciationResultSerializesAndDeserializesCorrectly() {
        val wordErrors = listOf(
            WordError(word = "hola", position = 0, expected = "ola", heard = "olla"),
            WordError(word = "buenos", position = 1, expected = null, heard = "buenoz")
        )
        
        val result = PronunciationResult(
            errorCount = 2,
            accuracy = 0.75f,
            wordErrors = wordErrors,
            skipped = false,
            duration = 3200L
        )
        
        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<PronunciationResult>(serialized)
        
        assertEquals(result, deserialized)
    }
    
    @Test
    fun wordErrorSerializesWithNullableFields() {
        val error = WordError(
            word = "gracias",
            position = 3,
            expected = null,
            heard = null
        )
        
        val serialized = json.encodeToString(error)
        val deserialized = json.decodeFromString<WordError>(serialized)
        
        assertEquals(error, deserialized)
    }
    
    @Test
    fun lineVisibilityHasCorrectValues() {
        assertEquals(LineVisibility.PRIVATE, LineVisibility.valueOf("PRIVATE"))
        assertEquals(LineVisibility.COMMITTED, LineVisibility.valueOf("COMMITTED"))
        assertEquals(2, LineVisibility.entries.size)
    }
}
