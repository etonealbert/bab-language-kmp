package com.bablabs.bringabrainlanguage.infrastructure.translation

import com.bablabs.bringabrainlanguage.domain.interfaces.TranslationProvider
import com.bablabs.bringabrainlanguage.domain.models.ExampleSentence
import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.PartOfSpeech
import com.bablabs.bringabrainlanguage.domain.models.WordTranslation

/**
 * Mock implementation of TranslationProvider for development and testing.
 * Returns realistic mock translations for common Spanish words.
 * 
 * In production, replace with:
 * - ServerTranslationProvider (subscribed users)
 * - NativeTranslationProvider (iOS 26 Foundation Models)
 */
class MockTranslationProvider : TranslationProvider {
    
    private val mockTranslations = mapOf(
        "café" to WordTranslation(
            word = "café",
            translation = "coffee",
            partOfSpeech = PartOfSpeech.NOUN,
            phoneticSpelling = "ka-FEH",
            audioUrl = null,
            definitions = listOf(
                "A hot drink made from roasted coffee beans",
                "A small restaurant serving coffee and light meals"
            ),
            exampleSentences = listOf(
                ExampleSentence(
                    native = "Quiero un café, por favor.",
                    translated = "I want a coffee, please."
                ),
                ExampleSentence(
                    native = "El café está muy caliente.",
                    translated = "The coffee is very hot."
                )
            ),
            relatedWords = listOf("cafetería", "cafeína", "descafeinado"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = null
        ),
        "hola" to WordTranslation(
            word = "hola",
            translation = "hello",
            partOfSpeech = PartOfSpeech.INTERJECTION,
            phoneticSpelling = "OH-lah",
            audioUrl = null,
            definitions = listOf("A greeting used when meeting someone"),
            exampleSentences = listOf(
                ExampleSentence(
                    native = "¡Hola! ¿Cómo estás?",
                    translated = "Hello! How are you?"
                )
            ),
            relatedWords = listOf("adiós", "buenos días", "buenas tardes"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = null
        ),
        "gracias" to WordTranslation(
            word = "gracias",
            translation = "thank you",
            partOfSpeech = PartOfSpeech.INTERJECTION,
            phoneticSpelling = "GRAH-see-ahs",
            audioUrl = null,
            definitions = listOf("An expression of gratitude"),
            exampleSentences = listOf(
                ExampleSentence(
                    native = "Muchas gracias por tu ayuda.",
                    translated = "Thank you very much for your help."
                )
            ),
            relatedWords = listOf("de nada", "agradecer", "agradecido"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = null
        ),
        "agua" to WordTranslation(
            word = "agua",
            translation = "water",
            partOfSpeech = PartOfSpeech.NOUN,
            phoneticSpelling = "AH-gwah",
            audioUrl = null,
            definitions = listOf(
                "A colorless liquid essential for life",
                "A body of water such as a sea or lake"
            ),
            exampleSentences = listOf(
                ExampleSentence(
                    native = "¿Me puede traer un vaso de agua?",
                    translated = "Can you bring me a glass of water?"
                )
            ),
            relatedWords = listOf("aguacero", "acuático", "regar"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = null
        ),
        "libro" to WordTranslation(
            word = "libro",
            translation = "book",
            partOfSpeech = PartOfSpeech.NOUN,
            phoneticSpelling = "LEE-broh",
            audioUrl = null,
            definitions = listOf("A written or printed work consisting of pages"),
            exampleSentences = listOf(
                ExampleSentence(
                    native = "Estoy leyendo un libro muy interesante.",
                    translated = "I am reading a very interesting book."
                )
            ),
            relatedWords = listOf("librería", "libreta", "biblioteca"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = null
        )
    )
    
    override suspend fun translateWord(
        word: String,
        sentenceContext: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ): WordTranslation {
        val normalizedWord = word.lowercase().trim()
        
        return mockTranslations[normalizedWord]?.copy(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            contextUsed = sentenceContext.ifBlank { null }
        ) ?: createGenericTranslation(word, sourceLanguage, targetLanguage, sentenceContext)
    }
    
    override fun isAvailable(): Boolean = true
    
    private fun createGenericTranslation(
        word: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode,
        context: String
    ): WordTranslation {
        return WordTranslation(
            word = word,
            translation = "[Translation of: $word]",
            partOfSpeech = null,
            phoneticSpelling = null,
            audioUrl = null,
            definitions = listOf("Definition not available in mock provider"),
            exampleSentences = emptyList(),
            relatedWords = emptyList(),
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            contextUsed = context.ifBlank { null }
        )
    }
}
