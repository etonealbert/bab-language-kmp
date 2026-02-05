package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.random.Random

class MockAIProvider : AIProvider {
    
    private val mockDialogs = mapOf(
        "coffee" to listOf(
            MockLine("Hola, bienvenido. Que deseas tomar?", "Hello, welcome. What would you like to drink?"),
            MockLine("Tenemos cafe, te, y chocolate caliente.", "We have coffee, tea, and hot chocolate."),
            MockLine("Muy bien. Seran tres euros.", "Very well. That will be three euros.")
        ),
        "heist" to listOf(
            MockLine("Alto ahi! Policia!", "Stop right there! Police!"),
            MockLine("No te muevas. Tienes derecho a guardar silencio.", "Don't move. You have the right to remain silent."),
            MockLine("Donde esta el dinero?", "Where is the money?")
        ),
        "default" to listOf(
            MockLine("Hola, como estas?", "Hello, how are you?"),
            MockLine("Muy bien, gracias. Y tu?", "Very well, thanks. And you?"),
            MockLine("Hasta luego!", "See you later!")
        )
    )
    
    private data class MockLine(val native: String, val translated: String)
    
    override suspend fun generate(context: DialogContext): DialogLine {
        delay(Random.nextLong(200, 500))
        
        val scenarioKey = context.scenario.lowercase()
        val lines = mockDialogs.entries
            .firstOrNull { scenarioKey.contains(it.key) }
            ?.value ?: mockDialogs["default"]!!
        
        val selectedLine = lines[context.previousLines.size % lines.size]
        val timestamp = Clock.System.now().toEpochMilliseconds()
        
        return DialogLine(
            id = "line-$timestamp",
            speakerId = "ai-${context.aiRole.lowercase().replace(" ", "-")}",
            roleName = context.aiRole,
            textNative = selectedLine.native,
            textTranslated = selectedLine.translated,
            timestamp = timestamp
        )
    }
    
    override fun streamGenerate(context: DialogContext): Flow<String> = flow {
        delay(100)
        
        val lines = mockDialogs["default"]!!
        val selectedLine = lines[0]
        
        selectedLine.native.split(" ").forEach { word ->
            emit("$word ")
            delay(50)
        }
    }
}
