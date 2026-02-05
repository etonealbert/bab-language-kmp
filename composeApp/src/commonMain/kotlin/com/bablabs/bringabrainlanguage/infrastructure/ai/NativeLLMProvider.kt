package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider
import com.bablabs.bringabrainlanguage.domain.interfaces.DialogContext
import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import kotlinx.coroutines.flow.Flow

enum class LLMAvailability {
    AVAILABLE,
    NOT_SUPPORTED,
    MODEL_NOT_READY,
    UNKNOWN
}

interface NativeLLMBridge {
    suspend fun initialize(systemPrompt: String): Boolean
    suspend fun generate(prompt: String): String
    fun streamGenerate(prompt: String): Flow<String>
    fun checkAvailability(): LLMAvailability
    fun dispose()
}

expect fun createNativeLLMBridge(): NativeLLMBridge?

class NativeLLMProvider(
    private val bridge: NativeLLMBridge
) : AIProvider {
    
    private var isInitialized = false
    
    override suspend fun generate(context: DialogContext): DialogLine {
        if (!isInitialized) {
            val systemPrompt = buildSystemPrompt(context)
            bridge.initialize(systemPrompt)
            isInitialized = true
        }
        
        val userPrompt = buildUserPrompt(context)
        val response = bridge.generate(userPrompt)
        
        return parseResponse(response, context)
    }
    
    override fun streamGenerate(context: DialogContext): Flow<String> {
        val prompt = buildUserPrompt(context)
        return bridge.streamGenerate(prompt)
    }
    
    private fun buildSystemPrompt(context: DialogContext): String {
        return """
            You are a helpful language learning assistant playing the role of "${context.aiRole}" 
            in a "${context.scenario}" scenario.
            
            The user is playing as "${context.userRole}".
            
            RULES:
            - Respond ONLY in ${context.targetLanguage}
            - Keep responses conversational and natural
            - Use vocabulary appropriate for intermediate learners
            - Stay in character as ${context.aiRole}
            
            FORMAT your response as JSON:
            {
                "textNative": "your response in ${context.targetLanguage}",
                "textTranslated": "translation in ${context.nativeLanguage}"
            }
        """.trimIndent()
    }
    
    private fun buildUserPrompt(context: DialogContext): String {
        val historyContext = if (context.previousLines.isNotEmpty()) {
            val recent = context.previousLines.takeLast(5)
            "Previous dialog:\n" + recent.joinToString("\n") { 
                "${it.roleName}: ${it.textNative}" 
            } + "\n\n"
        } else {
            "This is the start of the conversation.\n\n"
        }
        
        return "${historyContext}Generate the next line as ${context.aiRole}."
    }
    
    private fun parseResponse(response: String, context: DialogContext): DialogLine {
        return try {
            val jsonRegex = """\{[^}]+\}""".toRegex()
            val jsonMatch = jsonRegex.find(response)?.value ?: response
            
            val nativeRegex = """"textNative"\s*:\s*"([^"]+)"""".toRegex()
            val translatedRegex = """"textTranslated"\s*:\s*"([^"]+)"""".toRegex()
            
            val textNative = nativeRegex.find(jsonMatch)?.groupValues?.get(1) ?: response
            val textTranslated = translatedRegex.find(jsonMatch)?.groupValues?.get(1) ?: ""
            
            DialogLine(
                id = "native-${System.currentTimeMillis()}",
                speakerId = "ai-${context.aiRole.lowercase().replace(" ", "-")}",
                roleName = context.aiRole,
                textNative = textNative,
                textTranslated = textTranslated,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DialogLine(
                id = "native-${System.currentTimeMillis()}",
                speakerId = "ai-${context.aiRole.lowercase().replace(" ", "-")}",
                roleName = context.aiRole,
                textNative = response,
                textTranslated = "",
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

object NativeLLMFactory {
    fun createIfAvailable(): AIProvider? {
        val bridge = createNativeLLMBridge() ?: return null
        
        return when (bridge.checkAvailability()) {
            LLMAvailability.AVAILABLE -> NativeLLMProvider(bridge)
            else -> null
        }
    }
    
    fun checkAvailability(): LLMAvailability {
        val bridge = createNativeLLMBridge() ?: return LLMAvailability.NOT_SUPPORTED
        return bridge.checkAvailability()
    }
}
