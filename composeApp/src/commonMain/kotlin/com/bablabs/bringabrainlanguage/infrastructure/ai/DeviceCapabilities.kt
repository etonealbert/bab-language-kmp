package com.bablabs.bringabrainlanguage.infrastructure.ai

import com.bablabs.bringabrainlanguage.domain.interfaces.AIProvider

data class AICapabilities(
    val hasNativeLLM: Boolean,
    val nativeLLMStatus: LLMAvailability,
    val recommendedProvider: AIProviderType
)

enum class AIProviderType {
    NATIVE_LLM,
    CLOUD_API,
    MOCK
}

object DeviceCapabilities {
    
    fun check(): AICapabilities {
        val availability = NativeLLMFactory.checkAvailability()
        
        return AICapabilities(
            hasNativeLLM = availability == LLMAvailability.AVAILABLE,
            nativeLLMStatus = availability,
            recommendedProvider = when (availability) {
                LLMAvailability.AVAILABLE -> AIProviderType.NATIVE_LLM
                LLMAvailability.MODEL_NOT_READY -> AIProviderType.CLOUD_API
                LLMAvailability.NOT_SUPPORTED -> AIProviderType.CLOUD_API
                LLMAvailability.UNKNOWN -> AIProviderType.MOCK
            }
        )
    }
    
    fun getBestAvailableProvider(cloudProvider: AIProvider? = null): AIProvider {
        val nativeProvider = NativeLLMFactory.createIfAvailable()
        
        return nativeProvider
            ?: cloudProvider
            ?: MockAIProvider()
    }
}
